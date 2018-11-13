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

package com.dataartisans.flink.example.eventpattern.kafka

import org.apache.flink.streaming.connectors.kafka.partitioner.FlinkKafkaPartitioner

import com.dataartisans.flink.example.eventpattern.Event

import scala.util.hashing.MurmurHash3

/**
  * Partitions `Event`s by `sourceAddress`
  */
object EventPartitioner extends FlinkKafkaPartitioner[Event] {

  override def partition(record: Event,
                         key: Array[Byte],
                         value: Array[Byte],
                         targetTopic: String,
                         partitions: Array[Int]): Int = {

    val numPartitions = partitions.length
    (MurmurHash3.stringHash(record.sourceAddress) % numPartitions + numPartitions) % numPartitions
  }

}
