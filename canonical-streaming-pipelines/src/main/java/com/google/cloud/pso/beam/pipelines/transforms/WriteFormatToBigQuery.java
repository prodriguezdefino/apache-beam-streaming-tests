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
package com.google.cloud.pso.beam.pipelines.transforms;

import com.google.api.services.bigquery.model.TableSchema;
import com.google.cloud.pso.beam.pipelines.options.BigQueryWriteOptions;
import com.google.cloud.pso.beam.pipelines.options.EventPayloadOptions;
import java.util.Random;
import org.apache.avro.generic.GenericRecord;
import org.apache.beam.sdk.io.gcp.bigquery.BigQueryHelpers;
import org.apache.beam.sdk.io.gcp.bigquery.BigQueryIO;
import org.apache.beam.sdk.io.gcp.bigquery.BigQueryUtils;
import org.apache.beam.sdk.io.gcp.bigquery.DynamicDestinations;
import org.apache.beam.sdk.io.gcp.bigquery.TableDestination;
import org.apache.beam.sdk.transforms.PTransform;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.PDone;
import org.apache.beam.sdk.values.Row;
import org.apache.beam.sdk.values.ValueInSingleWindow;

/**
 * Writes to BigQuery using StorageWrite API.
 *
 * @param <T> The type to write into BigQuery, currently GenericRow and Row are supported.
 */
public abstract class WriteFormatToBigQuery<T> extends PTransform<PCollection<T>, PDone> {

  WriteFormatToBigQuery() {
  }

  public static WriteFormatToBigQuery<GenericRecord> writeGenericRecords() {
    return new WriteGenericRows();
  }

  public static WriteFormatToBigQuery<Row> writeBeamRows() {
    return new WriteBeamRows();
  }

  protected abstract BigQueryIO.Write<T> createBigQueryWriter();

  @Override
  public PDone expand(PCollection<T> input) {
    var options = input.getPipeline().getOptions().as(BigQueryWriteOptions.class);

    var write
            = createBigQueryWriter()
                    .withMethod(options.getBigQueryWriteMethod())
                    .withoutValidation()
                    .withWriteDisposition(BigQueryIO.Write.WriteDisposition.WRITE_APPEND)
                    .withExtendedErrorInfo();

    if (options.getTableDestinationCount() > 1) {
      var tableSchemaString = BigQueryHelpers.toJsonString(retrieveTableSchema(options));
      var tableCount = options.getTableDestinationCount();
      var tableSpec = options.getOutputTable();
      write = write
              .to(new DynamicDestinations<T, Integer>() {
                private final Random rand = new Random();

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
        var tableSchema = retrieveTableSchema(options);
        write = write
                .withSchema(tableSchema)
                .withCreateDisposition(BigQueryIO.Write.CreateDisposition.CREATE_IF_NEEDED);

      } else {
        write = write.withCreateDisposition(BigQueryIO.Write.CreateDisposition.CREATE_NEVER);
      }
    }
    input.apply("WriteToBigQuery", write);
    return PDone.in(input.getPipeline());
  }

  public static class WriteBeamRows extends WriteFormatToBigQuery<Row> {

    WriteBeamRows() {
    }

    @Override
    protected BigQueryIO.Write<Row> createBigQueryWriter() {
      return BigQueryIO
              .<Row>write()
              .useBeamSchema();
    }
  }

  public static class WriteGenericRows extends WriteFormatToBigQuery<GenericRecord> {

    WriteGenericRows() {
    }

    @Override
    protected BigQueryIO.Write<GenericRecord> createBigQueryWriter() {
      return BigQueryIO
              .writeGenericRecords();
    }
  }

  private static TableSchema retrieveTableSchema(EventPayloadOptions options) {
    return BigQueryUtils.toTableSchema(
            TransformTransportToFormat.retrieveRowSchema(options));
  }

}
