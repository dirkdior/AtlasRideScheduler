ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.8"

val AkkaVersion = "2.6.19"
val AlpakkaCsvVersion = "3.0.4"

lazy val root = (project in file("."))
  .settings(name := "AtlasRideScheduler")
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
      "com.lightbend.akka" %% "akka-stream-alpakka-csv" % AlpakkaCsvVersion,
      "com.typesafe.akka" %% "akka-stream-testkit" % AkkaVersion % Test
    )
  )
