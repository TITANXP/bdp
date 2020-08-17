package org.bdp.stream.model

case class ServerState (
                          serverId: Long,
                          timestamp: Long,
                          srcType: String,
                          severity: Int
                       )
