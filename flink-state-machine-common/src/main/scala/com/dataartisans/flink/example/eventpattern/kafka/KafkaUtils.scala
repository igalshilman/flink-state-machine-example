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

import java.util.Properties

object KafkaUtils {

  /**
    * Returns a copy of `properties` where we set the handed in default values if they are not
    * set in the original properties.
    */
  def copyKafkaProperties(properties: Properties, kafkaPropertyToDefault: Map[String, String]): Properties = {
    kafkaPropertyToDefault.foreach { propertyDefaultValue =>
      val value = properties.getProperty(propertyDefaultValue._1, propertyDefaultValue._2)
      if (value == propertyDefaultValue._2) {
        properties.put(propertyDefaultValue._1, propertyDefaultValue._2)
      }
    }
    properties
  }

}
