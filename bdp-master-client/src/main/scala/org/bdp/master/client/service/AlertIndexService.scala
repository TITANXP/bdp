package org.bdp.master.client.service

import com.typesafe.scalalogging.LazyLogging
import org.bdp.master.client.domain.AlertIndex
import org.bdp.master.client.util.{JsonDecoder, RedisClient}
import org.bdp.master.client.Constants._

object AlertIndexService extends LazyLogging {

  def getAlertIndexBy(id: Long): AlertIndex = {
    JsonDecoder.decodeAlertIndex(RedisClient.get(s"$ALERT_INDEX_KEYSPACE:$id"))
  }

  def getAlertIndexBy(name: String): AlertIndex = {
    val key = RedisClient.get(s"$INDEX_PREFIX$ALERT_INDEX_KEYSPACE:$name")
    JsonDecoder.decodeAlertIndex(RedisClient.get(key))
  }

}
