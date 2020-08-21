package org.bdp.stream

import com.typesafe.scalalogging.LazyLogging
import org.apache.spark.sql.SparkSession
import org.bdp.stream.Constants._
import org.bdp.stream.service.{AlertService, MetricService}

object Main extends LazyLogging{
  def main(args: Array[String]): Unit = {
    try {
      //创建SparkSession
      implicit val sparkSession = SparkSession
        .builder
        .appName("bdp-stream")
        .config("spark.cleaner.referenceTracking.cleanCheckpoints", true)
        .config("spark.streaming.stopGracefullyOnShutdown", true)
        .getOrCreate()

      import sparkSession.implicits._

      // 从kafka读取数据
      sparkSession
        .readStream
        .format("kafka")
        .option("kafka.bootstrap.servers", KAFKA_BROKER_LIST)
        .option("subscribe", s"$TOPIC_CPU_USAGE, $TOPIC_MEM_USED, $TOPIC_ALERT") //订阅的topic
        .option("startingOffsets", "latest") // latest:从最晚进入队列中的消息开始; earliest: 从队列中最早的消息开始
        .load()
        .selectExpr("CAST(key AS STRING)", "CAST(value AS STRING)")
        .createTempView("trunk")

      //测试
//      sparkSession
//        .sql("select * from trunk")
//        .writeStream
//        .format("console")
//        .start

      // 形成三条支流，对三类数据分别进行处理
      // cpu_usage
      if (CPU_USAGE_STREAM_ENABLED) {
        logger.info("[ CPU USAGE ] streaming is enabled!")
        sparkSession
          .sql(s"SELECT value FROM trunk WHERE key LIKE '$CPU_USAGE_MSG_KEY_PREFIX%'").as[String]
          .map(MetricService.transform(_)) // 将json解析为Metric类
          .createTempView(CPU_USAGE)
        MetricStream.restream(CPU_USAGE)
      }

      // mem_used
      if (MEM_USED_STREAM_ENABLED) {
        logger.info("[ MEM USED ] streaming is enabled!")
        sparkSession
          .sql(s"SELECT value FROM trunk WHERE key LIKE '$MEM_USED_MSG_KEY_PREFIX%'").as[String]
          .map(MetricService.transform(_))
          .createTempView(MEM_USED)
        MetricStream.restream(MEM_USED)
      }

      // alert
      if (ALERT_STREAM_ENABLED) {
        logger.info("[ ALERT ] streaming is enabled!")
        sparkSession
          .sql(s"SELECT value FROM trunk WHERE key LIKE '$ALERT_MSG_KEY_PREFIX%'").as[String]
          .map(AlertService.transform(_))
          .createTempView(ALERT)
        AlertStream.restream()
      }
      sparkSession.streams.awaitAnyTermination()
    } catch {
      case e: Throwable => e.printStackTrace()
    }

  }
}
