FROM maven:3-eclipse-temurin-17-focal AS build

USER root
# Installing Thrift dependencies
RUN apt-get update && apt-get install -f thrift-compiler && thrift -version
# Setting up the build env
RUN mkdir /build
WORKDIR /build
# copy the parent pom
COPY ./pom.xml ./pom.xml
# fetch all dependencies ignoring child modules (hopefully this does not change super frequently, so build is faster)
RUN mvn dependency:go-offline -B -P!modules
# copy the rest of the project
COPY apache-beam-ptransforms/ /build/apache-beam-ptransforms
COPY types/ /build/types
COPY streaming-data-generator/ /build/streaming-data-generator
COPY canonical-streaming-pipelines/ /build/canonical-streaming-pipelines
# Building pipeline
RUN mvn clean package

#
# Package stage
#
FROM gcr.io/dataflow-templates-base/java17-template-launcher-base:latest

ARG WORKDIR=/template
ARG MAINCLASS
RUN mkdir -p ${WORKDIR}
WORKDIR ${WORKDIR}

# Bring the bundled jar file for the pipeline from the build container
COPY --from=build /build/canonical-streaming-pipelines/target/streaming-pipelines-bundled-0.0.1-SNAPSHOT.jar /template/
RUN mkdir -p ${WORKDIR}/lib

ENV FLEX_TEMPLATE_JAVA_CLASSPATH=/template/streaming-pipelines-bundled-0.0.1-SNAPSHOT.jar
ENV FLEX_TEMPLATE_JAVA_MAIN_CLASS=${MAINCLASS}
