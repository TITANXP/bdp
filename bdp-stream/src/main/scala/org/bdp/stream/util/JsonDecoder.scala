package org.bdp.stream.util

import java.sql.Timestamp

import io.circe.Decoder.Result
import io.circe.{Decoder, HCursor}
import io.circe.generic.semiauto.deriveDecoder
import org.bdp.stream.model.{Alert, Metric}
import io.circe.parser._

// 将json字符串解析成实体类
object JsonDecoder {

  implicit private val metricDecoder: Decoder[Metric] = deriveDecoder
  implicit private val alertDecoder: Decoder[Alert] = deriveDecoder
  implicit private val timestampDecoder = new Decoder[Timestamp] {
    override def apply(c: HCursor): Result[Timestamp] = Decoder.decodeLong.map(s => new Timestamp(s)).apply(c)
  }

  def decodeMetric(json: String): Metric = {
    decode[Metric](json).right.get
  }

  def decodeAlert(json: String): Alert = {
    decode[Alert](json).right.get
  }

}
