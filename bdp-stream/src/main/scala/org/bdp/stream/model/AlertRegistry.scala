package org.bdp.stream.model

import com.typesafe.scalalogging.LazyLogging
import scala.collection.mutable
import org.bdp.master.client.service.AlertIndexService
import scala.math._
import org.bdp.stream.Constants._

/**
 * 自定义状态
 */
case class AlertRegistry () extends LazyLogging {

  // 这个Map存储着各种已知类型的Alert在所有发生过incident的时间点上收到的Alert消息的状态，是“已打开尚未关闭”还是“已打开且已关闭”。
  // 它的key是一个二元组，第一个元素是Alert类型的ID; 第二个元素是UNIX格式的时间戳; 再加上分组对应的key（serverId），
  //  这三个“坐标”可以精确定位一个incident，即哪一台服务器在什么时间报了什么告警。
  // Map的value也是一个二元组，默认初始值都是false，第一个元素标记是否收到了OPEN状态的Alert，第二个标记是否受到CLOSE状态的Alert。
  private var registry = mutable.Map[(Long, Long), (Boolean, Boolean)]()

  // 根据传入的Alert更新注册表
  def updateWith(alerts: Iterator[Alert]): Unit = {
    alerts.foreach {
      alert =>
        // 根据Alert消息中的内容找到这个Alert的类型ID
        val id = AlertIndexService.getAlertIndexBy(alert.message).id
        val timestamp = alert.timestamp.getTime
        val status = alert.status
        val key = (id, timestamp)
        // 如果没有对应的key说明这是第一次在这个服务器的这个时间点上发生这类incident
        val oldValue = registry.getOrElse(key, (false, false))
        val newValue = status match {
          case "OPEN" => (true, oldValue._2)
          case "CLOSED" => (oldValue._1, true)
        }
        // 更新map中对应key的值
        registry.update(key, newValue)
    }
  }

    /**
     *  对当前服务器的健康状况进行评估
     *    逐一迭代Map中的每一个元素，
     *    如果存在以OPEN但未CLOSE的alert，说明当前服务器上的一个incident尚未被修复，此时要获取这个Alert的severity（严重程度）留待后用，
     *    然后继续迭代剩余元素
     *    如果出现第二个未CLOSE的Alert，则将它的severity和前一个进行比较，取severity最大的
     *    迭代结束后，就可以得到当前服务器的最高告警等级，也就是这个服务器的当前健康状态
     */
    def evaluate(): Int = {
      registry.foldLeft(0){
        (severity, entry) =>
          val ((id, _), (open, closed)) = entry
          if(open && !closed){
            max(severity, AlertIndexService.getAlertIndexBy(id).severity)
          } else {
            severity
          }
      }
    }

    // 清理过期的Alert
    def cleanUp(now: Long): Unit = {
      registry.filter{
        case((id, timestamp), _) =>
          logger.debug(s"(CURRENT_TIME-ALERT_TIME-ALERT_TIME_TO_LIVE=" +
            s"($now-$timestamp)-$ALERT_TIME_TO_LIVE = ${(now-timestamp)-ALERT_TIME_TO_LIVE}")
          if(now - timestamp < ALERT_TIME_TO_LIVE) {
            logger.debug(s"($id, $timestamp) is kept in session because it is LIVE.")
            true
          } else {
            logger.debug(s"($id, $timestamp) is removed from session because it is EXPIRED.")
            false
          }
      }
    }
}
