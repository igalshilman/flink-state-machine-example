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
