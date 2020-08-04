package org.bdp.master.client.service

import org.bdp.master.client.Constants._
import com.typesafe.scalalogging.LazyLogging
import org.bdp.master.client.domain.App
import org.bdp.master.client.util.{JsonDecoder, RedisClient}

object AppService extends LazyLogging {

  def getAppBy(id: Long): App = {
    JsonDecoder.decodeApp(RedisClient.get(s"$APP_KEYSPACE:$id"))
  }

  def getAppBy(name: String): App = {
    val key = RedisClient.get(s"$INDEX_PREFIX$APP_KEYSPACE:$name")
    JsonDecoder.decodeApp(RedisClient.get(key))
  }

}
