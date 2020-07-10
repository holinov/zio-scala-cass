resolvers ++= Seq(Resolver.sonatypeRepo("releases"))

addSbtPlugin("org.foundweekends" % "sbt-bintray"  % "0.5.4")
addSbtPlugin("ch.epfl.scala"     % "sbt-scalafix" % "0.9.18")
addSbtPlugin("org.scalameta"     % "sbt-scalafmt" % "2.3.2")
