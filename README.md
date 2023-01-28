# Dataflow Load Test Suite

This repository contains a bunch of example scripts, a simple BQ ingestion pipeline (with multiple potential sources) and a streaming data generator to execute load tests on GCP.  

## Requisites

This code relies on local installations for JDK 11, Maven and Thrift binary, all of them available on $PATH. If using osx all this can be grabbed by running the pertinent `brew` commands (see https://brew.sh/ on how to install it, run `brew install thrift` once that is done).

A GCP project with Dataflow, GCS, PubSub and BigQuery enabled, the default service account having access to those resources and the existence of: 
* 1 PubSub topic and 1 PubSub subscription, the subscription name should be `<topic-name>-sub` 
* 1 BigQuery dataset with the same name as the PubSub topic
* 1 Bucket to stage the needed binaries for pipeline execution

## Quick Launch

To execute this suite with latest Beam release just do: 

```bash
sh build.sh && sh execute-ps2bq-suite-example.sh <some-gcp-project> <some-pubsub-topic-name> <some-bucket-name>
```

This command execution assumes all the named resources (PubSub topic and GCS bucket) are in the specified GCP project and that the default service account do have access permissions to those resources and to execute as a Dataflow Worker in the project. All this stuff can be easily changed by modifiying the `execute-*.sh` scripts included in the pertinent folders. 

After the command finishes 2 Dataflow pipelines will be created, one in charge of generating around 200MB/s load into PubSub using a small size payload for each event (500-2000KB) and another one in charge of grabbing the data from the PubSub subscription, process it, and write it on BigQuery. 

## Available Configurations

### Apache Beam version 

By default the suite uses the configured Apache Beam version present in the [`pom.xml`](https://github.com/prodriguezdefino/dataflow-streaming-generator/blob/main/pom.xml#L17) files (2.44.0 or newer). This can be overrided by setting the proper value in the `BEAM_VERSION` variable defined in the [`execute-ps2bq-suite-example.sh`](execute-ps2bq-suite-example.sh)  script. Most Apache Beam versions are available in Maven Central, including some of the latest SNAPSHOT versions, but this configuration may come handy when building Apache Beam natively and wanting to test things at scale. 

### Other Libraries versions 

If we need to test an specific version of a library in use by the ingestion pipeline, lets say a new version of the BigQuery StorageWrite client library, by adding the specific dependency in the pipeline's [pom](https://github.com/prodriguezdefino/dataflow-streaming-generator/blob/main/canonical-streaming-pipelines/pom.xml) will be sufficient. For example, adding: 

```xml
...
    
    <dependency>
        <groupId>com.google.cloud</groupId>
        <artifactId>google-cloud-bigquerystorage</artifactId>
        <version>2.28.3-SNAPSHOT</version>
    </dependency>
...
```

will force the ingestion pipeline to use that version of the library instead of the default one specified by the Apache Beam BOM dependencies. 

### BigQuery Table Destination count

By default the suite ingest all the data in 1 single table. We can change that by modifying the [`execute-ps2bq-suite-example`](https://github.com/prodriguezdefino/dataflow-streaming-generator/blob/main/execute-ps2bq-suite-example.sh#L38) script for example like this: 

```bash
...
 --useStorageApiConnectionPool=true \
 --bigQueryWriteMethod=STORAGE_API_AT_LEAST_ONCE \
 --tableDestinationCount=1 "          # <- this can be used to increase the number of destination tables
...
```

Note: the created tables will all exists in the same BigQuery dataset.

As seen in the snipet, the usage of BigQuery StorageWrite client pool is enabled by default, that can also be changed if needed using `false` to turn it off.

### Data Volume configuration

The amount of data and payload type that we want to send to the pipeline for BQ ingestion can be configured by modifying the [`execute-ps2bq-suite-example`](https://github.com/prodriguezdefino/dataflow-streaming-generator/blob/main/execute-ps2bq-suite-example.sh#L25) script for example:

```bash
source ./execute-ps2bq.sh $1 $2 $3 "\
  --className=com.google.cloud.pso.beam.generator.thrift.CompoundEvent \     # The payload to be generated with random data
  --generatorRatePerSec=250000 \                                             # The quantity of elements per second to be generated
  --maxRecordsPerBatch=4500 \                                                # The batch element count to be used for PubSub messages
  --compressionEnabled=true \                                                # The compression setting
  --completeObjects=true "$MORE_PARAMS                                       # If the payload objects have all the fields set 
```

The example configuration will yield ~200MB/s on batches of 2.0MB each. Why use compressions? It's advantageos for customers that can tradeoff a little bit of latency and gain discounts in PubSub and Dataflow costs. This can be totally turned off, but is on because the number of customer using similar approaches started to increase.

### Example schemas and PubSub payload format

By default the example script runs a thrift based workload, forcing the ingestion pipeline to go through a Thrift -> AVRO -> Row -> Proto format transformation chain which could be CPU intensive depending on the size of the thrift object. Also, there is a possibility to use AVRO as the payload format, which proves to be the faster to be generated (potentially reaching larger event per second count). Both format type examples can be found in the `/types` folder in the project. 

To use a specific generated Thrift class to test both the generator and the ingestion pipeline needs to be configured like:

```bash
...
source ./execute-ps2bq.sh $1 $2 $3 "\
  --className=com.google.cloud.pso.beam.generator.thrift.SimpleEvent \     # <-- changes the thrift type used to generate data
...
```

Similarly, to use avro as part of the tests the data generator can use data from an already generated Avro file or a schema file, in the first case it will cache the entries of the file in memory and randomly send them to the configured streaming infrastructure (making the data generation super fast) and in the case of using the schema file the generator pipeline will use the schema definition to generate random data (useful for quick tests). 

To use avro schemas as the input for generation the following configuration on the example [script](https://github.com/prodriguezdefino/dataflow-streaming-generator/blob/main/execute-ps2bq-suite-example.sh#L20) should be sufficient: 

``` bash
echo "starting data generator"
pushd streaming-data-generator

source ./execute-ps2bq.sh $1 $2 $3 "\
  --filePath=classpath://complex-event.avro \    # <-- this configures the file being read from classpath, other locations are supported 
  --format=AVRO_FROM_SCHEMA \                    # <-- selects the type of avro generation, AVRO_FROM_FILE would work here as well
  --generatorRatePerSec=1000 \
  --maxRecordsPerBatch=4500 \
  --compressionEnabled=false \
  --completeObjects=true "$MORE_PARAMS

popd

echo "starting processing pipeline"
pushd canonical-streaming-pipelines

source ./execute-ps2bq.sh $1 $2-sub $3 "\
 --avroSchemaLocation=classpath://complex-event.avro \ # <-- schema location, similar to the generator 
 --useStorageApiConnectionPool=true \
 --tableDestinationCount=1 "$MORE_PARAMS
``` 

For schema files, most of the known file location protocols are supported (local filesystem, classpath, GCS should work fine).

## Streaming Infrastructure
This suite can run the same load tests sending the generated data to PubSub, PubSubLite or Kafka. Since there are considerable differences between the mentioned streaming infrastructure components, is important from Apache Beam perspective and even Dataflow perspective how the execution scales on each case. 

Included there are startup scripts for each of those available streaming infrastructure components, needing no changes in the code, only making sure the infrastructure is created in the project where the tests are going to run. 

* PubSub suite     - [`execute-ps2bq-suite-example.sh`](execute-ps2bq-suite-example.sh)
* PubSubLite suite - [`execute-pslite2bq-suite-example.sh`](execute-pslite2bq-suite-example.sh)
* Kafka suite      - [`execute-kafka2bq-suite-example.sh`](execute-pskafka2bq-suite-example.sh)

## Upcoming Improvements

Soon, infrastructure generation should also be available, generated using Terraform. The test suite script should be capable of creating, given a GCP project and a service account key with enough permissions to execute resource creation, the needed resources (enable GCP services, Service Account for Dataflow run, BigQuery dataset, PubSub and PubSubLite topics and subscription and a simple Kafka cluster). Check on the [infra](https://github.com/prodriguezdefino/dataflow-streaming-generator/blob/main/infra) folder for progress.
