package org.bdp.stream

import com.typesafe.scalalogging.LazyLogging
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.streaming.GroupStateTimeout
import org.bdp.stream.model.{Alert, AlertRegistry}
import org.bdp.stream.util.{AlertWriter, ServerStateWriter}
import org.bdp.stream.Constants._
import org.bdp.stream.service.AlertService

object AlertStream extends LazyLogging {

  def restream()(implicit sparkSession: SparkSession): Unit = {
    // 将原生的Alert数据持久化到HBase
    persist
    // 对Alert进行评估，评估时会生成并维护Alert状态，评估的结果就是ServerState
    evaluate
  }

  def persist(implicit sparkSession: SparkSession): Unit = {
    logger.info("alert persist")
    import sparkSession.implicits._
    sparkSession.sparkContext.setLocalProperty("spark.scheduler.pool", s"pool_persist_alert")
    sparkSession
      .sql(s"SELECT * FROM alert").as[Alert]
      .writeStream
      .outputMode("update")
      .foreach(AlertWriter())
      .queryName(s"persist_alert")
      .start
  }

  def evaluate(implicit sparkSession: SparkSession): Unit = {
    logger.info("alert evaluate")
    import sparkSession.implicits._
    implicit val stateEncoder = org.apache.spark.sql.Encoders.kryo[AlertRegistry]
    sparkSession.sparkContext.setLocalProperty("spark.scheduler.pool", s"pool_evaluate_alert")
    sparkSession
      .sql(s"SELECT * FROM alert").as[Alert]
      .withWatermark("timestamp", ALERT_WATERMARK)
      //对Alert按服务器进行分组，为面向服务器的状态评估做准备
      .groupByKey(alert => AlertService.getServerId(alert.hostname))
      .mapGroupsWithState(GroupStateTimeout.NoTimeout)(AlertService.updateAlertGroupState)
      .writeStream
      .outputMode("update")
      .foreach(ServerStateWriter())
      .queryName(s"evaluate_alert")
      .start
  }

}
