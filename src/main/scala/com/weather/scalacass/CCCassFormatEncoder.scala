package com.weather.scalacass

import com.weather.scalacass.NameEncoders._
import shapeless.labelled.FieldType
import shapeless.{ ::, HList, HNil, LabelledGeneric, Lazy, Witness }

abstract class DerivedCCCassFormatEncoder[F] extends CCCassFormatEncoder[F]

object NameEncoders {
  trait NameEncoder {
    def apply(name: String): String
  }

  val identityEncoder: NameEncoder  = (name: String) => name
  val upperCaseEncoder: NameEncoder = _.toUpperCase()
  val snakeCaseEncoder: NameEncoder = snakify

  private[this] val _camel1 = "([A-Z]+)([A-Z][a-z])".r
  private[this] val _camel2 = "([a-z\\d])([A-Z])".r
  private def snakify(field: String): String =
    _camel2.replaceAllIn(_camel1.replaceAllIn(field, "$1_$2"), "$1_$2").toLowerCase

//  private def bbq(field: String): String = _camel2.replaceAllIn(_camel1.replaceAllIn(field, "$1-$2"), "$1-$2").toLowerCase
//  private def camelize(name: String, firstIsUpper: Boolean = false): String = {
//    val chars = name.toCharArray
//    for (i <- 0 until chars.length - 1) {
//      if (chars(i) == '_') chars(i + 1) = chars(i + 1).toUpper
//    }
//    if (firstIsUpper) chars(0) = chars(0).toUpper
//    new String(chars.filter(_ != '_'))
//  }

  private var _nameEncoder: NameEncoder      = identityEncoder
  def setNameEncoder(enc: NameEncoder): Unit = _nameEncoder = enc
  def nameEncoder: NameEncoder               = _nameEncoder
}
object DerivedCCCassFormatEncoder {

  implicit val hNilEncoder: DerivedCCCassFormatEncoder[HNil] =
    new DerivedCCCassFormatEncoder[HNil] {
      def encodeWithName(f: HNil)  = Right(Nil)
      def encodeWithQuery(f: HNil) = Right(Nil)

      val names = Nil
      val types = Nil
    }

  implicit def hConsEncoder[K <: Symbol, H, T <: HList](
      implicit w: Witness.Aux[K],
      tdH: Lazy[CassFormatEncoder[H]],
      tdT: Lazy[DerivedCCCassFormatEncoder[T]]
  ): DerivedCCCassFormatEncoder[FieldType[K, H] :: T] =
    new DerivedCCCassFormatEncoder[FieldType[K, H] :: T] {
      private lazy val wName = nameEncoder(w.value.name)
      def encodeWithName(f: FieldType[K, H] :: T) =
        for {
          h <- tdH.value.encode(f.head)
          t <- tdT.value.encodeWithName(f.tail)
        } yield (wName, h) :: t
      def encodeWithQuery(f: FieldType[K, H] :: T) =
        for {
          h <- tdH.value.encode(f.head)
          t <- tdT.value.encodeWithQuery(f.tail)
        } yield (tdH.value.withQuery(f.head, wName), h) :: t
      def names = wName :: tdT.value.names
      def types = tdH.value.cassType :: tdT.value.types
    }

  implicit def ccConverter[T, Repr <: HList](
      implicit gen: LabelledGeneric.Aux[T, Repr],
      hListDecoder: Lazy[DerivedCCCassFormatEncoder[Repr]]
  ): DerivedCCCassFormatEncoder[T] =
    new DerivedCCCassFormatEncoder[T] {
      def encodeWithName(f: T)  = hListDecoder.value.encodeWithName(gen.to(f))
      def encodeWithQuery(f: T) = hListDecoder.value.encodeWithQuery(gen.to(f))
      def names                 = hListDecoder.value.names
      def types                 = hListDecoder.value.types
    }
}

trait CCCassFormatEncoder[F] { self =>
  def encodeWithName(f: F): Result[List[(String, AnyRef)]]
  def encodeWithQuery(f: F): Result[List[(String, AnyRef)]]
  def names: List[String]
  def types: List[String]
  def namesAndTypes: List[(String, String)] = names zip types

  final def map[G](fn: G => F): CCCassFormatEncoder[G] =
    new CCCassFormatEncoder[G] {
      def encodeWithName(f: G): Result[List[(String, AnyRef)]] =
        self.encodeWithName(fn(f))
      def encodeWithQuery(f: G): Result[List[(String, AnyRef)]] =
        self.encodeWithQuery(fn(f))
      def names = self.names
      def types = self.types
    }
  final def flatMap[G](fn: G => Result[F]): CCCassFormatEncoder[G] =
    new CCCassFormatEncoder[G] {
      def encodeWithName(f: G): Result[List[(String, AnyRef)]] =
        fn(f).flatMap(self.encodeWithName)
      def encodeWithQuery(f: G): Result[List[(String, AnyRef)]] =
        fn(f).flatMap(self.encodeWithQuery)
      def names = self.names
      def types = self.types
    }
}

object CCCassFormatEncoder extends ProductCCCassFormatEncoders {
  implicit def derive[T](
      implicit derived: Lazy[DerivedCCCassFormatEncoder[T]]
  ): CCCassFormatEncoder[T] = derived.value
  def apply[T](
      implicit instance: CCCassFormatEncoder[T]
  ): CCCassFormatEncoder[T] = instance
}
