ThisBuild / version := "0.1.0"

ThisBuild / scalaVersion := "2.13.9"

ThisBuild / scapegoatVersion := "2.0.0"

ThisBuild / scapegoatReports := Seq("xml")

Scapegoat / scalacOptions += "-P:scapegoat:overrideLevels:all=Warning"

val akkaVersion = "2.7.0"
val akkaHttpVersion = "10.4.0"
val cassandraVersion = "1.1.0"
val logbackVersion = "1.3.4"
val circeVersion = "0.14.3"

ThisBuild / libraryDependencies ++= Seq(
  // Akka actor
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  // Akka streams
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.lightbend.akka" %% "akka-stream-alpakka-csv" % "5.0.0",
  // Akka http
  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion,
  // Akka projections
  "com.lightbend.akka" %% "akka-projection-core" % "1.3.0",
  "com.lightbend.akka" %% "akka-projection-eventsourced" % "1.3.0",
  "com.lightbend.akka" %% "akka-projection-cassandra" % "1.3.0",
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
  "org.scalatest" %% "scalatest" % "3.2.14"
)

lazy val root = (project in file("."))
  .settings(
    name := "SteamReviews",
    idePackagePrefix := Some("dev.galre.josue.steamreviews")
  )
