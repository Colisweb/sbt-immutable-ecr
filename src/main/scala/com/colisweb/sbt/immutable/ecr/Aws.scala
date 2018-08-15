package com.colisweb.sbt.immutable.ecr

import com.amazonaws.auth._
import com.amazonaws.auth.profile.ProfileCredentialsProvider

private[ecr] object Aws {

  def credentialsProvider: AWSCredentialsProvider =
    new AWSCredentialsProviderChain(
      new EnvironmentVariableCredentialsProvider(),
      new SystemPropertiesCredentialsProvider(),
      new ProfileCredentialsProvider(sys.env.getOrElse("AWS_DEFAULT_PROFILE", "default")),
      new EC2ContainerCredentialsProviderWrapper()
    )

}
