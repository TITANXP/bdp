package org.bdp.stream.assembler

import org.apache.hadoop.hbase.client.Put
import org.apache.hadoop.hbase.util.Bytes
import org.bdp.stream.model.Metric
import org.bdp.stream.Constants._

// 将Metric转换为HBase接受的Put
object MetricAssembler {
  def assemble(metric: Metric): Put = {
    // rowkey: [hostname][metric][timestamp]
    // 假设hostname是固定长度的
    // metric则使用固定长度的缩写：cpu.usage -> cu; mem.used -> mu; alert -> al

    // rowkey:metricId
    val put = new Put(Bytes.toBytes(metric.id))
    // column family, qualifier, value
    put.addColumn(METRIC_COL_FAMILY, METRIC_Q_NAME, Bytes.toBytes(metric.name))
    put.addColumn(METRIC_COL_FAMILY, METRIC_Q_HOSTNAME, Bytes.toBytes(metric.hostname))
    put.addColumn(METRIC_COL_FAMILY, METRIC_Q_TIMESTAMP, Bytes.toBytes(metric.timestamp.getTime))
    put.addColumn(METRIC_COL_FAMILY, METRIC_Q_VALUE, Bytes.toBytes(metric.value))
    put
  }

}
