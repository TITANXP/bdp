package org.bdp.master.client.domain

case class TSD(
                metric: String,
                value: String,
                timestamp: Long,
                tags: Map[String, String]
              )
