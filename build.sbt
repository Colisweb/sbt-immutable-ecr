organization := "com.colisweb.sbt"

name := "sbt-immutable-ecr"

scalaVersion := "2.12.6"

sbtPlugin := true

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.3.6" % "provided")

libraryDependencies ++= {
  val amazonSdkV = "1.11.387"
  Seq(
    "com.amazonaws" % "aws-java-sdk-sts" % amazonSdkV,
    "com.amazonaws" % "aws-java-sdk-ecr" % amazonSdkV,
    "org.scalatest" %% "scalatest"       % "3.0.5" % "test"
  )
}

scriptedLaunchOpts := scriptedLaunchOpts.value ++ Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
scriptedBufferLog := false

credentials += Credentials(Path.userHome / ".bintray" / ".credentials")
licenses := Seq("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0.html"))
homepage := Some(url("https://github.com/colisweb/sbt-immutable-ecr"))
bintrayOrganization := Some("colisweb")
bintrayReleaseOnPublish := true
publishMavenStyle := true
pomExtra := (
  <url>https://github.com/colisweb/sbt-immutable-ecr</url>
    <scm>
      <url>git@github.com:colisweb/sbt-immutable-ecr.git</url>
      <connection>scm:git:git@github.com:colisweb/sbt-immutable-ecr.git</connection>
    </scm>
    <developers>
      <developer>
        <id>guizmaii</id>
        <name>Jules Ivanic</name>
        <url>https://www.colisweb.com/</url>
      </developer>
    </developers>
)
