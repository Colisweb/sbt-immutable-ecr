import java.util.UUID

import com.amazonaws.regions.Regions

name := "sbt-immutable-ecr-test-0"

version := UUID.randomUUID().toString

scalaVersion  := "2.12.6"

enablePlugins(JavaAppPackaging, ImmutableEcrPlugin)

ImmutableEcr / region := Regions.EU_WEST_1
