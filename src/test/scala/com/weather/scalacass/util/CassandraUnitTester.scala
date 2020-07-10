package com.weather.scalacass.util

import com.weather.scalacass.NameEncoders
import org.cassandraunit.utils.EmbeddedCassandraServerHelper

abstract class CassandraUnitTester extends CassandraTester {
  override def beforeAll() = {
    super.beforeAll()
    NameEncoders.setNameEncoder(NameEncoders.snakeCaseEncoder)
    EmbeddedCassandraServerHelper.startEmbeddedCassandra(
      EmbeddedCassandraServerHelper.CASSANDRA_RNDPORT_YML_FILE,
      30000L
    )
    _client = Some(CassandraClient(List("localhost"), Some(EmbeddedCassandraServerHelper.getNativeTransportPort)))
  }

  override def afterAll() =
    EmbeddedCassandraServerHelper.cleanEmbeddedCassandra()

  def beforeEach(): Unit = {}
  def afterEach(): Unit  = {}

  before {
    beforeEach()
  }

  after {
    afterEach()
  }
}
