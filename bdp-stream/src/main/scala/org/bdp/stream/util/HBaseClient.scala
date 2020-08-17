package org.bdp.stream.util

import com.typesafe.scalalogging.LazyLogging
import org.apache.hadoop.hbase.{HBaseConfiguration, TableName}
import org.apache.hadoop.hbase.client.{BufferedMutator, BufferedMutatorParams, Connection, ConnectionFactory, RetriesExhaustedWithDetailsException}

import scala.collection.JavaConverters.asScalaBufferConverter
import org.bdp.stream.Constants._

// 在Spark的每个Executor上 创建并维持一个Connection实例， 每个partition对应一个mutator
object HBaseClient extends LazyLogging {

  private val connection = createConnection()

  private val mutatorParams = createMutatorParams()

  // 为每个表创建mutatorParams
  private def createMutatorParams(): Map[String, BufferedMutatorParams] = {
    Map[String, BufferedMutatorParams] (
      METRIC_TABLE_NAME -> createMutatorParams(METRIC_TABLE_NAME),
      ALERT_TABLE_NAME -> createMutatorParams(ALERT_TABLE_NAME),
      SERVER_STATE_TABLE_NAME -> createMutatorParams(SERVER_STATE_TABLE_NAME)
    )
  }

  // 根据表名创建mutatorParams
  private def createMutatorParams(tableName: String): BufferedMutatorParams = {
    val listener = new BufferedMutator.ExceptionListener {
      override def onException(e: RetriesExhaustedWithDetailsException, bufferedMutator: BufferedMutator): Unit = {
        for (cause: Throwable <- e.getCauses.asScala) {
          for (cause: Throwable <- e.getCauses.asScala) {
            logger.error(s"HBase put operation failed! the error message is: ${cause.getMessage}")
            cause.printStackTrace()
          }
          throw e
        }
      }
    }
    new BufferedMutatorParams(TableName.valueOf(tableName)).listener(listener)
  }

  // 创建connection
  private def createConnection(): Connection = {
    try {
      val conf = HBaseConfiguration.create()
      conf.addResource("hbase-site.xml")
      ConnectionFactory.createConnection(conf)
    } catch {
      case e: Throwable =>
        logger.error(s"HBase create connection operation failed! the error message is: ${e.getMessage}")
        throw e
    }
  }

  // 根据表名创建并返回对应的mutator
  def mutator(tableName: String): BufferedMutator = {
    try {
      connection.getBufferedMutator(mutatorParams(tableName))
    } catch {
      case e: Exception =>
        logger.error(s"HBase get mutator operation failed! the error message is: ${e.getMessage}")
        throw e
    }
  }
}

