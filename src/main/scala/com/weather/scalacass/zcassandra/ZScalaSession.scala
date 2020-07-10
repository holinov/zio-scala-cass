package com.weather.scalacass.zcassandra

import com.datastax.driver.core.Session
import com.weather.scalacass.{ CCCassFormatDecoder, CCCassFormatEncoder, ScalaSession }
import zio.{ Has, RIO, Task, UIO, ZIO, ZLayer }

import scala.reflect.ClassTag

object ZScalaSession {
  type ZScalaSession = Has[Service]

  trait Service {
    def scalaSession: UIO[ScalaSession]
    def createKeyspace: UIO[Unit]

    def repo[Entity: CCCassFormatEncoder: CCCassFormatDecoder: ClassTag](
        table: String
    ): Task[ZCassandraRepos[Entity]] =
      scalaSession.flatMap(ss => ZCassandraRepos.make[Entity](ss, table))
  }

  def fromSession(ses: Session, ks: String): Service = new Service {
    override def scalaSession: UIO[ScalaSession] = ZIO.effectTotal(ScalaSession(ks)(ses))

    override def createKeyspace: UIO[Unit] =
      scalaSession
        .map(_.createKeyspace("replication = {'class': 'SimpleStrategy', 'replication_factor': 1}").execute())
        .unit
  }

  def repo[Entity: CCCassFormatEncoder: CCCassFormatDecoder: ClassTag](
      table: String
  ): RIO[ZScalaSession, ZCassandraRepos[Entity]] =
    ZIO.environment[ZScalaSession].flatMap(_.get.repo(table))

  def scalaSession: RIO[ZScalaSession, ScalaSession] =
    ZIO.environment[ZScalaSession].flatMap(_.get.scalaSession)

}
