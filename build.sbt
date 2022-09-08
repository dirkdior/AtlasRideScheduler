ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.8"

val AkkaVersion = "2.6.19"
val AlpakkaCsvVersion = "3.0.4"
val GeolatteGeomVersion = "1.7.0"

val ScalaTestVersion = "3.2.12"

lazy val root = (project in file("."))
  .settings(name := "AtlasRideScheduler")
  .settings(
    resolvers += ("jitpack" at "https://jitpack.io")
  )
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
      "com.lightbend.akka" %% "akka-stream-alpakka-csv" % AlpakkaCsvVersion,
      "org.geolatte" %% "geolatte-geom-scala" % GeolatteGeomVersion,
      "com.typesafe.akka" %% "akka-stream-testkit" % AkkaVersion % Test,
      "org.scalatest" %% "scalatest" % ScalaTestVersion % Test
    )
  )
