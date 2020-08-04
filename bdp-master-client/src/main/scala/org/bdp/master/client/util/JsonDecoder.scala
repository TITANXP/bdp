package org.bdp.master.client.util

import java.sql.Timestamp

import io.circe.Decoder.Result
import io.circe.{Decoder, HCursor}
import io.circe.generic.semiauto.deriveDecoder
import io.circe.jawn.decode
import org.bdp.master.client.domain.{AlertIndex, App, MetricIndex, MetricThreshold, Server}

object JsonDecoder {
  //  Encoder[A]是将A类型转化成JSON的函数，Decoder[A]是将Json转化成一个A对象或者是exception的函数
  implicit val appDecoder: Decoder[App] = deriveDecoder[App]
  implicit val serverDecoder: Decoder[Server] = deriveDecoder[Server]
  implicit val metricThresholdDecoder: Decoder[MetricThreshold] = deriveDecoder[MetricThreshold]
  implicit val metricIndexDecoder: Decoder[MetricIndex] = deriveDecoder[MetricIndex]
  implicit val alertIndexDecoder: Decoder[AlertIndex] = deriveDecoder[AlertIndex]
  implicit val timestampDecoder = new Decoder[Timestamp] {
    override def apply(c: HCursor): Result[Timestamp] = Decoder.decodeLong.map(s => new Timestamp(s)).apply(c)
  }

  def decodeApp(json: String): App = {
    decode[App](json).right.get
  }

  def decodeServer(json: String): Server = {
    decode[Server](json).right.get
  }

  def decodeMetricIndex(json: String): MetricIndex = {
    println(decode[MetricIndex](json).left.e)
    decode[MetricIndex](json).right.get
  }

  def decodeAlertIndex(json: String): AlertIndex = {
    decode[AlertIndex](json).right.get
  }

}
