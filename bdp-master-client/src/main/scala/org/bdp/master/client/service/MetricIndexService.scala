package org.bdp.master.client.service

import com.typesafe.scalalogging.LazyLogging
import org.bdp.master.client.domain.MetricIndex
import org.bdp.master.client.util.{JsonDecoder, RedisClient}
import org.bdp.master.client.Constants._

object MetricIndexService extends LazyLogging{

  def getMetricIndexBy(id: Long): MetricIndex = {
    JsonDecoder.decodeMetricIndex(RedisClient.get(s"$METRIC_INDEX_KEYSPACE:$id"))
  }

  def getMetricIndexBy(name: String): MetricIndex = {
    val key = RedisClient.get(s"$INDEX_PREFIX$METRIC_INDEX_KEYSPACE:$name")
    JsonDecoder.decodeMetricIndex(RedisClient.get(key))
  }

}
