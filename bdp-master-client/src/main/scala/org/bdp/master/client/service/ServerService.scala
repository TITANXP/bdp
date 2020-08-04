package org.bdp.master.client.service

import com.typesafe.scalalogging.LazyLogging
import org.bdp.master.client.domain.Server
import org.bdp.master.client.util.{JsonDecoder, RedisClient}
import org.bdp.master.client.Constants._

object ServerService extends LazyLogging{

  def getServerBy(id: Long): Server = {
    JsonDecoder.decodeServer(RedisClient.get(s"$SERVER_KEYSPACE:$id"))
  }

  def getServerBy(hostname: String): Server = {
    val key = RedisClient.get(s"$INDEX_PREFIX$SERVER_KEYSPACE:$hostname")
    JsonDecoder.decodeServer(RedisClient.get(key))
  }

}
