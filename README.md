# sbt-immutable-ecr

An [SBT](http://www.scala-sbt.org/) plugin for managing [Docker](http://docker.io) images within [Amazon ECR](https://aws.amazon.com/ecr/) in an immutable way.

[ ![Download](https://api.bintray.com/packages/colisweb/sbt-plugins/sbt-immutable-ecr/images/download.svg) ](https://bintray.com/colisweb/sbt-plugins/sbt-immutable-ecr/_latestVersion)
[![Build Status](https://travis-ci.org/Colisweb/sbt-immutable-ecr.svg?branch=master)](https://travis-ci.org/Colisweb/sbt-immutable-ecr)

This project is a fork of [sbt-ecr](https://github.com/sbilinski/sbt-ecr) aiming to enforce immutability in the Docker tags management.   
We want to thanks all the contrinutors of `sbt-ecr` for their work.

## Features

Enable the use of the [sbt-native-packager DockerPlugin](https://www.scala-sbt.org/sbt-native-packager/formats/docker.html) with [Amazon ECR](https://aws.amazon.com/ecr/) in an immutable way.

## Immutable ?

With this plugin you'll not be able to push two times the same `version` to your ECS registry.
Each `version` (or `tag`) in your registry is uniq and immutable.

### Why is this important ?

1. Because we want to be 100% confident when we push a new version of our app to the registry that we're not overriding an already published version by mistake.
2. Because when we rollback our production servers to a previous version, we want to be 100% confident that the version we're rollbacking to is the version we're rollbacking to.
3. Because immutability is awesome !

## Prerequisites

The plugin assumes that [sbt-native-packager](https://github.com/sbt/sbt-native-packager) has been included in your SBT build configuration.    
This can be done by adding the plugin following instructions at http://www.scala-sbt.org/sbt-native-packager/ or by adding
another plugin that includes and initializes it (e.g. the SBT plugin for Play 2.6.x).

## Installation

Add the following to your `project/plugins.sbt` file:

```scala
resolvers += Resolver.bintrayRepo("colisweb", "sbt-plugins")

addSbtPlugin("com.colisweb.sbt" % "sbt-immutable-ecr" % "0.5.0")
```

Add `sbt-immutable-ecr` settings to your `build.sbt`:   

```scala
import com.amazonaws.regions.Regions

enablePlugins(ImmutableEcrPlugin)

ImmutableEcr / accountId := "123456789912" // More info: https://docs.aws.amazon.com/IAM/latest/UserGuide/console_account-alias.html
ImmutableEcr / region := Regions.US_EAST_1
```

That's all ! :tada:

:warning: This plugins will set the `Docker / dockerRepository` value for you, so you **SHOULD NOT SET** it in your `build.sbt`.
:warning: Because of how ECR works, you **SHOULD NOT SET** `Docker / dockerUsername`.

Now you can use the normal workflow of the [sbt-native-packager DockerPlugin](https://www.scala-sbt.org/sbt-native-packager/formats/docker.html).
