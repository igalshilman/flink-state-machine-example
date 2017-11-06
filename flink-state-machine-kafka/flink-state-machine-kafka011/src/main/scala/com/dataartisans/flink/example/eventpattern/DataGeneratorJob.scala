/*
 * Copyright 2015 Data Artisans GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dataartisans.flink.example.eventpattern

import org.apache.flink.api.common.state.{ListState, ListStateDescriptor}
import org.apache.flink.api.java.utils.ParameterTool
import org.apache.flink.configuration.Configuration
import org.apache.flink.runtime.state.{FunctionInitializationContext, FunctionSnapshotContext}
import org.apache.flink.streaming.api.checkpoint.CheckpointedFunction
import org.apache.flink.streaming.api.functions.source.RichParallelSourceFunction
import org.apache.flink.streaming.api.functions.source.SourceFunction.SourceContext
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaProducer011
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaProducer011.Semantic
import org.apache.flink.streaming.util.serialization.KeyedSerializationSchemaWrapper
import org.apache.flink.util.XORShiftRandom

import com.dataartisans.flink.example.eventpattern.kafka.{EventDeSerializer, EventPartitioner, KafkaUtils}
import grizzled.slf4j.Logger

import java.util.concurrent.CountDownLatch
import java.util.{Optional, Properties, UUID}


/**
  * This Flink job runs the following:
  * The EventsGeneratorSource from the state machine
  * an operator that adds "event time" to the stream (just counting elements)
  * a Kafka sink writing the resulting stream to a topic
  *
  * Local invocation line:
  *   --numKeys <>
  *   --topic <>
  *   --bootstrap.servers localhost:9092
  *   --transaction.timeout.ms 60000 (transaction timeout for Kafka exactly-once producer)
  *   --semantic exactly-once (alternatively: none)
  *   --sleep <> (time in ms to sleep between messages, 0 disables sleeping completely)
 */
object DataGeneratorJob {

  def main(args: Array[String]): Unit = {
    // retrieve input parameters
    val pt: ParameterTool = ParameterTool.fromArgs(args)

    // create the environment to create streams and configure execution
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.setParallelism(pt.getInt("parallelism", 16))

    // this statement enables the checkpointing mechanism with an interval of 5 sec
    env.enableCheckpointing(pt.getInt("checkpointInterval", 5000))

    val semanticArg = pt.get("semantic", "exactly-once")
    require (
      semanticArg.equals("exactly-once") || semanticArg.equals("none"),
      "semantic must be either exactly-once or none, was: " + semanticArg)
    val semantic = if (semanticArg.equals("exactly-once")) Semantic.EXACTLY_ONCE else Semantic.NONE

    val stream = env.addSource(
      new KeyedEventsGeneratorSource(
        pt.getInt("numKeys", 200),
        semantic,
        pt.getLong("sleep", 100)))

    stream.addSink(new FlinkKafkaProducer011[Event](
      pt.getRequired("topic"),
      new KeyedSerializationSchemaWrapper[Event](new EventDeSerializer()),
      createKafkaProducerProperties(pt.getProperties),
      Optional.of(EventPartitioner),
      semantic,
      5))

    // trigger program execution
    env.execute("Kafka events generator")
  }

  def createKafkaProducerProperties(properties: Properties): Properties = {
    KafkaUtils.copyKafkaProperties(
      properties,
      Map(
        "bootstrap.servers" -> null,
        "transaction.timeout.ms" -> null))
  }
}

