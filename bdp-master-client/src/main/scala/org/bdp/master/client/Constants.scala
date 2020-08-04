package org.bdp.master.client

import com.typesafe.config.{Config, ConfigFactory}

/**
 * 定义常量
 */
object Constants {
  // 加载配置文件
  private val config: Config = ConfigFactory.load("bdp-master-client.conf")

  val APP_SERVICE = "APP_SERVICE"
  val SERVER_SERVICE = "SERVER_SERVICE"
  val METRIC_THRESHOLD_SERVICE = "METRIC_THRESHOLD_SERVICE"

  val APP_USAGE = "app.usage"
  val MEM_USED = "mem.used"

  val APP_KEYSPACE = "app"
  val SERVER_KEYSPACE = "server"
  val METRIC_INDEX_KEYSPACE = "metric_index"
  val ALERT_INDEX_KEYSPACE = "alert_index"
  val INDEX_PREFIX = "i_"


  val REDIS_HOST = config.getString("redis.host")
  val REDIS_PORT = config.getInt("redis.port")
}
