package org.bdp.dwh.udf
import org.apache.hadoop.hive.ql.exec.UDF

/**
 * 根据输入的Metric数值和给定的阈值，判断告警级别并返回
 *  Hive和Spark SQL都有自己的UDF机制，由于Spark SQL兼容Hive，所以使用基于Hive实现的UDF有更好的兼容性
 */
class GenRag extends UDF {
  def evaluate(avg: Int, amberThreshold: Int, redThreshold: Int): String = {
    if(avg < amberThreshold)
      "GREEN"
    else if(avg > redThreshold)
      "RED"
    else
      "AMBER"
  }
}