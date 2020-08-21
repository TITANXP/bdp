package org.bdp.stream

import com.typesafe.scalalogging.LazyLogging
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions.{avg, unix_timestamp, window}
import org.bdp.stream.model.Metric
import org.bdp.stream.util.{MetricWriter, ServerStateWriter}
import org.bdp.stream.Constants._
import org.bdp.stream.service.MetricService

object MetricStream extends LazyLogging{

  def restream(metric: String)(implicit sparkSession: SparkSession): Unit = {
    // 将原生的Metric数据持久化到HBase
    persist(metric)
    // 对Metric数据进行评估并生成ServerState，并将ServerState持久化到HBase
    evaluate(metric)
  }

  def persist(metric: String)(implicit sparkSession: SparkSession): Unit = {
    logger.info(s"$metric persist")
    import sparkSession.implicits._
    sparkSession.sparkContext.setLocalProperty("spark.scheduler.pool", s"pool_persist_$metric")// 为这些短作业引入独立的scheduler pool
    sparkSession
      .sql(s"SELECT * FROM $metric").as[Metric]
      .writeStream
      .outputMode("update")
      .foreach(MetricWriter())
      .queryName(s"persist_$metric")
      .start
//    sparkSession
//      .sql(s"SELECT * FROM $metric").as[Metric]
//      .writeStream
//      .format("console")
//      .start
  }

  def evaluate(metric: String)(implicit sparkSession: SparkSession): Unit = {
    logger.info(s"$metric evaluate")
    import sparkSession.implicits._
    sparkSession.sparkContext.setLocalProperty("spark.scheduler.pool", s"pool_persist_$metric")
    sparkSession
      .sql(s"SELECT * FROM $metric").as[Metric]
      .withWatermark("timestamp", METRIC_WATERMARK) //允许数据的最大延迟，如果系统当前时间和数据的时间戳相差超过阈值，则舍弃这条数据
      .dropDuplicates("id", "timestamp")  // 根据id和timestamp进行去重
      .groupBy($"hostname", window($"timestamp", WINDOW, SLIDE)) // 每5s截取过去60s内的数据，并按服务器进行分组
      .agg(avg($"value") as "avg") // 计算Metric的value字段的平均值，并将结果作为新的列avg
      // 经过groupBy和avg处理后，Dataset的数据格式只剩下hostnaem、avg、window三列
      // 其中window是一个复合结构，包含start和end两个timestamp类型的字段
      // 我们将window.end作为ServerState的时间戳
      .select($"hostname", (unix_timestamp($"window.end") cast "bigint") as "timestamp", $"avg") // 用select取出hostname，avg，window.end封装成一个三元组
      .as[(String, Long, Double)]
      // 去和阈值进行对比,并生成ServerState对象
      .map(MetricService.evaluate(metric, _))
      .writeStream
      .outputMode("update")
      .foreach(ServerStateWriter())
      .queryName(s"evalute_$metric")
      .start
  }

}
