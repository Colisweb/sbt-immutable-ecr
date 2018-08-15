package com.colisweb.sbt.immutable.ecr

import java.util
import java.util.Base64

import com.amazonaws.auth._
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.regions.{Region, Regions}
import com.amazonaws.services.ecr.model._
import com.amazonaws.services.ecr.{AmazonECR, AmazonECRClientBuilder}
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder
import com.amazonaws.services.securitytoken.model.GetCallerIdentityRequest
import com.typesafe.sbt.packager.docker.DockerPlugin
import sbt.Def.Initialize
import sbt.Keys._
import sbt.{Def, _}

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.sys.process._

object ImmutableEcrPlugin extends AutoPlugin {

  object autoImport {
    lazy val ImmutableEcr = config("immutableecr")

    lazy val region = settingKey[Regions]("Amazon EC2 region.")
  }
  import autoImport._

  private object Aws {

    lazy val credentialsProvider: Def.Initialize[AWSCredentialsProviderChain] =
      Def.setting {
        new AWSCredentialsProviderChain(
          new EnvironmentVariableCredentialsProvider(),
          new SystemPropertiesCredentialsProvider(),
          new ProfileCredentialsProvider(sys.env.getOrElse("AWS_DEFAULT_PROFILE", "default")),
          new EC2ContainerCredentialsProviderWrapper()
        )
      }
  }

  private object AwsSts {
    import Aws._

    lazy val accountId: Def.Initialize[String] =
      Def.setting {
        val request = new GetCallerIdentityRequest()

        AWSSecurityTokenServiceClientBuilder
          .standard()
          .withRegion((ImmutableEcr / region).value)
          .withCredentials(credentialsProvider.value)
          .build()
          .getCallerIdentity(request)
          .getAccount
      }
  }

  private object AwsEcr {

    import Aws._
    import AwsSts._

    lazy val createRepository: Initialize[Task[Unit]] =
      Def.task {
        val logger = streams.value.log

        val region = getRegion.value
        val repo   = repositoryName.value

        try {
          val request  = new CreateRepositoryRequest().withRepositoryName(repo)
          val response = ecrClient.value.createRepository(request)
          logger.info(s"Repository created in $region: arn=${response.getRepository.getRepositoryArn}")
        } catch {
          case _: RepositoryAlreadyExistsException => logger.info(s"Repository exists: $region/$repositoryName")
        }
      }

    lazy val dockerCredentials: Initialize[Task[(String, String)]] =
      Def.task {
        val request  = new GetAuthorizationTokenRequest()
        val response = ecrClient.value.getAuthorizationToken(request)

        response.getAuthorizationData.asScala
          .map(_.getAuthorizationToken)
          .map(Base64.getDecoder.decode(_))
          .map(new String(_, "UTF-8"))
          .map(_.split(":"))
          .headOption match {
          case Some(Array(user, pass)) => user -> pass
          case _ =>
            throw new IllegalStateException("Authorization token not found.")
        }
      }

    /**
      * Interesting documentations:
      *   - https://docs.aws.amazon.com/AmazonECR/latest/APIReference/API_ListImages.html
      */
    lazy val alreadyExistingTags: Initialize[Task[List[String]]] =
      Def.task {
        val logger = streams.value.log

        val registryId = accountId.value
        val repo       = repositoryName.value
        val client     = ecrClient.value

        /*
         * Should stay a `def` for the sake of immutability !
         */
        @inline
        def newRequest: ListImagesRequest =
          new ListImagesRequest()
            .withFilter(new ListImagesFilter().withTagStatus(TagStatus.TAGGED))
            .withMaxResults(100)
            .withRegistryId(registryId)
            .withRepositoryName(repo)

        @inline
        def tags(response: ListImagesResult): util.List[ImageIdentifier] = response.getImageIds

        @inline
        def call(request: ListImagesRequest): ListImagesResult = client.listImages(request)

        @tailrec
        def recursiveCalls(acc: util.List[ImageIdentifier], nextToken: String): List[String] =
          if (nextToken == null) acc.asScala.map(_.getImageTag).sorted.reverse.toList
          else {
            val response = call(newRequest.withNextToken(nextToken))
            acc.addAll(tags(response))
            recursiveCalls(acc, response.getNextToken)
          }

        val firstResponse = call(newRequest)

        val allTags = recursiveCalls(tags(firstResponse), firstResponse.getNextToken)

        logger.info(
          s"""
             | Tags retrived:
             |
             |${allTags.grouped(10).map(_.mkString(", ")).mkString("\n")}
             |
             |""".stripMargin.trim
        )

        allTags
      }

    private lazy val ecrClient: Initialize[Task[AmazonECR]] =
      Def.task {
        val region = getRegion.value

        AmazonECRClientBuilder
          .standard()
          .withRegion(region.getName)
          .withCredentials(credentialsProvider.value)
          .build()
      }
  }

  import AwsEcr._
  import AwsSts._

  override def requires: Plugins = DockerPlugin

  import DockerPlugin.autoImport._

  override lazy val projectSettings: Seq[Def.Setting[_]] =
    inConfig(Docker) {
      Seq(
        dockerRepository := {
          val region = getRegion.value
          val id     = accountId.value

          Some(s"$id.dkr.ecr.$region.${region.getDomain}")
        },
        publish := ((Docker / publish) dependsOn (createRepository, taskAlreadyCreated, login)).value
      )
    }

  private lazy val taskAlreadyCreated: Initialize[Task[Unit]] = Def.task {
    val tagToPush         = (Docker / dockerAlias).value.tag
    val alreadyPushedTags = alreadyExistingTags.value

    if (tagToPush.nonEmpty && alreadyPushedTags.contains(tagToPush.get)) {
      sys.error("ImmutableEcr: the tag you're trying to push already exists")
    }
  }

  private lazy val login: Initialize[Task[Unit]] = Def.task {
    val logger = streams.value.log

    val (user, pass) = dockerCredentials.value
    val cmd          = s"docker login -u $user -p $pass https://${(Docker / dockerRepository).value.get}"

    cmd ! logger match {
      case 0 => ()
      case _ => sys.error(s"AWS ECR login failed. Command: $cmd")
    }
  }

  private lazy val getRegion: Initialize[Region] = Def.setting { Region.getRegion((ImmutableEcr / region).value) }
  private lazy val repositoryName                = Def.setting { (Docker / name).value }

}
