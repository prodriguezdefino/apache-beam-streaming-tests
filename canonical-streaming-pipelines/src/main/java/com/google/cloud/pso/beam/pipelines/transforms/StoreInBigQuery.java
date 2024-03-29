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

import com.google.cloud.pso.beam.common.formats.options.TransportFormatOptions;
import com.google.cloud.pso.beam.common.formats.transforms.TransformTransportToFormat;
import com.google.cloud.pso.beam.common.transport.ErrorTransport;
import com.google.cloud.pso.beam.common.transport.EventTransport;
import com.google.cloud.pso.beam.pipelines.options.BigQueryWriteOptions;
import org.apache.beam.sdk.extensions.avro.coders.AvroGenericCoder;
import org.apache.beam.sdk.transforms.Flatten;
import org.apache.beam.sdk.transforms.PTransform;
import org.apache.beam.sdk.transforms.windowing.DefaultTrigger;
import org.apache.beam.sdk.transforms.windowing.GlobalWindows;
import org.apache.beam.sdk.transforms.windowing.Window;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.PCollectionList;
import org.apache.beam.sdk.values.PCollectionTuple;
import org.apache.beam.sdk.values.PDone;
import org.apache.beam.sdk.values.TupleTag;

/**
 * Given the existing configuration for write formats, transform the <EventTransport> and writes it
 * into BigQuery.
 */
public class StoreInBigQuery extends PTransform<PCollection<EventTransport>, PCollectionTuple> {

  public static final TupleTag<ErrorTransport> FAILED_EVENTS = new TupleTag<>() {};

  StoreInBigQuery() {}

  public static StoreInBigQuery store() {
    return new StoreInBigQuery();
  }

  public static StoreErrorsInBigQuery storeErrors() {
    return new StoreErrorsInBigQuery();
  }

  public static TupleTag<ErrorTransport> failedEvents() {
    return FAILED_EVENTS;
  }

  @Override
  public PCollectionTuple expand(PCollection<EventTransport> input) {
    var returnPCT = PCollectionTuple.empty(input.getPipeline());
    var options = input.getPipeline().getOptions().as(BigQueryWriteOptions.class);

    return switch (options.getFormatToStore()) {
      case BEAM_ROW -> {
        var maybeRows = input.apply("PrepDataAsRow", TransformTransportToFormat.transformToRows());
        maybeRows
            .get(TransformTransportToFormat.successfulRows())
            .setRowSchema(
                TransformTransportToFormat.retrieveRowSchema(
                    input.getPipeline().getOptions().as(TransportFormatOptions.class)))
            .apply("WriteIntoBigQuery", WriteFormatToBigQuery.writeBeamRows());
        yield returnPCT.and(FAILED_EVENTS, maybeRows.get(TransformTransportToFormat.FAILED_EVENTS));
      }
      case AVRO_GENERIC_RECORD -> {
        var maybeGenericRecord =
            input.apply(
                "PrepDataAsGenericRecord", TransformTransportToFormat.transformToGenericRecords());
        maybeGenericRecord
            .get(TransformTransportToFormat.successfulGenericRecords())
            .setCoder(
                AvroGenericCoder.of(
                    TransformTransportToFormat.retrieveAvroSchema(
                        input.getPipeline().getOptions().as(TransportFormatOptions.class))))
            .apply("WriteIntoBigQuery", WriteFormatToBigQuery.writeGenericRecords());
        yield returnPCT.and(
            FAILED_EVENTS, maybeGenericRecord.get(TransformTransportToFormat.FAILED_EVENTS));
      }
      case TABLE_ROW -> {
        var maybeTableRows =
            input.apply("PrepDataAsTableRow", TransformTransportToFormat.transformToTableRows());
        maybeTableRows
            .get(TransformTransportToFormat.successfulTableRows())
            .apply("WriteIntoBigQuery", WriteFormatToBigQuery.writeTableRows());
        yield returnPCT.and(
            FAILED_EVENTS, maybeTableRows.get(TransformTransportToFormat.FAILED_EVENTS));
      }
    };
  }

  public static class StoreErrorsInBigQuery
      extends PTransform<PCollectionList<ErrorTransport>, PDone> {

    @Override
    public PDone expand(PCollectionList<ErrorTransport> input) {

      var errorsToStore =
          PCollectionList.of(
              input.getAll().stream()
                  // make sure all the input PCollections discard their window information
                  .map(
                      pc ->
                          pc.apply(
                              "rewindowIntoGlobal",
                              Window.<ErrorTransport>into(new GlobalWindows())
                                  .triggering(DefaultTrigger.of())))
                  .toList());

      errorsToStore
          .apply("FlattenErrors", Flatten.pCollections())
          .apply("TransformToRows", TransformTransportToFormat.transformErrorsToRows())
          .apply("WriteErrorsToBigQuery", WriteFormatToBigQuery.writeErrorsAsBeamRows());
      return PDone.in(input.getPipeline());
    }
  }
}
