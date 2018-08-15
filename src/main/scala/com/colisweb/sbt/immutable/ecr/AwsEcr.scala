package com.colisweb.sbt.immutable.ecr

import java.util
import java.util.Base64

import com.amazonaws.regions.Region
import com.amazonaws.services.ecr.model._
import com.amazonaws.services.ecr.{AmazonECR, AmazonECRClientBuilder}
import sbt.Logger

import scala.annotation.tailrec
import scala.collection.JavaConverters._

private[ecr] object AwsEcr {

  import Aws._

  def createRepository(region: Region, repositoryName: String)(implicit logger: Logger): Unit =
    try {
      val request  = new CreateRepositoryRequest().withRepositoryName(repositoryName)
      val response = ecr(region).createRepository(request)
      logger.info(s"Repository created in $region: arn=${response.getRepository.getRepositoryArn}")
    } catch {
      case _: RepositoryAlreadyExistsException => logger.info(s"Repository exists: $region/$repositoryName")
    }

  def dockerCredentials(region: Region): (String, String) = {
    val request  = new GetAuthorizationTokenRequest()
    val response = ecr(region).getAuthorizationToken(request)

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
  def alreadyExistingTags(
      region: Region,
      registryId: String,
      repositoryName: String
  )(implicit logger: Logger): List[String] = {
    val client = ecr(region)

    /*
     * Should stay a `def` for the sake of immutability !
     */
    @inline
    def newRequest: ListImagesRequest =
      new ListImagesRequest()
        .withFilter(new ListImagesFilter().withTagStatus(TagStatus.TAGGED))
        .withMaxResults(100)
        .withRegistryId(registryId)
        .withRepositoryName(repositoryName)

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

  private def ecr(region: Region): AmazonECR =
    AmazonECRClientBuilder
      .standard()
      .withRegion(region.getName)
      .withCredentials(credentialsProvider())
      .build()

}
