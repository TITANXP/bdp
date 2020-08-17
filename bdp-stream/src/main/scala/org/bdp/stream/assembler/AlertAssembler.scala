package org.bdp.stream.assembler

import org.apache.hadoop.hbase.client.Put
import org.apache.hadoop.hbase.util.Bytes
import org.bdp.stream.model.Alert
import org.bdp.stream.Constants._

// 将Alert转换为HBase接受的Put
object AlertAssembler {
  def assemble(alert: Alert): Put = {
    // rowkey
    val put = new Put(Bytes.toBytes(alert.id))
    // column family, qualifier, value
    put.addColumn(ALERT_COL_FAMILY, ALERT_Q_HOSTNAME, Bytes.toBytes(alert.hostname))
    put.addColumn(ALERT_COL_FAMILY, ALERT_Q_MESSAGE, Bytes.toBytes(alert.message))
    put.addColumn(ALERT_COL_FAMILY, ALERT_Q_STATUS, Bytes.toBytes(alert.status))
    put.addColumn(ALERT_COL_FAMILY, ALERT_Q_TIMESTAMP, Bytes.toBytes(alert.timestamp.getTime))
    put
  }
}
