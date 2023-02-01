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
import com.google.cloud.pso.beam.common.Utilities;
import com.google.cloud.pso.beam.pipelines.options.BigQueryWriteOptions;
import com.google.cloud.pso.beam.pipelines.options.EventPayloadOptions;
import java.util.Random;
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
 */
public class WriteToBigQuery extends PTransform<PCollection<Row>, PDone> {

  WriteToBigQuery() {
  }

  public static WriteToBigQuery create() {
    return new WriteToBigQuery();
  }

  @Override
  public PDone expand(PCollection<Row> input) {
    var options = input.getPipeline().getOptions().as(BigQueryWriteOptions.class);

    var write
            = BigQueryIO
                    .<Row>write()
                    .withMethod(options.getBigQueryWriteMethod())
                    .useBeamSchema()
                    .withoutValidation()
                    .withWriteDisposition(BigQueryIO.Write.WriteDisposition.WRITE_APPEND)
                    .withExtendedErrorInfo();

    if (options.getTableDestinationCount() > 1) {
      var tableSchemaString = retrieveTableSchema(options).toString();
      var tableCount = options.getTableDestinationCount();
      var tableSpec = options.getOutputTable();
      var shouldSkew = options.isDestinationTableLoadSkewed();
      write = write
              .to(new DynamicDestinations<Row, Integer>() {
                private final Random rand = new Random();

                @Override
                public Integer getDestination(ValueInSingleWindow element) {
                  if (shouldSkew) {
                    return Utilities.nextSkewedBoundedInteger(0, tableCount, 80, 0);
                  } else {
                    return rand.nextInt(tableCount);
                  }
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

  private static TableSchema retrieveTableSchema(EventPayloadOptions options) {
    return BigQueryUtils.toTableSchema(
            PrepareBQIngestion.retrieveRowSchema(options));
  }

}
