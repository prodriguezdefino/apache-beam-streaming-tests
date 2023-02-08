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

import com.google.cloud.pso.beam.common.transport.ErrorTransport;
import com.google.cloud.pso.beam.common.transport.EventTransport;
import com.google.cloud.pso.beam.pipelines.options.BigQueryWriteOptions;
import com.google.cloud.pso.beam.pipelines.options.EventPayloadOptions;
import org.apache.beam.sdk.coders.AvroGenericCoder;
import org.apache.beam.sdk.transforms.PTransform;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.PCollectionTuple;
import org.apache.beam.sdk.values.PDone;

/**
 * Given the existing configuration for write formats, transform the <EventTransport> and writes it
 * into BigQuery.
 */
public class StoreInBigQuery extends PTransform<PCollection<EventTransport>, PCollectionTuple> {

  public static final String ERROR_TAG_NAME = "errors";

  StoreInBigQuery() {
  }

  public static StoreInBigQuery store() {
    return new StoreInBigQuery();
  }

  public static StoreErrorsInBigQuery storeErrors() {
    return new StoreErrorsInBigQuery();
  }

  @Override
  public PCollectionTuple expand(PCollection<EventTransport> input) {
    var returnPCT = PCollectionTuple.empty(input.getPipeline());
    if (input.getPipeline().getOptions()
            .as(BigQueryWriteOptions.class)
            .isUsingAvroToStore()) {
      var maybeGenericRecord = input
              .apply("PrepDataAsGenericRecord",
                      TransformTransportToFormat.transformToGenericRecords());
      maybeGenericRecord
              .get(TransformTransportToFormat.successfulGenericRecords())
              .setCoder(
                      AvroGenericCoder.of(
                              TransformTransportToFormat.retrieveAvroSchema(
                                      input.getPipeline().getOptions().as(
                                              EventPayloadOptions.class))))
              .apply("WriteIntoBigQuery",
                      WriteFormatToBigQuery.writeGenericRecords());
      returnPCT.and(
              ERROR_TAG_NAME, maybeGenericRecord.get(TransformTransportToFormat.FAILED_EVENTS));
    } else if (input.getPipeline().getOptions()
            .as(BigQueryWriteOptions.class)
            .isUsingTableRowToStore()) {
      var maybeTableRows = input
              .apply("PrepDataAsTableRow",
                      TransformTransportToFormat.transformToTableRows());
      maybeTableRows.get(TransformTransportToFormat.successfulTableRows())
              .apply("WriteIntoBigQuery",
                      WriteFormatToBigQuery.writeTableRows());
      returnPCT.and(ERROR_TAG_NAME, maybeTableRows.get(TransformTransportToFormat.FAILED_EVENTS));
    } else {
      var maybeRows = input
              .apply("PrepDataAsRow", TransformTransportToFormat.transformToRows());
      maybeRows.get(TransformTransportToFormat.successfulRows())
              .setRowSchema(
                      TransformTransportToFormat.retrieveRowSchema(
                              input.getPipeline().getOptions().as(EventPayloadOptions.class)))
              .apply("WriteIntoBigQuery", WriteFormatToBigQuery.writeBeamRows());
      returnPCT.and(ERROR_TAG_NAME, maybeRows.get(TransformTransportToFormat.FAILED_EVENTS));
    }

    return returnPCT;
  }

  public static class StoreErrorsInBigQuery extends PTransform<PCollection<ErrorTransport>, PDone> {

    @Override
    public PDone expand(PCollection<ErrorTransport> input) {
      throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }
  }

}
