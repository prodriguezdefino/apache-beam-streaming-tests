/*
 * Copyright (C) 2022 Google Inc.
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
import com.google.cloud.pso.beam.options.StreamingSourceOptions;
import com.google.cloud.pso.beam.pipelines.options.BigQueryWriteOptions;
import com.google.cloud.pso.beam.pipelines.transforms.PrepareBQIngestion;
import com.google.cloud.pso.beam.pipelines.transforms.WriteToBigQuery;
import com.google.cloud.pso.beam.transforms.ReadStreamingSource;
import com.google.cloud.pso.beam.udf.transforms.ExecuteUDF;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.options.Default;
import org.apache.beam.sdk.options.Description;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ingestion pipeline for BigQuery, reads data from a specified StreamingSource.
 */
public class StreamingSourceToBigQuery {

  private static final Logger LOG = LoggerFactory.getLogger(StreamingSourceToBigQuery.class);

  /**
   * Options for the streaming data generator
   */
  public interface StreamingSourceToBigQueryOptions
          extends StreamingSourceOptions, BigQueryWriteOptions {

    @Description("FQCN of the UDF that will execute")
    @Default.String("")
    String getUDFClassName();

    void setUDFClassName(String value);

  }

  /**
   * Sets up and starts generator pipeline.
   *
   * @param args
   * @throws
   */
  public static void main(String[] args) throws Exception {
    var options
            = PipelineOptionsFactory.fromArgs(args)
                    .withValidation()
                    .as(StreamingSourceToBigQueryOptions.class);

    // Run generator pipeline
    var pipeline = Pipeline.create(options);

    pipeline
            .apply("ReadFromStreamingSource", ReadStreamingSource.create())
            .apply("MaybeDecompress", MaybeDecompressEvents.create())
            .apply("MaybeExecuteUDF", ExecuteUDF.create(options.getUDFClassName()))
            .apply("PrepIngestion", PrepareBQIngestion.create())
            .apply("WriteIntoBigQuery", WriteToBigQuery.create());

    pipeline.run();
  }

}
