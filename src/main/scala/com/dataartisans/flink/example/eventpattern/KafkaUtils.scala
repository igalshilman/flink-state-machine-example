package com.dataartisans.flink.example.eventpattern

import java.util.Properties

object KafkaUtils {

  /**
    * Returns a copy of <code>properties</code> that contains only keys that are also in
    * <code>kafkaPropertyToDefault</code>. If a property is not represented in <code>properties</code>,
    * the default value as specified by <code>kafkaPropertyToDefault</code> is used.
    * If no default value is specified (i.e., null), the key will not included in the copy.
    *
    * @param properties Map that can contain properties that are not relevant to Kafka
    * @param kafkaPropertyToDefault Mappings from Kafka property name to default value (null if none)
    */
  def copyKafkaProperties(properties: Properties, kafkaPropertyToDefault: Map[String, String]): Properties = {
    val consumerProperties = new Properties()
    kafkaPropertyToDefault.foreach { propertyDefaultValue =>
      val value = properties.getProperty(propertyDefaultValue._1, propertyDefaultValue._2)
      if (value != null) {
        consumerProperties.put(propertyDefaultValue._1, value)
      }
    }
    consumerProperties
  }

}
