resolvers ++= Seq(Resolver.sonatypeRepo("releases"))

addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.9.18")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.3.2")
