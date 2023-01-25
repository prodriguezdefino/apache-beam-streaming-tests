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

import com.google.api.services.bigquery.model.TableSchema;
import com.google.cloud.pso.beam.common.compression.transforms.MaybeDecompressEvents;
import com.google.cloud.pso.beam.options.StreamingSourceOptions;
import com.google.cloud.pso.beam.pipelines.transforms.PrepareBQIngestion;
import com.google.cloud.pso.beam.transforms.ReadStreamingSource;
import com.google.cloud.pso.beam.udf.transforms.ExecuteUDF;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.io.gcp.bigquery.BigQueryHelpers;
import org.apache.beam.sdk.io.gcp.bigquery.BigQueryIO;
import org.apache.beam.sdk.io.gcp.bigquery.BigQueryUtils;
import org.apache.beam.sdk.io.gcp.bigquery.DynamicDestinations;
import org.apache.beam.sdk.io.gcp.bigquery.TableDestination;
import org.apache.beam.sdk.options.Default;
import org.apache.beam.sdk.options.Description;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.options.Validation;
import org.apache.beam.sdk.values.Row;
import org.apache.beam.sdk.values.ValueInSingleWindow;
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
          extends StreamingSourceOptions {

    @Description("FQCN of the type the pipeline will ingest")
    @Default.String("com.google.cloud.pso.beam.generator.thrift.CompoundEvent")
    String getClassName();

    void setClassName(String value);

    @Description("FQCN of the UDF that will execute")
    @Default.String("")
    String getUDFClassName();

    void setUDFClassName(String value);

    @Description("The BigQuery destination table. When using table destination count > 1 this will be used as the base table name.")
    @Validation.Required
    String getOutputTable();

    void setOutputTable(String value);

    @Description("The quantity of destination tables the data will be written to (Random distribution).")
    @Default.Integer(1)
    Integer getTableDestinationCount();

    void setTableDestinationCount(Integer value);

    @Description("Configures if the pipeline should create BQ tables.")
    @Default.Boolean(true)
    Boolean isCreateBQTable();

    void setCreateBQTable(Boolean value);

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
            .apply("PrepIngestion", PrepareBQIngestion.create(options.getClassName()))
            .apply("WriteIntoBigQuery", createBigQueryWriter(options));

    pipeline.run();
  }

  static BigQueryIO.Write<Row> createBigQueryWriter(
          StreamingSourceToBigQueryOptions options)
          throws ClassNotFoundException {

    var write
            = BigQueryIO
                    .<Row>write()
                    .withMethod(BigQueryIO.Write.Method.STORAGE_API_AT_LEAST_ONCE)
                    .useBeamSchema()
                    .withoutValidation()
                    .withWriteDisposition(BigQueryIO.Write.WriteDisposition.WRITE_APPEND)
                    .withExtendedErrorInfo();

    if (options.getTableDestinationCount() > 1) {
      var tableSchemaString = getTableSchemaBeforeLaunch(options).toString();
      var tableCount = options.getTableDestinationCount();
      var tableSpec = options.getOutputTable();
      write = write
              .to(new DynamicDestinations<Row, Integer>() {
                private Random rand = new Random();

                @Override
                public Integer getDestination(ValueInSingleWindow element) {
                  return rand.nextInt(tableCount);
                }

                @Override
                public TableDestination getTable(Integer destination) {
                  return new TableDestination(tableSpec + "-" + destination, null);
                }

                @Override
                public TableSchema getSchema(Integer destination) {
                  return BigQueryHelpers.fromJsonString(tableSchemaString, TableSchema.class);
                }
              });
      if (options.isCreateBQTable()) {
        write = write
                .withCreateDisposition(BigQueryIO.Write.CreateDisposition.CREATE_IF_NEEDED);
      } else {
        write = write.withCreateDisposition(BigQueryIO.Write.CreateDisposition.CREATE_NEVER);
      }
    } else {
      write = write.to(options.getOutputTable());
      if (options.isCreateBQTable()) {
        var tableSchema = getTableSchemaBeforeLaunch(options);
        write = write
                .withSchema(tableSchema)
                .withCreateDisposition(BigQueryIO.Write.CreateDisposition.CREATE_IF_NEEDED);

      } else {
        write = write.withCreateDisposition(BigQueryIO.Write.CreateDisposition.CREATE_NEVER);
      }
    }

    return write;
  }

  private static TableSchema getTableSchemaBeforeLaunch(StreamingSourceToBigQueryOptions options)
          throws ClassNotFoundException {
    return BigQueryUtils.toTableSchema(
            PrepareBQIngestion.retrieveRowSchema(options.getClassName()));
  }

}
