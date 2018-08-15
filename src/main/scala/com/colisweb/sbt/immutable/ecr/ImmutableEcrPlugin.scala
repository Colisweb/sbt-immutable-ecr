package com.colisweb.sbt.immutable.ecr

import com.amazonaws.regions.{Region, Regions}
import com.typesafe.sbt.packager.docker.DockerPlugin
import sbt.Keys._
import sbt.internal.util.ManagedLogger
import sbt.{Def, _}

import scala.sys.process._

object ImmutableEcrPlugin extends AutoPlugin {

  object autoImport {
    lazy val ImmutableEcr = config("immutableecr")

    lazy val region = settingKey[Regions]("Amazon EC2 region.")
    lazy val accountId =
      settingKey[String]("AWS Account ID. https://docs.aws.amazon.com/IAM/latest/UserGuide/console_account-alias.html")
  }
  import autoImport._

  override def requires: Plugins = DockerPlugin

  import DockerPlugin.autoImport._

  override lazy val projectSettings: Seq[Def.Setting[_]] =
    inConfig(Docker) {
      Seq(
        dockerRepository := {
          val regionV = getRegion.value
          val id      = (ImmutableEcr / accountId).value

          Some(s"$id.dkr.ecr.$regionV.${regionV.getDomain}")
        },
        publish := ((Docker / publish) dependsOn (createRepository, taskAlreadyCreated, login)).value
      )
    }

  private lazy val taskAlreadyCreated: Def.Initialize[Task[Unit]] = Def.task {
    implicit val logger: ManagedLogger = streams.value.log

    val tagToPush = (Docker / dockerAlias).value.tag
    val alreadyPushedTags =
      AwsEcr.alreadyExistingTags(getRegion.value, (ImmutableEcr / accountId).value, (Docker / name).value)

    if (tagToPush.nonEmpty && alreadyPushedTags.contains(tagToPush.get)) {
      sys.error("ImmutableEcr: the tag you're trying to push already exists")
    }
  }

  private lazy val createRepository: Def.Initialize[Task[Unit]] = Def.task {
    implicit val logger: ManagedLogger = streams.value.log
    AwsEcr.createRepository(getRegion.value, (Docker / name).value)
  }

  private lazy val login: Def.Initialize[Task[Unit]] = Def.task {
    implicit val logger: ManagedLogger = streams.value.log

    val (user, pass) = AwsEcr.dockerCredentials(getRegion.value)
    val cmd          = s"docker login -u $user -p $pass https://${(Docker / dockerRepository).value.get}"

    cmd ! logger match {
      case 0 => ()
      case _ => sys.error(s"AWS ECR login failed. Command: $cmd")
    }
  }

  private lazy val getRegion: Def.Initialize[Region] = Def.setting { Region.getRegion((ImmutableEcr / region).value) }

}
