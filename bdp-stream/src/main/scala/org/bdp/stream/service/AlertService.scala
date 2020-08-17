package org.bdp.stream.service

import com.typesafe.scalalogging.LazyLogging
import org.apache.spark.sql.streaming.GroupState
import org.bdp.stream.model.{Alert, AlertRegistry, ServerState}
import org.bdp.stream.util.JsonDecoder
import org.bdp.master.client.service.ServerService
import org.bdp.stream.Constants.ALERT

object AlertService extends LazyLogging {

  // 将json字符串解析为Alert实体类
  def transform(alertMsg: String): Alert = {
    try {
      JsonDecoder.decodeAlert(alertMsg)
    } catch {
      case ex: Exception =>
        logger.error("decode kafka message error: " + ex.getMessage)
        null
    }
  }

  // 使用bdp-master-client的ServerService，通过hostname查找server，然后返回server的id
  def getServerId(hostname: String): Long = {
    ServerService.getServerBy(hostname).id
  }

  /**
   * 更新每个Alert分组的状态信息
   * @param serverId 分组对应的key，因为在AlertStream中是按serverId进行分组的，所以这个就是serverId
   * @param alerts 分组集合的迭代器
   * @param state 分组对应的GroupState实例
   * @return ServerState服务器状态信息
   */
  def updateAlertGroupState(serverId: Long, alerts: Iterator[Alert], state: GroupState[AlertRegistry]): ServerState = {
    // 1. 取出当前分组对应的AlertRegistry
    val alertRegistry = state.getOption.getOrElse(AlertRegistry())
    val now = System.currentTimeMillis()/1000
    // 2. 先清理掉注册表中已经过期，需要被淘汰的数据
    alertRegistry.cleanUp(now: Long)
    // 3. 然后根据新到达的alerts数据更新注册表状态
    alertRegistry.updateWith(alerts)
    state.update(alertRegistry)
    // 4. 汇总注册表信息，生成ServerState
    val severity = alertRegistry.evaluate()
    //  为了在时间戳上和Metric生成的ServerState对齐，所以也将Alert生成的ServerState的时间戳“round”成5s的倍数，以便后续基于时间的关联查询
    val timestamp = (now + 5) / 5 * 5000
    ServerState(serverId, timestamp, ALERT, severity)
  }

}
