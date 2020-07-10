package com.weather.scalacass.scsession

import com.weather.scalacass.NameEncoders

class NamingEncoderTests extends ActionUnitTests {
  case class SelectiveSelect(i: Int)
  case class Query(str: String)

  "name encoder" should "use defined encoding" in {
    NameEncoders.setNameEncoder(NameEncoders.upperCaseEncoder)
    val query = ss.select[SelectiveSelect](table, Query("asdf"))
    println(query.getStringRepr.getOrElse(None))
    println(query.execute().getOrElse(None))
  }
}
