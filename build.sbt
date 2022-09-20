ThisBuild / version := "0.1.0"

ThisBuild / scalaVersion := "2.13.9"

val akkaVersion = "2.6.20"
val akkaHttpVersion = "10.2.10"
val cassandraVersion = "1.0.6"

ThisBuild / libraryDependencies ++= Seq(
  // Akka actor
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  // Akka streams
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  // Akka http
  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion,
  // akka dependencies and testkit
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test,
  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
  "com.typesafe.akka" %% "akka-coordination" % akkaVersion,
  "com.typesafe.akka" %% "akka-remote" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-tools" % akkaVersion,
  "com.typesafe.akka" %% "akka-pki" % akkaVersion,
  // akka persistence
  "com.typesafe.akka" %% "akka-persistence" % akkaVersion,
  "com.typesafe.akka" %% "akka-persistence-query" % akkaVersion,
  // akka persistence cassandra
  "com.typesafe.akka" %% "akka-persistence-cassandra" % cassandraVersion,
  "com.typesafe.akka" %% "akka-persistence-cassandra-launcher" % cassandraVersion % Test,
  // dependencies
  "ch.qos.logback" % "logback-classic" % "1.4.0",
  "org.scalatest" %% "scalatest" % "3.2.13",
  "com.github.jwt-scala" %% "jwt-spray-json" % "9.0.2",
  "com.github.swagger-akka-http" %% "swagger-akka-http" % "2.8.0",
)

lazy val root = (project in file("."))
  .settings(
    name := "scala-steam",
    idePackagePrefix := Some("net.josuegalre.steam")
  )