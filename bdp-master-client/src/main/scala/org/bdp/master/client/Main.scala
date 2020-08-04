package org.bdp.master.client

import com.typesafe.scalalogging.LazyLogging
import org.bdp.master.client.service.{AlertIndexService, AppService, MetricIndexService, ServerService}

object Main extends App with LazyLogging {
  println(AppService.getAppBy(1))
  println(AppService.getAppBy("MyCRM"))

  println(ServerService.getServerBy(1))
  println(ServerService.getServerBy("svr1001"))

  println(MetricIndexService.getMetricIndexBy(1))
  println(MetricIndexService.getMetricIndexBy("cpu.usage"))

  println(AlertIndexService.getAlertIndexBy(1))
  println(AlertIndexService.getAlertIndexBy("free space warning (mb) for host disk"))
}
