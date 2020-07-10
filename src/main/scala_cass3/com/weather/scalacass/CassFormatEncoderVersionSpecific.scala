package com.weather.scalacass

import com.datastax.driver.core.{ Cluster, DataType, TupleValue }

trait LowPriorityCassFormatEncoderVersionSpecific {
  implicit def tupleFormat[TUP <: Product](
      implicit cluster: Cluster,
      underlying: TupleCassFormatEncoder[TUP]
  ): CassFormatEncoder[TUP] = new CassFormatEncoder[TUP] {
    type From = TupleValue
    val cassDataType =
      cluster.getMetadata.newTupleType(underlying.dataTypes: _*)
    def encode(f: TUP): Result[From] =
      underlying.encode(f).map(ar => cassDataType.newValue(ar: _*))
  }
}
trait CassFormatEncoderVersionSpecific extends LowPriorityCassFormatEncoderVersionSpecific {
  import CassFormatEncoder.{ sameTypeCassFormatEncoder, transCassFormatEncoder }

  implicit val dateFormat: CassFormatEncoder[java.util.Date] =
    sameTypeCassFormatEncoder(DataType.timestamp)
  implicit val datastaxLocalDateFormat: CassFormatEncoder[com.datastax.driver.core.LocalDate] =
    sameTypeCassFormatEncoder(DataType.date)
  implicit val timeFormat: CassFormatEncoder[Time] =
    transCassFormatEncoder(DataType.time, time => Long.box(time.millis))
}
