package com.weather.scalacass

import com.datastax.driver.core.exceptions.{
  BusyConnectionException,
  ConnectionException,
  DriverInternalError,
  NoHostAvailableException,
  PagingStateException,
  QueryExecutionException,
  TransportException,
  UnsupportedFeatureException,
  UnsupportedProtocolVersionException
}

trait NotRecoverableVersionSpecific {
  def apply(t: Throwable) = t match {
    case _: TransportException | _: QueryExecutionException | _: NoHostAvailableException | _: BusyConnectionException |
        _: ConnectionException | _: DriverInternalError | _: PagingStateException | _: UnsupportedFeatureException |
        _: UnsupportedProtocolVersionException =>
      true
    case _ => false
  }
}