class KeyedEventsGeneratorSource(numKeys: Int, semantic: Semantic, sleep: Long)
  extends RichParallelSourceFunction[Event] with CheckpointedFunction {

  @transient var log = Logger(getClass)

  // startKey is inclusive, endKey is exclusive
  case class KeyRange(startKey: Int, endKey: Int, keyState: scala.collection.mutable.Map[Int, State]) {
    require(
      startKey < endKey,
      s"startKey must be strictly lower than endKey (startKey: $startKey, endKey: $endKey)")
  }

  var running = true
  val rnd = new XORShiftRandom()

  @transient var keyRanges: ListState[KeyRange] = _
  @transient var localKeyRanges: Seq[KeyRange] = Seq()
  @transient var cancelLatch: CountDownLatch = _

  /** Only relevant if semantic is not EXACTLY_ONCE. */
  @transient var keyPrefix: String = UUID.randomUUID().toString

  override def cancel(): Unit = {
    if (cancelLatch != null) {
      cancelLatch.countDown()
    }
    running = false
  }

  override def open(parameters: Configuration): Unit = {
    cancelLatch = new CountDownLatch(1)
  }

  override def initializeState(context: FunctionInitializationContext): Unit = {
    log = Logger(getClass)

    keyPrefix = UUID.randomUUID().toString

    keyRanges = context.getOperatorStateStore.getListState(new ListStateDescriptor[KeyRange]("keyRanges", classOf[KeyRange]))

    if (context.isRestored && semantic == Semantic.EXACTLY_ONCE) {
      val keyRangeIterator = Option(keyRanges.get())
        .getOrElse(throw new IllegalStateException("keyRanges must not be null"))
        .iterator
      while (keyRangeIterator.hasNext) {
        localKeyRanges :+ keyRangeIterator.next()
      }
    } else {
      // we always initialize from zero, never snapshot state

      // initialize our initial operator state based on the number of keys and the parallelism
      val subtaskIndex = getRuntimeContext.getIndexOfThisSubtask
      val numSubtasks = getRuntimeContext.getNumberOfParallelSubtasks

      // cannot have more ranges than there are keys
      val numKeyRanges = Math.min(getRuntimeContext.getMaxNumberOfParallelSubtasks, numKeys)

      // This is the maximum number of keys per range. Empty ranges will be filtered out
      val keysPerKeyRange = Math.ceil(numKeys / numKeyRanges.toFloat).toInt

      log.info(s"KEY STATS $numKeyRanges $numKeys $keysPerKeyRange")

      // which are our key ranges
      localKeyRanges = 0
        .until(numKeyRanges)
        .filter(range => range % numSubtasks == subtaskIndex)
        .flatMap { range =>
          val startIndex = range * keysPerKeyRange
          // Math.min to avoid ending up with more keys than requested
          val endIndex = Math.min(startIndex + keysPerKeyRange, numKeys)
          val states: Seq[(Int, State)] = startIndex.until(endIndex).map(i => i -> InitialState)
          if (startIndex < endIndex) {
            log.info(s"START $startIndex until $endIndex")
            Seq(KeyRange(startIndex, endIndex, scala.collection.mutable.Map[Int, State](states: _*)))
          } else {
            Seq.empty
          }
        }

      log.info(s"Event source $subtaskIndex/$numSubtasks has key ranges $localKeyRanges.")
    }
  }

  override def snapshotState(context: FunctionSnapshotContext): Unit = {
    keyRanges.clear()
    localKeyRanges.foreach(keyRanges.add(_))
  }

  override def run(ctx: SourceContext[Event]): Unit = {

    while(running && localKeyRanges.nonEmpty) {
      val keyRangeIndex = rnd.nextInt(localKeyRanges.size)
      val keyRange = localKeyRanges(keyRangeIndex)
      val key = rnd.nextInt(keyRange.endKey - keyRange.startKey) + keyRange.startKey

      ctx.getCheckpointLock.synchronized {
        val keyState = keyRange.keyState.get(key)
        val (nextEvent, newState) = keyState.get.randomTransition(rnd)
        if (newState == TerminalState) {
          keyRange.keyState += (key -> InitialState)
        } else {
          keyRange.keyState += (key -> newState)
        }

        val sourceAddress =
          if (semantic == Semantic.EXACTLY_ONCE) "" + key else keyPrefix + "" + key
        ctx.collect(Event(sourceAddress, nextEvent))
      }
      if (sleep > 0) {
        Thread.sleep(sleep)
      }
    }

    cancelLatch.await()
  }
}

