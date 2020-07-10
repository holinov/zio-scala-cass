package com.weather.scalacass

import java.util.UUID

import com.datastax.driver.core.utils.UUIDs
import com.datastax.driver.core.DataType
import com.google.common.reflect.TypeToken

import scala.util.Try

final case class TimeUUID private (v: UUID) extends AnyVal
object TimeUUID {
  implicit val timeUUIDDecoder: CassFormatDecoder[TimeUUID] =
    CassFormatDecoder[UUID].map[TimeUUID](TimeUUID.applyUnsafe)
  implicit val timeUUIDEncoder: CassFormatEncoder[TimeUUID] =
    CassFormatEncoder[UUID].withCassDataType(DataType.timeuuid()).map[TimeUUID](_.v)
  implicit val Token: TypeToken[TimeUUID] = TypeToken.of(classOf[TimeUUID]).wrap()

  class WrongUUIDType(v: UUID) extends Exception(s"Wrong UUID type found: ${v.variant()} ")

  private def applyUnsafe(v: UUID) = new TimeUUID(v)

  def apply(v: UUID): Try[TimeUUID] = Try(if (v.version() == 1) new TimeUUID(v) else throw new WrongUUIDType(v))

  def apply(): TimeUUID = new TimeUUID(UUIDs.timeBased())
}
