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

import com.google.api.services.bigquery.model.TableRow;
import com.google.cloud.pso.beam.common.transport.CommonErrorTransport;
import com.google.cloud.pso.beam.common.transport.ErrorTransport;
import com.google.cloud.pso.beam.common.transport.EventTransport;
import com.google.cloud.pso.beam.pipelines.options.EventPayloadOptions;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.thrift.ThriftData;
import org.apache.avro.thrift.ThriftDatumWriter;
import org.apache.beam.sdk.io.FileSystems;
import org.apache.beam.sdk.io.gcp.bigquery.BigQueryUtils;
import org.apache.beam.sdk.schemas.Schema;
import org.apache.beam.sdk.schemas.utils.AvroUtils;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.PTransform;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.PCollectionTuple;
import org.apache.beam.sdk.values.Row;
import org.apache.beam.sdk.values.TupleTag;
import org.apache.beam.sdk.values.TupleTagList;
import org.apache.thrift.TBase;
import org.apache.thrift.TDeserializer;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Transform in charge of obtaining a format <T> ready to be ingested into BigQuery.
 *
 * @param <T> A format compatible with BigQueryIO direct usage.
 */
public abstract class TransformTransportToFormat<T>
        extends PTransform<PCollection<EventTransport>, PCollectionTuple> {

  private static final Logger LOG = LoggerFactory.getLogger(TransformTransportToFormat.class);
  public static final TupleTag<ErrorTransport> FAILED_EVENTS = new TupleTag<>() {
  };

  TransformTransportToFormat() {
  }

  public static TransformToRows transformToRows() {
    return new TransformToRows();
  }

  public static TransformToGenericRecords transformToGenericRecords() {
    return new TransformToGenericRecords();
  }

  public static TransformToTableRows transformToTableRows() {
    return new TransformToTableRows();
  }

  public static TransformErrorTransportsToRow transformErrorsToRows() {
    return new TransformErrorTransportsToRow();
  }

  public static TupleTag<TableRow> successfulTableRows() {
    return TransformToTableRows.SUCCESSFULLY_PROCESSED_EVENTS;
  }

  public static TupleTag<Row> successfulRows() {
    return TransformToRows.SUCCESSFULLY_PROCESSED_EVENTS;
  }

  public static TupleTag<GenericRecord> successfulGenericRecords() {
    return TransformToGenericRecords.SUCCESSFULLY_PROCESSED_EVENTS;
  }

  static abstract class TransformTransport<T> extends DoFn<EventTransport, T> {

    protected Schema beamSchema;
    protected org.apache.avro.Schema avroSchema;
    protected Class<? extends TBase<?, ?>> thriftClass;
    protected final String className;
    protected final String avroSchemaLocation;
    protected final EventPayloadOptions.EventFormat eventFormat;

    public TransformTransport(
            String className, String avroSchemaLocation,
            EventPayloadOptions.EventFormat eventFormat) {
      this.className = className;
      this.avroSchemaLocation = avroSchemaLocation;
      this.eventFormat = eventFormat;
    }

    @Setup
    public void setup() throws Exception {
      switch (eventFormat) {
        case THRIFT: {
          thriftClass = retrieveThriftClass(className);
          avroSchema = retrieveAvroSchema(className);
          break;
        }
        case AVRO: {
          avroSchema = retrieveAvroSchemaFromLocation(avroSchemaLocation);
          break;
        }
      }
      setupSpecific();
    }

    protected void setupSpecific() throws Exception {
    }

    @ProcessElement
    public void processElement(ProcessContext context) throws Exception {
      try {
        processSpecific(context);
      } catch (Exception ex) {
        var msg = "Errors occurred while trying to transform the transport to the format.";
        LOG.error(msg, ex);
        context.output(FAILED_EVENTS, CommonErrorTransport.of(context.element(), msg, ex));
      }
    }

    protected abstract void processSpecific(ProcessContext context) throws Exception;
  }

  public static class TransformToRows extends TransformTransportToFormat<Row> {

    public static TupleTag<Row> SUCCESSFULLY_PROCESSED_EVENTS = new TupleTag<>() {
    };

    @Override
    public PCollectionTuple expand(PCollection<EventTransport> input) {
      var options = input.getPipeline().getOptions().as(EventPayloadOptions.class);
      return input
              .apply("TransformToRow",
                      ParDo.of(
                              new TransformTransportToRow(
                                      options.getClassName(),
                                      options.getAvroSchemaLocation(),
                                      options.getEventFormat()))
                              .withOutputTags(
                                      SUCCESSFULLY_PROCESSED_EVENTS,
                                      TupleTagList.of(FAILED_EVENTS)));

    }

    static class TransformTransportToRow extends TransformTransport<Row> {

      public TransformTransportToRow(
              String className, String avroSchemaLocation,
              EventPayloadOptions.EventFormat eventFormat) {
        super(className, avroSchemaLocation, eventFormat);
      }

      @Override
      public void setupSpecific() throws Exception {
        beamSchema = AvroUtils.toBeamSchema(avroSchema);
      }

      @Override
      public void processSpecific(ProcessContext context) throws Exception {
        context.output(
                SUCCESSFULLY_PROCESSED_EVENTS,
                retrieveRowFromTransport(
                        context.element(), eventFormat, thriftClass, beamSchema, avroSchema));
      }

    }
  }

  public static class TransformToGenericRecords extends TransformTransportToFormat<GenericRecord> {

    public static TupleTag<GenericRecord> SUCCESSFULLY_PROCESSED_EVENTS = new TupleTag<>() {
    };

    @Override
    public PCollectionTuple expand(PCollection<EventTransport> input) {
      var options = input.getPipeline().getOptions().as(EventPayloadOptions.class);
      return input
              .apply("TransformToGenericRecord",
                      ParDo.of(
                              new TransformTransportToGenericRecord(
                                      options.getClassName(),
                                      options.getAvroSchemaLocation(),
                                      options.getEventFormat()))
                              .withOutputTags(
                                      SUCCESSFULLY_PROCESSED_EVENTS,
                                      TupleTagList.of(FAILED_EVENTS)));

    }

    static class TransformTransportToGenericRecord extends TransformTransport<GenericRecord> {

      public TransformTransportToGenericRecord(
              String className, String avroSchemaLocation,
              EventPayloadOptions.EventFormat eventFormat) {
        super(className, avroSchemaLocation, eventFormat);
      }

      @Override
      public void processSpecific(ProcessContext context) throws Exception {
        context.output(
                SUCCESSFULLY_PROCESSED_EVENTS,
                retrieveGenericRecordFromTransport(
                        context.element(), eventFormat, thriftClass, avroSchema));
      }

    }
  }

  public static class TransformToTableRows extends TransformTransportToFormat<TableRow> {

    public static TupleTag<TableRow> SUCCESSFULLY_PROCESSED_EVENTS = new TupleTag<>() {
    };

    @Override
    public PCollectionTuple expand(PCollection<EventTransport> input) {
      var options = input.getPipeline().getOptions().as(EventPayloadOptions.class);
      return input
              .apply("TransformToGenericRecord",
                      ParDo.of(
                              new TransformTransportToTableRow(
                                      options.getClassName(),
                                      options.getAvroSchemaLocation(),
                                      options.getEventFormat()))
                              .withOutputTags(
                                      SUCCESSFULLY_PROCESSED_EVENTS,
                                      TupleTagList.of(FAILED_EVENTS)));
    }

    static class TransformTransportToTableRow extends TransformTransport<TableRow> {

      public TransformTransportToTableRow(
              String className, String avroSchemaLocation,
              EventPayloadOptions.EventFormat eventFormat) {
        super(className, avroSchemaLocation, eventFormat);
      }

      @Override
      public void processSpecific(ProcessContext context) throws Exception {
        context.output(
                SUCCESSFULLY_PROCESSED_EVENTS,
                retrieveTableRowFromTransport(
                        context.element(), eventFormat, thriftClass, avroSchema));
      }

    }
  }

  public static class TransformErrorTransportsToRow
          extends PTransform<PCollection<ErrorTransport>, PCollection<Row>> {

    @Override
    public PCollection<Row> expand(PCollection<ErrorTransport> input) {
      return input
              .apply("TransformErrorsToRow",
                      ParDo.of(
                              new TransformErrorTransportToRow()))
              .setRowSchema(ErrorTransport.ERROR_ROW_SCHEMA);
    }

    static class TransformErrorTransportToRow extends DoFn<ErrorTransport, Row> {

      @ProcessElement
      public void processElement(ProcessContext context) throws Exception {
        context.output(context.element().toRow());
      }
    }
  }

  static Row retrieveRowFromTransport(
          EventTransport transport,
          EventPayloadOptions.EventFormat eventFormat,
          Class<? extends TBase<?, ?>> thriftClass,
          Schema beamSchema,
          org.apache.avro.Schema avroSchema) {

    return AvroUtils.toBeamRowStrict(
            retrieveGenericRecordFromTransport(transport, eventFormat, thriftClass, avroSchema),
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
          EventPayloadOptions.EventFormat eventFormat,
          Class<? extends TBase<?, ?>> thriftClass,
          org.apache.avro.Schema avroSchema) {
    try {
      switch (eventFormat) {
        case AVRO: {
          var reader = new GenericDatumReader<GenericRecord>(avroSchema);
          var avroRec = new GenericData.Record(avroSchema);
          var decoder = DecoderFactory.get().binaryDecoder(
                  transport.getData(), 0, transport.getData().length, null);
          reader.read(avroRec, decoder);
          return avroRec;
        }
        case THRIFT: {
          var thriftEmptyInstance = thriftClass.getConstructor().newInstance();
          var thriftObject = getThriftObjectFromData(thriftEmptyInstance, transport.getData());
          return retrieveGenericRecordFromThriftData(thriftObject, avroSchema);
        }
        default:
          throw new RuntimeException("Format not implemented " + eventFormat);
      }
    } catch (Exception ex) {
      var msg = "Error while trying to retrieve a generic record from the transport object.";
      LOG.error(msg, ex);
      throw new RuntimeException(msg, ex);
    }
  }

  static TableRow retrieveTableRowFromTransport(
          EventTransport transport,
          EventPayloadOptions.EventFormat eventFormat,
          Class<? extends TBase<?, ?>> thriftClass,
          org.apache.avro.Schema avroSchema) {
    try {
      switch (eventFormat) {
        case AVRO: {
          var reader = new GenericDatumReader<GenericRecord>(avroSchema);
          var avroRec = new GenericData.Record(avroSchema);
          var decoder = DecoderFactory.get().binaryDecoder(
                  transport.getData(), 0, transport.getData().length, null);
          reader.read(avroRec, decoder);
          return BigQueryUtils.convertGenericRecordToTableRow(
                  avroRec,
                  BigQueryUtils.toTableSchema(
                          AvroUtils.toBeamSchema(avroSchema)));
        }
        case THRIFT: {
          var thriftEmptyInstance = thriftClass.getConstructor().newInstance();
          var thriftObject = getThriftObjectFromData(thriftEmptyInstance, transport.getData());
          var genericRecord = retrieveGenericRecordFromThriftData(thriftObject, avroSchema);
          return BigQueryUtils.convertGenericRecordToTableRow(
                  genericRecord,
                  BigQueryUtils.toTableSchema(
                          AvroUtils.toBeamSchema(avroSchema)));
        }
        default:
          throw new RuntimeException("Format not implemented " + eventFormat);
      }
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

  public static Schema retrieveRowSchema(EventPayloadOptions options) {
    switch (options.getEventFormat()) {
      case THRIFT: {
        var thriftClassName = options.getClassName();
        return retrieveRowSchema(thriftClassName);
      }
      case AVRO: {
        return AvroUtils.toBeamSchema(
                retrieveAvroSchemaFromLocation(options.getAvroSchemaLocation()));
      }
      default:
        throw new IllegalArgumentException(
                "Event format has not being implemented for ingestion: "
                + options.getEventFormat());
    }
  }

  public static org.apache.avro.Schema retrieveAvroSchema(EventPayloadOptions options) {
    switch (options.getEventFormat()) {
      case THRIFT: {
        var thriftClassName = options.getClassName();
        return retrieveAvroSchema(thriftClassName);
      }
      case AVRO: {
        return retrieveAvroSchemaFromLocation(options.getAvroSchemaLocation());
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

  public static org.apache.avro.Schema retrieveAvroSchema(String className) {
    try {
      var thriftRecord = retrieveThriftClass(className);
      return ThriftData.get().getSchema(thriftRecord);
    } catch (ClassNotFoundException ex) {
      var msg = "Error while trying to retrieve avro schema from " + className;
      LOG.error(msg, ex);
      throw new RuntimeException(msg, ex);
    }
  }

  public static Class<? extends TBase<?, ?>> retrieveThriftClass(String className)
          throws ClassNotFoundException {
    return (Class<? extends TBase<?, ?>>) Class.forName(
            className, true, Thread.currentThread().getContextClassLoader());
  }

  public static org.apache.avro.Schema retrieveAvroSchemaFromLocation(String avroSchemaLocation) {
    InputStream iStream = null;

    try {
      if (avroSchemaLocation.startsWith("classpath://")) {
        iStream = TransformTransportToFormat.class
                .getResourceAsStream(
                        avroSchemaLocation.replace("classpath://", "/"));
      } else {
        iStream
                = Channels.newInputStream(FileSystems.open(
                        FileSystems.matchNewResource(avroSchemaLocation, false)));
      }
      return new org.apache.avro.Schema.Parser().parse(iStream);
    } catch (Exception ex) {
      var msg = "Error while trying to retrieve the avro schema from location "
              + avroSchemaLocation;
      LOG.error(msg, ex);
      throw new RuntimeException(msg, ex);
    }
  }

}
