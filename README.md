# Dataflow Load Test Suite

This repository contains a bunch of example scripts, a simple BQ ingestion pipeline (with multiple potential sources) and a streaming data generator to execute load tests on GCP.  

## Requisites

This code relies on local installations for JDK 11, Maven and Thrift binary, all of them available on $PATH. If using osx all this can be grabbed by running the pertinent `brew` commands (see https://brew.sh/).

A GCP project with Dataflow, GCS, PubSub and BigQuery enabled, the default service account having access to those resources and the existence of: 
* 1 PubSub topic and 1 PubSub subscription, the subscription name should be `<topic-name>-sub` 
* 1 BigQuery dataset with the same name as the PubSub topic
* 1 Bucket to stage the needed binaries for pipeline execution

## Quick Launch

To execute this suite with latest Beam release just do: 
``` bash
sh build.sh && sh execute-ps2bq-suite-example.sh <some-gcp-project> <some-pubsub-topic-name> <some-bucket-name>
```
This command execution assumes all the named resources (PubSub topic and GCS bucket) are in the specified GCP project and that the default service account do have access permissions to those resources and to execute as a Dataflow Worker in the project. All this stuff can be easily changed by modifiying the `execute-*.sh` scripts included in the pertinent folders. 

After the command finishes 2 Dataflow pipelines will be created, one in charge of generating around 200MB/s load into PubSub using a small size payload for each event (500-2000KB) and another one in charge of grabbing the data from the PubSub subscription, process it, and write it on BigQuery. 

## Available Configurations

### Apache Beam version 

By default the suite uses the configured Apache Beam version present in the `pom.xml` files (2.44.0 or newer). This can be overrided by setting the proper value in the `BEAM_VERSION` variable defined in the `execute-ps2bq-suite-example.sh` script. Most Apache Beam versions are available in Maven Central, including some of the latest SNAPSHOT versions, but this configuration may come handy when building Apache Beam natively and wanting to test things at scale. 

### Other Libraries versions 

If we need to test an specific version of a library in use by the ingestion pipeline, lets say a new version of the BigQuery StorageWrite client library, by adding the specific dependency in the pipeline's pom (`canonical-streaming-pipeline/pom.xml`) will be sufficient. For example, adding: 


``` xml
...
    
    <dependency>
        <groupId>com.google.cloud</groupId>
        <artifactId>google-cloud-bigquerystorage</artifactId>
        <version>2.28.3-SNAPSHOT</version>
    </dependency>
...
```
will force the ingestion pipeline to use that version of the library instead of the default one specified by the Apache Beam BOM dependencies. 

## BigQuery Table Destination count

By default the suite ingest all the data in 1 single table. We can change that by modifying the `canonical-streaming-pipeline/execute-ps2bq.sh` script for example like this: 

``` bash
...
 --useStorageApiConnectionPool=true \
 --tableDestinationCount=1 "          # <- this can be used to increase the number of destination tables
...
```
the created tables will all exists in the same BigQuery dataset.

As seen in the snipet, the usage of BigQuery StorageWrite client pool is enabled by default, that can also be changed if needed.

## Data Volume configuration

The amount of data and payload type that we want to send to the pipeline for BQ ingestion can be configured by modifying the `execute-ps2bq-suite-example` script for example.

```bash
source ./execute-ps2bq.sh $1 $2 $3 "\
  --className=com.google.cloud.pso.beam.generator.thrift.CompoundEvent \     # The payload to be generated with random data
  --generatorRatePerSec=200000 \                                             # The quantity of elements per second to be generated
  --maxRecordsPerBatch=4500 \                                                # The batch element count to be used for PubSub messages
  --compressionEnabled=true \                                                # The compression setting
  --completeObjects=true "$MORE_PARAMS                                       # If the payload objects have all the fields set 
```

The example configuration will yield ~200MB/s on batches of 2.1MB each. Why use compressions? It's advantageos for customers that can tradeoff a little bit of latency and gain discounts in PubSub and Dataflow costs. This can be totally turned off, but is on because the number of customer using similar approaches started to increase.

To inspect the available payload schemas, check the `/types` folder. Different payload sizes generate different stress on the components (mostly Dataflow and BigQuery).
