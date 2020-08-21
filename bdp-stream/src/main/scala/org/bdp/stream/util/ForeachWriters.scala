package org.bdp.stream.util

import com.typesafe.scalalogging.LazyLogging
import org.apache.hadoop.hbase.client.BufferedMutator
import org.apache.spark.sql.ForeachWriter
import org.bdp.stream.model.{Alert, Metric, ServerState}
import org.bdp.stream.Constants._
import org.bdp.stream.assembler.{AlertAssembler, MetricAssembler, ServerStateAssembler}

/**
 * 实现自定义的sink
 * 需要继承ForeachWriter，并实现三个方法
 */

// 持久化Metric到HBase
case class MetricWriter() extends ForeachWriter[Metric] with LazyLogging {
  // HBase客户端批量写入数据的接口类
  private var mutator: BufferedMutator = _

  /**
   * 获取相应的资源，建立于sink的连接
   *  open方法中有一个参数partitionId，这说明Structured Streaming的设计在暗示开发者是否要以partition为单位创建Connection
   *  这与在RDD中推荐在foreachPartition中创建数据库连接的思想是一致的
   *  具体到HBase，我们选择的做法是，每个Executor维持一个Connection，每个partition对应一个mutator，这样的映射关系是比较合理的
   */
  override def open(partitionId: Long, version: Long): Boolean = {
    try {
      mutator = HBaseClient.mutator(METRIC_TABLE_NAME)
      logger.info(s"Opening HBase connection & mutator for table [ $METRIC_TABLE_NAME (partitionId=$partitionId) ] is done!")
      true
    } catch {
      case e: Throwable =>
        logger.error(s"Opening HBase mutator for table [ $METRIC_TABLE_NAME (partitionId=$partitionId) ] is failed! the error message is: ${e.getMessage}")
        throw e
        false
    }
  }

  /*
   *  完成数据的写入
   *    通过MetricAssembler将Metric数据转换为HBase接受的put格式数据
   *    然后通过mutator.mutate(put)将put实例加入到Mutator中
   */
  override def process(metric: Metric): Unit = {
    logger.info(s"metric=$metric")
    val put = MetricAssembler.assemble(metric)
    mutator.mutate(put)
  }

  // 释放资源
  override def close(errorOrNull: Throwable): Unit = {
    try {
      mutator.close()
      logger.info(s"Closing HBase connection & mutator for table [ $METRIC_TABLE_NAME ] is done!")
    } catch {
      case e: Throwable =>
        logger.error(s"Closing HBase mutator for table [ $METRIC_TABLE_NAME ] is failed! the error message is: ${e.getMessage}")
        throw e
    }
  }
}

// 持久化Alert到HBase
case class AlertWriter() extends ForeachWriter[Alert] with LazyLogging {

  private var mutator: BufferedMutator = _

  override def open(partitionId: Long, version: Long): Boolean = {
    try {
      mutator = HBaseClient.mutator(s"$ALERT_TABLE_NAME")
      logger.info(s"Opening HBase connection & mutator for table [ $ALERT_TABLE_NAME (partitionId=$partitionId) ] is done!")
      true
    } catch {
      case e: Throwable =>
        logger.error(s"Opening HBase mutator for table [ $ALERT_TABLE_NAME (partitionId=$partitionId) ] is failed! the error message is: ${e.getMessage}")
        throw e
        false
    }
  }

  override def process(alert: Alert): Unit = {
    val put = AlertAssembler.assemble(alert)
    mutator.mutate(put)
    logger.info(s"alert=$alert")
  }

  override def close(errorOrNull: Throwable): Unit = {
    try {
      mutator.close()
      logger.info(s"Closing HBase connection & mutator for table [ $ALERT_TABLE_NAME ] is done!")
    } catch {
      case e: Throwable =>
        logger.error(s"Closing HBase mutator for table [ $ALERT_TABLE_NAME ] is failed! the error message is: ${e.getMessage}")
        throw e
    }
  }
}


// 持久化ServerState到HBase
case class ServerStateWriter() extends ForeachWriter[ServerState] with LazyLogging {

  private var mutator: BufferedMutator = _

  override def open(partitionId: Long, version: Long): Boolean = {
    try {
      mutator = HBaseClient.mutator(SERVER_STATE_TABLE_NAME)
      logger.info(s"Opening HBase connection & mutator for table [ $SERVER_STATE_TABLE_NAME (partitionId=$partitionId) ] is done!")
      true
    } catch {
      case e: Throwable =>
        logger.error(s"Opening Hbase mutator for table [ $SERVER_STATE_TABLE_NAME (partition=$partitionId) ] is failed! the error message is: ${e.getMessage}")
        throw e
        false
    }
  }

  override def process(serverState: ServerState): Unit = {
    val put = ServerStateAssembler.assembler(serverState)
    mutator.mutate(put)
    logger.info(s"serverState=$serverState")
  }

  override def close(errorOrNull: Throwable): Unit = {
    try {
      mutator.close()
      logger.info(s"Closing HBase connection for table [ $SERVER_STATE_TABLE_NAME ] is done!")
    } catch {
      case e: Throwable =>
        logger.error(s"Closing HBase mutator for table [ $SERVER_STATE_TABLE_NAME ] is failed! the error message is: ${e.getMessage}")
        throw e
    }
  }
}
