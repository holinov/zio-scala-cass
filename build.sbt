val baseVersion       = "1.0.6"
val cassandra3Version = "3.7.1"
val cassandra2Version = "2.1.10.3"
val cassandraVersion =
  sys.props.getOrElse("cassandra-driver.version", cassandra3Version) match {
    case v @ (`cassandra3Version` | `cassandra2Version`) => v
    case _ =>
      throw new IllegalArgumentException(
        s"cassandra version must be one of $cassandra3Version, $cassandra2Version"
      )
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

testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
//addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1")
//scalafixDependencies in ThisBuild += "org.scala-lang.modules" %% "scala-collection-migrations" % "2.1.4"

lazy val commonSettings = Seq(
  scalaVersion := "2.13.2",
  crossScalaVersions := Seq("2.13.2", "2.12.10"),
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
      "-P:semanticdb:synthetics:on"
    ) ++ (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, 13)) => Seq("-Ymacro-annotations", "-Xlint")
      case Some((2, 12)) =>
        Seq(
          "-Xlint:adapted-args,nullary-unit,inaccessible,nullary-override,infer-any,missing-interpolator,doc-detached,private-shadow,type-parameter-shadow,poly-implicit-overload,option-implicit,delayedinit-select,by-name-right-associative,package-object-classes,unsound-match,stars-align",
          "-Ywarn-unused:privates,locals"
        )
      case _ =>
        throw new IllegalArgumentException(
          s"scala version not configured: ${scalaVersion.value}"
        )
    }),
  (scalacOptions in Test) -= "-Xfatal-warnings",
  parallelExecution in Test := false
)

//lazy val macroSettings = Seq(
//  libraryDependencies ++= (CrossVersion.partialVersion(scalaVersion.value) match {
//      case Some((2, 10)) =>
//        Seq(
//          "org.scalameta"          %% "scalameta"            % "4.3.18",
//          "org.scala-lang"         % "scala-reflect"         % scalaVersion.value,
//          "org.scala-lang"         % "scala-compiler"        % scalaVersion.value % "provided",
//          "com.datastax.cassandra" % "cassandra-driver-core" % cassandraVersion classifier "shaded",
//          "org.scalamacros"        %% "quasiquotes"          % "2.1.1" cross CrossVersion.binary
//        )
//      case _ => Seq.empty
//    })
//)

lazy val applicationSettings = Seq(
  name := "zio-scala-cass",
  moduleName := "zio-scala-cass",
  organization := "com.media.gr",
  description := "a wrapper for the Java Cassandra driver that uses case classes to simplify and codify creating cached statements in a type-safe manner",
  version := s"$baseVersion",
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
      "org.scala-lang.modules" %% "scala-collection-compat" % "2.1.6",
      "dev.zio"                %% "zio"                     % "1.0.0-RC20",
      "dev.zio"                %% "zio-streams"             % "1.0.0-RC20",
      "dev.zio"                %% "zio-test"                % "1.0.0-RC20" % "test",
      "dev.zio"                %% "zio-test-sbt"            % "1.0.0-RC20" % "test",
      "org.scalatest"          %% "scalatest"               % "3.2.0" % "test"
    ) ++ (if (cassandraVersion startsWith "2.1.")
            Seq("org.cassandraunit" % "cassandra-unit" % "2.2.2.1" % "test")
          else
            Seq(
              "com.datastax.cassandra" % "cassandra-driver-extras" % cassandraVersion excludeAll (ExclusionRule(
                "com.datastax.cassandra",
                "cassandra-driver-core"
              ), ExclusionRule("com.google.guava", "guava")),
              "org.cassandraunit" % "cassandra-unit" % "3.3.0.2" % "test"
            ))
)

lazy val publishSettings = Seq(
  licenses := Seq(
      "MIT" -> url("http://www.opensource.org/licenses/mit-license.php")
    ),
  publishMavenStyle := true,
  credentials += Credentials(Path.userHome / ".sbt" / ".credentials"),
  publishTo := Some(
      "Gr Nexus" at "http://nexus.infra.guru:8081/repository/maven_private/"
    ).map(_.withAllowInsecureProtocol(true))
)

lazy val `scala-cass` = project
  .in(file("."))
  .settings(
    sourceGenerators in Compile += (sourceManaged in Compile)
        .map(Boilerplate.gen)
        .taskValue
  )
  .settings(commonSettings: _*)
  .settings(applicationSettings: _*)
  .settings(publishSettings: _*)
  //  .settings(macroSettings: _*)
  .settings(
    addUnmanagedSourceDirsFrom(
      if (cassandraVersion startsWith "2.1.") "scala_cass21"
      else "scala_cass3"
    )
  )

addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias("fix", "all compile:scalafix test:scalafix")
