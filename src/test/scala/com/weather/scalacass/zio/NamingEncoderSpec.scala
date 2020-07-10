package com.weather.scalacass.zio
import com.datastax.driver.core.Session
import com.weather.scalacass.{ NameEncoders, ScalaSession }
import zio.test._
import zio.test.Assertion._

object NamingEncoderSpec extends DefaultRunnableSpec {
  case class Query(str: String, strVal1: String)

  def spec = suite("NamingEncoderSpec")(
    test("check name encoder applies") {
      NameEncoders.setNameEncoder(NameEncoders.snakeCaseEncoder)
      val ss: ScalaSession = new ScalaSession("ks")(null: Session)
      val query            = ss.select[Query]("table1", Query("asdf", "fff"))
      assert(query.getStringRepr.getOrElse(""))(
        equalTo("SELECT str, str_val1 FROM ks.table1 WHERE str=? AND str_val1=?")
      )
    }
  )
}
