ThisBuild / version := "0.1.0"

ThisBuild / scalaVersion := "2.13.9"

val akkaVersion      = "2.6.20"
val akkaHttpVersion  = "10.2.10"
val cassandraVersion = "1.0.6"
val logbackVersion   = "1.3.0"
val circeVersion     = "0.14.3"

ThisBuild / libraryDependencies ++= Seq(
  // Akka actor
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  // Akka streams
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.lightbend.akka" %% "akka-stream-alpakka-csv" % "4.0.0",
  // Akka http
  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion,
  // json serializing
  "io.circe" %% "circe-core" % circeVersion,
  "io.circe" %% "circe-generic" % circeVersion,
  "io.circe" %% "circe-parser" % circeVersion,
  "de.heikoseeberger" %% "akka-http-circe" % "1.39.2",
  // akka dependencies and testkit
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test,
  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
  "com.typesafe.akka" %% "akka-coordination" % akkaVersion,
  "com.typesafe.akka" %% "akka-remote" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-tools" % akkaVersion,
  "com.typesafe.akka" %% "akka-pki" % akkaVersion,
  "com.typesafe.akka" %% "akka-serialization-jackson" % akkaVersion,
  // akka persistence
  "com.typesafe.akka" %% "akka-persistence" % akkaVersion,
  "com.typesafe.akka" %% "akka-persistence-query" % akkaVersion,
  // akka persistence cassandra
  "com.typesafe.akka" %% "akka-persistence-cassandra" % cassandraVersion,
  "com.typesafe.akka" %% "akka-persistence-cassandra-launcher" % cassandraVersion % Test,
  // dependencies
  "ch.qos.logback" % "logback-classic" % logbackVersion,
  "org.scalatest" %% "scalatest" % "3.2.13",
  "com.github.swagger-akka-http" %% "swagger-akka-http" % "2.8.0",
  // logback
  "ch.qos.logback" % "logback-classic" % "1.3.1"
)

lazy val root = (project in file("."))
  .settings(
    name := "scala-akka-project",
    idePackagePrefix := Some("dev.galre.josue.akkaProject")
  )