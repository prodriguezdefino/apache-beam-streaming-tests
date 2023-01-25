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
import java.io.IOException;
import java.util.logging.Level;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.thrift.ThriftData;
import org.apache.beam.sdk.schemas.Schema;
import org.apache.beam.sdk.schemas.utils.AvroUtils;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.PTransform;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.Row;
import org.apache.thrift.TBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Transform in charge of obtaining a Beam Row ready to be ingested into BigQuery.
 */
public class PrepareBQIngestion extends PTransform<PCollection<EventTransport>, PCollection<Row>> {

  private static final Logger LOG = LoggerFactory.getLogger(PrepareBQIngestion.class);

  private final String className;

  PrepareBQIngestion(String className) {
    this.className = className;
  }

  public static PrepareBQIngestion create(String className) {
    return new PrepareBQIngestion(className);
  }

  @Override
  public PCollection<Row> expand(PCollection<EventTransport> input) {
    return input
            .apply("TransformToRow", ParDo.of(new TransformTransportToRow(className)))
            .setRowSchema(retrieveRowSchema(className));
  }

  static class TransformTransportToRow extends DoFn<EventTransport, Row> {

    private Schema beamSchema;
    private org.apache.avro.Schema avroSchema;
    private final String className;

    public TransformTransportToRow(String className) {
      this.className = className;
    }

    @Setup
    public void setup() throws Exception {
      avroSchema = retrieveAvroSchema(className);
      beamSchema = AvroUtils.toBeamSchema(avroSchema);
    }

    @StartBundle
    public void startBundle() {
    }

    @ProcessElement
    public void processElement(ProcessContext context) throws Exception {
      context.output(retrieveRowFromTransport(context.element(), beamSchema, avroSchema));
    }

    static Row retrieveRowFromTransport(
            EventTransport transport, Schema beamSchema, org.apache.avro.Schema avroSchema) {
      return AvroUtils.toBeamRowStrict(
              retrieveGenericRecordFromTransport(transport, avroSchema),
              beamSchema);
    }

    static GenericRecord retrieveGenericRecordFromTransport(
            EventTransport transport, org.apache.avro.Schema avroSchema) {
      return retrieveGenericRecordFromThriftData(transport.getData(), avroSchema);
    }

    static GenericRecord retrieveGenericRecordFromThriftData(
            byte[] thriftData, org.apache.avro.Schema avroSchema) {
      try {
        var reader = ThriftData.get().createDatumReader(avroSchema);
        var decoder = DecoderFactory.get().binaryDecoder(thriftData, null);
        return (GenericRecord) reader.read(null, decoder);
      } catch (IOException ex) {
        var msg = "Error while trying to retrieve a generic record from the thrift data.";
        LOG.error(msg, ex);
        throw new RuntimeException(msg, ex);
      }
    }

  }

  public static Schema retrieveRowSchema(String className) {
    try {
      var thriftRecord = retrieveThriftClass(className);
      var avroSchema = ThriftData.get().getSchema(thriftRecord);
      return AvroUtils.toBeamSchema(avroSchema);
    } catch (ClassNotFoundException ex) {
      var msg = "Error while trying to create class instance of " + className;
      LOG.error(msg, ex);
      throw new RuntimeException(msg, ex);
    }
  }

  public static org.apache.avro.Schema retrieveAvroSchema(String className)
          throws ClassNotFoundException {
    var thriftRecord = retrieveThriftClass(className);
    return ThriftData.get().getSchema(thriftRecord);
  }

  public static Class<? extends TBase<?, ?>> retrieveThriftClass(String className)
          throws ClassNotFoundException {
    return (Class<? extends TBase<?, ?>>) Class.forName(
            className, true, Thread.currentThread().getContextClassLoader());
  }

}
