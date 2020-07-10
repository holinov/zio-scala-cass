package com.weather.scalacass.zcassandra

import com.datastax.driver.core._
import com.weather.scalacass._
import com.weather.scalacass.scsession.{ SCRawSelectStatement, SCStatement }
import zio.{ RIO, Task, UIO, ZIO }
import zio.blocking._
import zio.stream._

import scala.collection.compat._
import scala.jdk.CollectionConverters._
import scala.reflect.ClassTag

case class ZCassandraKeyspaceMeta(ksMeta: KeyspaceMetadata) {
  val tables: Task[List[TableMetadata]] = ZIO.effect(ksMeta.getTables.iterator().asScala.toList)
  val views: Task[List[MaterializedViewMetadata]] =
    ZIO.effect(ksMeta.getMaterializedViews.iterator().asScala.toList)

  def existsTable(name: String): Task[Boolean] =
    tables
      .map(_.map(_.getName).contains(name))

  def existsTableOrView(name: String): Task[Boolean] =
    for {
      tables <- tables
      views  <- views
      names = (tables.map(_.getName) ++ views.map(_.getName)).toSet
    } yield names.contains(name)
}

case class ZCassandraRepos[Entity: CCCassFormatEncoder: CCCassFormatDecoder: ClassTag](
    table: String,
    scalaSession: ScalaSession,
    isTraceEnabled: Boolean = false,
    trace: String => UIO[Unit] = _ => ZIO.unit
) {
  import com.weather.scalacass.syntax._
  private val classTag = implicitly[ClassTag[Entity]]

  protected def executeStmt[T](stmt: SCStatement[T], method: String): RIO[Blocking, T] =
    logStatement(stmt, method)
      .flatMap(_ => blocking(ZIO.fromEither(stmt.execute())))

  protected def logStatement[T](stmt: SCStatement[T], method: String): UIO[Unit] =
    trace(
      s"[zrepos_new] [entity: ${classTag.runtimeClass.getName} method: $method table: ${scalaSession.keyspace}.$table ] Executing ${stmt.toString()}"
    ).when(isTraceEnabled)

  def keyspaceMeta: RIO[Blocking, ZCassandraKeyspaceMeta] =
    ZIO.effect(ZCassandraKeyspaceMeta(scalaSession.session.getCluster.getMetadata.getKeyspace(scalaSession.keyspace)))

  def dropTable: RIO[Blocking, Unit] =
    executeStmt(scalaSession.dropTable(table), "dropTable").unit

  def truncateTable: RIO[Blocking, Unit] =
    executeStmt(scalaSession.truncateTable(table), "truncateTable").unit

  def createTable(numPartitionKeys: Int, numClusteringKeys: Int): RIO[Blocking, Unit] =
    executeStmt(scalaSession.createTable[Entity](table, numPartitionKeys, numClusteringKeys), "create").unit

  def existsTableOrView: RIO[Blocking, Boolean] =
    for {
      meta   <- keyspaceMeta
      exists <- meta.existsTableOrView(table)
    } yield exists

  def ensureTable(numPartitionKeys: Int, numClusteringKeys: Int): RIO[Blocking, Boolean] =
    for {
      exists <- existsTableOrView
      res <- if (!exists) createTable(numPartitionKeys, numClusteringKeys).as(true)
      else ZIO.succeed(false)
    } yield res

  def insert(entity: Entity): RIO[Blocking, ResultSet] =
    executeStmt(scalaSession.insert(table, entity), "insert")

  def updateOne[Query: CCCassFormatEncoder](entity: Entity, query: Query): RIO[Blocking, ResultSet] =
    executeStmt(scalaSession.update(table, entity, query), "update")

  def delete[Query: CCCassFormatEncoder](query: Query): RIO[Blocking, ResultSet] =
    executeStmt(scalaSession.deleteRow(table, query), "delete")

  def select[Query: CCCassFormatEncoder](query: Query): RIO[Blocking, Iterator[Entity]] =
    executeStmt(scalaSession.select[Entity](table, query), "select").map(_.as[Entity])

  def selectOneRaw[Query: CCCassFormatEncoder](query: Query): RIO[Blocking, Option[Row]] =
    executeStmt(scalaSession.selectOneStar(table, query), "selectOneRaw")

  def exists[Query: CCCassFormatEncoder](query: Query): RIO[Blocking, Boolean] =
    executeStmt(scalaSession.selectOne[Query](table, query), "exists").map(_.isDefined)

  def selectOne[Query: CCCassFormatEncoder](query: Query): RIO[Blocking, Option[Entity]] =
    executeStmt(scalaSession.selectOne[Entity](table, query), "selectOne").map(_.as[Entity])

  def selectStream[Query: CCCassFormatEncoder](query: Query): ZStream[Blocking, Throwable, Entity] =
    Stream.unwrap {
      for {
        stmt    <- ZIO.effect(scalaSession.select[Entity](table, query))
        _       <- logStatement(stmt, "selectStream")
        iterEff <- blocking(ZIO.effect(stmt.execute()))
        iter    <- ZIO.fromEither(iterEff)
      } yield Stream.fromIterator(iter.as[Entity])
    }

  def selectRawStream(select: SCRawSelectStatement[scala.Iterator]): Stream[Throwable, Row] =
    Stream.unwrap {
      for {
        _        <- logStatement(select, "selectRawStream")
        etherRes <- ZIO.fromFuture(implicit ec => select.executeAsync())
        res      <- ZIO.fromEither(etherRes)
      } yield Stream.fromIterator(res)
    }
}

object ZCassandraRepos {
  def make[Entity: CCCassFormatEncoder: CCCassFormatDecoder: ClassTag](
      scalaSession: ScalaSession,
      table: String
  ): ZIO[Any, Throwable, ZCassandraRepos[Entity]] =
    for {
      res <- ZIO.effect(new ZCassandraRepos(table, scalaSession))
    } yield res
}
