package org.bdp.master.client.util

import org.bdp.master.client.Constants._
import com.typesafe.scalalogging.LazyLogging
import redis.clients.jedis.{Jedis, JedisPool, JedisPoolConfig}

object RedisClient extends LazyLogging{

  private val pool = new JedisPool(new JedisPoolConfig(), REDIS_HOST, REDIS_PORT)

  // 获取redis连接，并执行传入的lambda
  private def withClient[T](f: Jedis => T): T = {
    val redis = pool.getResource
    try {
      f(redis)
    } catch {
      case e: Throwable =>
        logger.error(s"Redis operation faild! The error mesage is ${e.getMessage}")
        throw e
    } finally {
      redis.close()
    }
  }

  // 获取key对应的值
  def get(key: String): String ={
    withClient(jedis => jedis.get(key))
  }



}
