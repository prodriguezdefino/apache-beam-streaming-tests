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

import com.google.cloud.pso.beam.common.transport.EventTransport;
import com.google.cloud.pso.beam.pipelines.options.BigQueryWriteOptions;
import org.apache.beam.sdk.transforms.PTransform;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.PDone;

/**
 * Given the existing configuration for write formats, transform the <EventTransport> and writes it
 * into BigQuery.
 */
public class StoreInBigQuery extends PTransform<PCollection<EventTransport>, PDone> {

  StoreInBigQuery() {
  }

  public static StoreInBigQuery store() {
    return new StoreInBigQuery();
  }

  @Override
  public PDone expand(PCollection<EventTransport> input) {
    if (input.getPipeline().getOptions().as(BigQueryWriteOptions.class).isUsingAvroToStore()) {
      return input
              .apply("PrepDataAsGenericRecord",
                      TransformTransportToFormat.transformToGenericRecords())
              .apply("WriteIntoBigQuery",
                      WriteFormatToBigQuery.writeGenericRecords());
    }
    if (input.getPipeline().getOptions().as(BigQueryWriteOptions.class).isUsingTableRowToStore()) {
      return input
              .apply("PrepDataAsTableRow",
                      TransformTransportToFormat.transformToTableRows())
              .apply("WriteIntoBigQuery",
                      WriteFormatToBigQuery.writeTableRows());
    }
    return input
            .apply("PrepDataAsRow", TransformTransportToFormat.transformToRows())
            .apply("WriteIntoBigQuery", WriteFormatToBigQuery.writeBeamRows());
  }

}
