# Apache Beam Load Test Suite

This repository contains a bunch of example scripts, a few processing pipelines (reading data from differernt sources and writing them to destinations) and a streaming data generator to execute load tests on a GCP project.  

## Requisites

This code relies on local installations for JDK 17, Maven, jq script, terraform and Thrift binaries, all of them available on $PATH. If using Mac OS all this can be grabbed by running the pertinent `brew` commands (see https://brew.sh/ on how to install it, run `brew install <dependency>` once that is done).

Also, there is a need to have a GCP project with Dataflow, GCS, PubSub, PubSubLite, BigTable and BigQuery enabled, the default service account having access to those resources and the existence of one service account or user account capable of running terraform on the project to create the needed infrastructure for the pipeline execution.

### Cloning this repository

Since this repository includes dependencies to other repositories, to properly run builds the clone action should include all the submodules: 
``` bash
git clone --recurse-submodules https://github.com/prodriguezdefino/apache-beam-streaming-tests.git 
```

## Repository Structure

There are several folders in the repository, pipeline code, scripts, infrastructure automation, in most cases the example scripts should be sufficient to have the pipelines up and running, but here is a detail of what to expect on each of them: 
 * `apache-beam-ptransforms` - this is a submodule folder including a set of useful Apache Beam PTransforms that are reused by the pipelines. Implementations for payload format handling, read and write from/to streaming sources and destinations, aggregations and configurations can be found here.
 * `infra` - this contains the terraform scripts used to automatically create and destroy the infrastructure needed by the test suite. In most cases the launching scripts will take care of executing the infrastructure automation before launching the pipelines, but in case of need for troubleshooting or the default values used to create the infrastructure, this is the place to go.
 * `dataflow-template-scripts` - contains a set of scripts that can be used to create templates for the different flavors of pipelines and also to launch them after creation has been completed.
 * `example-suite-scripts` - a bunch of scripts to launch the different flavors of pipelines for ingestion and aggregations from different sources and to their destinations and also the specific clean up scripts to terminate the pipelines and tear down the infrastructure.  
 * `types` - a set of example data type definitions, in different formats (JSON schema, Avro schema, Thrift IDLs), that can be used to generate and process data for the tests
 * `canonical-streaming-pipelines` - it contains 2 streaming pipelines that can be used to load test, one focused on ingesting data into BigQuery and the other to run configurable aggregations and store results in BigTable. Check on the folder's readme doc to understand available options for configuration of the pipelines. 
 * `streaming-data-generator` -  it contains a streaming data generator, which can be used to write a configurable rate of messages per second to a selected streaming infrastructure, the structure of the data can be also configured supporting JSON schema, Avro schema and Thrift formats as of now. 

## Quick Launch

To execute this suite with latest Beam release just do: 

```bash
sh build.sh && sh example-suite-scripts/execute-ps2bq-suite.sh <some-gcp-project> <some-run-name> 
```

This command execution take care of creating all the needed infrastructure in the specified GCP project, make sure the default service account do have access permissions to those resources and to execute as a Dataflow Worker in the project.  

After the command finishes 2 Dataflow pipelines will be created, one in charge of generating around 100MB/s load into PubSub using a small size payload for each event (500-2000KB) and another one in charge of grabbing the data from the PubSub subscription, process it, and write it on BigQuery. Using the script as a template can help to change the volume, input format and ingestion configuration for more specific tests.

## Available Configurations

### Apache Beam version 

By default the suite uses the configured Apache Beam version present in the [`pom.xml`](https://github.com/prodriguezdefino/dataflow-streaming-generator/blob/main/pom.xml#L17) files (2.46.0 or newer). This can be overriden by setting the proper value in the `BEAM_VERSION` variable defined in the [`execute-ps2bq-suite.sh`](example-suite-scripts/execute-ps2bq-suite-example.sh)  script. Most Apache Beam versions are available in Maven Central, including some of the latest SNAPSHOT versions, but this configuration may come handy when building Apache Beam natively and wanting to test things at scale. 

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

By default the suite ingest all the data in 1 single table. We can change that by modifying the [`execute-ps2bq-suite-example`](https://github.com/prodriguezdefino/dataflow-streaming-generator/blob/main/example-suite-scripts/execute-ps2bq-suite.sh#L66) script for example like this: 

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

The amount of data and payload type that we want to send to the pipeline for BQ ingestion can be configured by modifying the [`execute-ps2bq-suite-example`](https://github.com/prodriguezdefino/dataflow-streaming-generator/blob/main/example-suite-scripts/execute-ps2bq-suite-example.sh#L43) script for example:

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

To use avro schemas as the input for generation the following configuration on the example [script](https://github.com/prodriguezdefino/dataflow-streaming-generator/blob/main/example-suite-scripts/execute-ps2bq-suite-example.sh) should be sufficient: 

``` bash
echo "starting data generator"
pushd streaming-data-generator

source ./execute-ps2bq.sh $1 $2 $3 "\
  --schemaFileLocation=classpath://complex-event.avro \    # <-- this configures the file being read from classpath, other locations are supported (gs, filesystem)
  --format=AVRO \                                          # <-- selects the type of avro generation
  --generatorRatePerSec=1000 \
  --maxRecordsPerBatch=4500 \
  --compressionEnabled=false \
  --completeObjects=true "$MORE_PARAMS

popd

echo "starting processing pipeline"
pushd canonical-streaming-pipelines

source ./execute-ps2bq.sh $1 $2-sub $3 "\
 --schemaFileLocation=classpath://complex-event.avro \     # <-- schema location, similar to the generator 
 --useStorageApiConnectionPool=true \
 --tableDestinationCount=1 "$MORE_PARAMS
``` 

For schema files, most of the known file location protocols are supported (local filesystem, classpath, GCS should work fine).

## Streaming Infrastructure
This suite can run the same load tests sending the generated data to PubSub, PubSubLite or Kafka. Since there are considerable differences between the mentioned streaming infrastructure components, is important from Apache Beam perspective and even Dataflow perspective how the execution scales on each case. 

Included there are startup scripts for each of those available streaming infrastructure components, needing no changes in the code, and the infrastructure automation will take care of creating the needed resources to run the workloads. 

* PubSub suite     - [`execute-ps2bq-suite.sh`](example-suite-scripts/execute-ps2bq-suite.sh)
* PubSubLite suite - [`execute-pslite2bq-suite.sh`](example-suite-scripts/execute-pslite2bq-suite.sh)
* Kafka suite      - [`execute-kafka2bq-suite.sh`](example-suite-scripts/execute-kafka2bq-suite.sh)

In the case of the Kafka suite, GCE instances will be used to host the Kafka brokers and the Zookeeper nodes, review the default setttings in the terraform templates to change the needed partitions and retentions when moving more data than the one set as default in the example scripts.