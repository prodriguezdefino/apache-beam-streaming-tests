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
import com.google.cloud.pso.beam.pipelines.options.EventPayloadOptions;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.thrift.ThriftData;
import org.apache.avro.thrift.ThriftDatumWriter;
import org.apache.beam.sdk.schemas.Schema;
import org.apache.beam.sdk.schemas.utils.AvroUtils;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.PTransform;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.Row;
import org.apache.thrift.TBase;
import org.apache.thrift.TDeserializer;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
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
            .setRowSchema(retrieveRowSchema(
                    input.getPipeline().getOptions().as(EventPayloadOptions.class)));
  }

  static class TransformTransportToRow extends DoFn<EventTransport, Row> {

    private Schema beamSchema;
    private org.apache.avro.Schema avroSchema;
    private Class<? extends TBase<?, ?>> thriftClass;
    private final String className;

    public TransformTransportToRow(String className) {
      this.className = className;
    }

    @Setup
    public void setup() throws Exception {
      avroSchema = retrieveAvroSchema(className);
      beamSchema = AvroUtils.toBeamSchema(avroSchema);
      thriftClass = retrieveThriftClass(className);
    }

    @StartBundle
    public void startBundle() {
    }

    @ProcessElement
    public void processElement(ProcessContext context) throws Exception {
      context.output(
              retrieveRowFromTransport(context.element(), thriftClass, beamSchema, avroSchema));
    }

    static Row retrieveRowFromTransport(
            EventTransport transport,
            Class<? extends TBase<?, ?>> thriftClass,
            Schema beamSchema,
            org.apache.avro.Schema avroSchema) {

      return AvroUtils.toBeamRowStrict(
              retrieveGenericRecordFromTransport(transport, thriftClass, avroSchema),
              beamSchema);
    }

    static TBase<?, ?> getThriftObjectFromData(TBase<?, ?> emptyInstance, byte[] data) {
      try {
        TDeserializer deserializer = null;
        try {
          deserializer = new TDeserializer(new TBinaryProtocol.Factory());
        } catch (Exception e) {
          throw new RuntimeException("Error while creating a TDeserializer.", e);
        }
        deserializer.deserialize(emptyInstance, data);
        return emptyInstance;
      } catch (TException ex) {
        throw new RuntimeException("Can't serialize instance.", ex);
      }
    }

    static GenericRecord retrieveGenericRecordFromTransport(
            EventTransport transport,
            Class<? extends TBase<?, ?>> thriftClass,
            org.apache.avro.Schema avroSchema) {
      try {
        var thriftEmptyInstance = thriftClass.getConstructor().newInstance();
        var thriftObject = getThriftObjectFromData(thriftEmptyInstance, transport.getData());
        return retrieveGenericRecordFromThriftData(thriftObject, avroSchema);
      } catch (Exception ex) {
        var msg = "Error while trying to retrieve a generic record from the transport object.";
        LOG.error(msg, ex);
        throw new RuntimeException(msg, ex);
      }
    }

    static GenericRecord retrieveGenericRecordFromThriftData(
            TBase<?, ?> thriftObject, org.apache.avro.Schema avroSchema) {
      try {
        var bao = new ByteArrayOutputStream();
        var w = new ThriftDatumWriter(avroSchema);
        var e = EncoderFactory.get().binaryEncoder(bao, null);
        w.write(thriftObject, e);
        e.flush();
        return new GenericDatumReader<GenericRecord>(avroSchema).read(
                null,
                DecoderFactory.get().binaryDecoder(
                        new ByteArrayInputStream(bao.toByteArray()), null));
      } catch (IOException ex) {
        var msg = "Error while trying to retrieve a generic record from the thrift data.";
        LOG.error(msg, ex);
        throw new RuntimeException(msg, ex);
      }
    }

  }

  public static Schema retrieveRowSchema(EventPayloadOptions options) {
    switch (options.getEventFormat()) {
      case THRIFT: {
        var thriftClassName = options.getClassName();
        return retrieveRowSchema(thriftClassName);
      }
      default:
        throw new IllegalArgumentException(
                "Event format has not being implemented for ingestion: "
                + options.getEventFormat());
    }
  }

  public static Schema retrieveRowSchema(String thriftClassName) {
    try {
      var thriftRecord = retrieveThriftClass(thriftClassName);
      var avroSchema = ThriftData.get().getSchema(thriftRecord);
      return AvroUtils.toBeamSchema(avroSchema);
    } catch (ClassNotFoundException ex) {
      var msg = "Error while trying to create class instance of " + thriftClassName;
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
