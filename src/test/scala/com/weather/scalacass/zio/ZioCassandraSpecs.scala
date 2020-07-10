package com.weather.scalacass.zio
import com.datastax.driver.core.{ Cluster, Session }
import com.weather.scalacass.{ NameEncoders, ScalaSession }
import org.cassandraunit.utils.EmbeddedCassandraServerHelper
import zio.test._
import zio.test.Assertion._
import com.weather.scalacass.zcassandra.ZScalaSession._
import zio.{ ULayer, ZIO, ZLayer, ZManaged }

case class Entity(vVal1: String, vVal2: String)
case class Query(str: String, strVal1: String)

object ZioCassandraSpecs extends DefaultRunnableSpec {
  def spec = suite("ZioCassandraSpecs")(
    suite("managed repository")(
      testM("check name encoder applies") {
        for {
          sess <- scalaSession
          query = sess.select[Query]("table1", Query("asdf", "fff"))
        } yield assert(query.getStringRepr.getOrElse(""))(
          equalTo(s"SELECT str, str_val1 FROM ${ks}.table1 WHERE str=? AND str_val1=?")
        )
      },
      testM("ensures table") {
        for {
          repo     <- repo[Entity]("table1")
          _        <- repo.ensureTable(1, 0)
          isExists <- repo.existsTableOrView
          _        <- repo.dropTable
        } yield assert(isExists)(equalTo(true))
      },
      testM("it creates table using shared connection") {
        for {
          repo     <- repo[Entity]("table2")
          _        <- repo.createTable(1, 0)
          isExists <- repo.existsTableOrView
        } yield assert(isExists)(equalTo(true))
      }
    ).provideCustomLayerShared(embeededClusterClient)
  )

  val ks = "KS1"
  val embeededClusterClient: ULayer[ZScalaSession] = {
    def build =
      for {
        _ <- ZIO.effect(NameEncoders.setNameEncoder(NameEncoders.snakeCaseEncoder))
        _ <- ZIO.effect(
          EmbeddedCassandraServerHelper
            .startEmbeddedCassandra(EmbeddedCassandraServerHelper.CASSANDRA_RNDPORT_YML_FILE, 30000L)
        )
        cluster <- ZIO.effect {
          val c = Cluster.builder().addContactPoint("localhost")
          c.withPort(EmbeddedCassandraServerHelper.getNativeTransportPort)
          c.build()
        }
        session <- ZIO.effect(cluster.connect())
        zs = fromSession(session, ks)
        _ <- zs.createKeyspace
      } yield (zs, session)

    val managed = ZManaged
      .make(build) {
        case (service, session) =>
          for {
            _ <- ZIO.effectTotal(session.close())
            _ <- ZIO.effectTotal(EmbeddedCassandraServerHelper.cleanEmbeddedCassandra())
          } yield service
      }
      .map(_._1)
    ZLayer.fromManaged(managed.orDie)
  }

}
