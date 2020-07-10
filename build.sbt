val baseVersion       = "4.0.0"
val cassandra3Version = "3.7.1"
val cassandra2Version = "2.1.10.3"
val cassandraVersion = sys.props.getOrElse("cassandra-driver.version", cassandra3Version) match {
  case v @ (`cassandra3Version` | `cassandra2Version`) => v
  case _ =>
    throw new IllegalArgumentException(s"cassandra version must be one of $cassandra3Version, $cassandra2Version")
}

def addUnmanagedSourceDirsFrom(folder: String) = {
  def addSourceFilesTo(conf: Configuration) =
    unmanagedSourceDirectories in conf := {
      val sds = (unmanagedSourceDirectories in conf).value
      val sd  = (sourceDirectory in conf).value
      sds :+ new java.io.File(sd, folder)
    }

  Seq(addSourceFilesTo(Compile), addSourceFilesTo(Test))
}

lazy val commonSettings = Seq(
  scalaVersion := "2.13.2",
  semanticdbEnabled := true,
  semanticdbVersion := scalafixSemanticdb.revision,
  scalacOptions ++= Seq(
      "-encoding",
      "UTF-8",
      "-feature",
      "-language:existentials",
      "-language:higherKinds",
      "-language:implicitConversions",
      "-unchecked",
      "-Xfatal-warnings",
      "-Ywarn-numeric-widen",
      "-Ywarn-value-discard",
      "-Ymacro-annotations"
    ),
  (scalacOptions in Test) -= "-Xfatal-warnings",
  parallelExecution in Test := false
)

lazy val applicationSettings = Seq(
  name := "ScalaCass",
  organization := "com.github.thurstonsand",
  description := "a wrapper for the Java Cassandra driver that uses case classes to simplify and codify creating cached statements in a type-safe manner",
  version := s"$baseVersion-$cassandraVersion",
  libraryDependencies ++= Seq(
      "com.google.code.findbugs" % "jsr305"                % "3.0.1" % "provided", // Intellij does not like "compile-internal, test-internal", use "provided" instead
      "org.joda"                 % "joda-convert"          % "1.8.1" % "provided", // Intellij does not like "compile-internal, test-internal", use "provided" instead
      "org.slf4j"                % "slf4j-api"             % "1.7.25" % "provided", // Intellij does not like "compile-internal, test-internal", use "provided" instead
      "joda-time"                % "joda-time"             % "2.9.4",
      "com.chuusai"              %% "shapeless"            % "2.3.3",
      "com.google.guava"         % "guava"                 % "19.0",
      "com.datastax.cassandra"   % "cassandra-driver-core" % cassandraVersion classifier "shaded" excludeAll ExclusionRule(
        "com.google.guava",
        "guava"
      ),
      "org.scalatest" %% "scalatest" % "3.2.0" % "test"
    ) ++ (if (cassandraVersion startsWith "2.1.")
            Seq(
              "org.cassandraunit" % "cassandra-unit" % "2.2.2.1" % "test"
            )
          else
            Seq(
              "com.datastax.cassandra" % "cassandra-driver-extras" % cassandraVersion excludeAll (ExclusionRule(
                "com.datastax.cassandra",
                "cassandra-driver-core"
              ), ExclusionRule("com.google.guava", "guava")),
              "org.cassandraunit" % "cassandra-unit" % "3.3.0.2" % "test"
            ))
)

lazy val noPublishSettings = Seq(
  publish := ((): Unit),
  publishLocal := ((): Unit),
  publishArtifact := false
)

lazy val publishSettings = Seq(
  homepage := Some(url("https://github.com/thurstonsand")),
  licenses := Seq("MIT" -> url("http://www.opensource.org/licenses/mit-license.php")),
  pomExtra :=
    <scm>
      <url>git@github.com/thurstonsand/scala-cass.git</url>
      <connection>scm:git:git@github.com/thurstonsand/scala-cass.git</connection>
    </scm>
      <developers>
        <developer>
          <id>thurstonsand</id>
          <name>Thurston Sandberg</name>
          <url>https://github.com/thurstonsand</url>
        </developer>
      </developers>,
  publishMavenStyle := true,
  pomIncludeRepository := (_ => false),
  bintrayReleaseOnPublish in ThisBuild := false,
  bintrayPackageLabels := Seq("cassandra")
)

lazy val `scala-cass` = project
  .in(file("."))
  .settings(
    moduleName := "scala-cass",
    sourceGenerators in Compile += (sourceManaged in Compile).map(Boilerplate.gen).taskValue
  )
  .settings(commonSettings: _*)
  .settings(applicationSettings: _*)
  .settings(publishSettings: _*)
  .settings(addUnmanagedSourceDirsFrom(if (cassandraVersion startsWith "2.1.") "scala_cass21" else "scala_cass3"))

addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias("fix", "all compile:scalafix test:scalafix")
