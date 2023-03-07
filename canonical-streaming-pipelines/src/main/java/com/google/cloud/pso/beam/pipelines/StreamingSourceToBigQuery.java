/*
 * Copyright (C) 2023 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.cloud.pso.beam.pipelines;

import com.google.cloud.pso.beam.common.compression.transforms.MaybeDecompressEvents;
import com.google.cloud.pso.beam.common.formats.options.TransportFormatOptions;
import com.google.cloud.pso.beam.options.StreamingSourceOptions;
import com.google.cloud.pso.beam.pipelines.options.BigQueryWriteOptions;
import com.google.cloud.pso.beam.pipelines.transforms.StoreInBigQuery;
import com.google.cloud.pso.beam.transforms.ReadStreamingSource;
import com.google.cloud.pso.beam.udf.transforms.ExecuteUDF;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.options.Default;
import org.apache.beam.sdk.options.Description;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.values.PCollectionList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Ingestion pipeline for BigQuery, reads data from a specified StreamingSource. */
public class StreamingSourceToBigQuery {

  private static final Logger LOG = LoggerFactory.getLogger(StreamingSourceToBigQuery.class);

  /** Options for the ingestion pipeline */
  public interface StreamingSourceToBigQueryOptions
      extends StreamingSourceOptions, TransportFormatOptions, BigQueryWriteOptions {

    @Description("FQCN of the UDF that will execute")
    @Default.String("")
    String getUDFClassName();

    void setUDFClassName(String value);
  }

  /**
   * Sets up and starts ingestion pipeline.
   *
   * @param args
   * @throws
   */
  public static void main(String[] args) throws Exception {
    var options =
        PipelineOptionsFactory.fromArgs(args)
            .withValidation()
            .as(StreamingSourceToBigQueryOptions.class);

    // Create the pipeline
    var pipeline = Pipeline.create(options);

    // read from the streaming sources and maybe decompress payloads
    var maybeDecompressed =
        pipeline
            .apply("ReadFromStreamingSource", ReadStreamingSource.create())
            .apply("MaybeDecompress", MaybeDecompressEvents.create());
    // possibly execute a configured UDF
    var maybeUDFExec =
        maybeDecompressed
            .get(MaybeDecompressEvents.SUCCESSFULLY_PROCESSED_EVENTS)
            .apply("MaybeExecuteUDF", ExecuteUDF.create(options.getUDFClassName()));
    // store the data into BigQuery
    var maybeStored =
        maybeUDFExec
            .get(ExecuteUDF.SUCCESSFULLY_PROCESSED_EVENTS)
            .apply("StoreInBigQuery", StoreInBigQuery.store());

    // process errors from the multiple previous stages
    PCollectionList.of(maybeDecompressed.get(MaybeDecompressEvents.FAILED_EVENTS))
        .and(maybeUDFExec.get(ExecuteUDF.FAILED_EVENTS))
        .and(maybeStored.get(StoreInBigQuery.failedEvents()))
        .apply("StoreErrorsInBigQuery", StoreInBigQuery.storeErrors());

    pipeline.run();
  }
}
