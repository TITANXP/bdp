package org.bdp.stream.assembler

import org.apache.hadoop.hbase.client.Put
import org.apache.hadoop.hbase.util.Bytes
import org.bdp.stream.model.ServerState
import org.bdp.stream.Constants._

// 将ServerState转换为HBase接受的Put
object ServerStateAssembler {
  def assembler(serverState: ServerState): Put = {
    // rowkey:serverId+timestamp
    val put = new Put(Bytes.toBytes(serverState.serverId) ++ Bytes.toBytes(serverState.timestamp))
    // column family, qualifier, value
    put.addColumn(SERVER_STATE_COL_FAMILY, SERVER_STATE_Q_SEVERITY, Bytes.toBytes(serverState.severity))
    put.addColumn(SERVER_STATE_COL_FAMILY, SERVER_STATE_Q_SRC_TYPE, Bytes.toBytes(serverState.srcType))
    put
  }
}
