package org.bdp.stream.service

import com.typesafe.scalalogging.LazyLogging
import org.bdp.master.client.domain.SEVERITY.{AMBER, GREEN, RED}
import org.bdp.stream.model.{Metric, ServerState}
import org.bdp.stream.util.JsonDecoder
import org.bdp.master.client.service.ServerService

object MetricService extends LazyLogging {

  // 将json字符串解析为Metric实体类
  def transform(metricMsg: String): Metric = {
    try {
      JsonDecoder.decodeMetric(metricMsg)
    } catch {
      case ex: Exception =>
        logger.error("decode kafka message error: " + ex.getMessage)
        null
    }
  }

  // 根据metric数据返回ServerState对象
  def evaluate(metric: String, row: (String, Long, Double)): ServerState = {
    val (hostname, timestamp, avg) = row
    // 通过bdp-master-client 取出service
    val service = ServerService.getServerBy(hostname)
    // 根据Metric的名称和hostname查询出这台服务器对应Metric的阈值
    val serverId = service.id
    val amberThreshold = service.metricThresholds(metric.replace("_", ".")).amberThreshold
    val redThreshold = service.metricThresholds(metric.replace("_", ".")).redThreshold
    // 将平均值和阈值进行比较，以确定ServerState的严重等级
    val severity = avg match {
      case avg if avg < amberThreshold => GREEN
      case avg if avg >= redThreshold => RED
      case _ => AMBER
    }
    // 创建ServerState对象并返回
    ServerState(serverId, timestamp, metric, severity.id)
  }
}
