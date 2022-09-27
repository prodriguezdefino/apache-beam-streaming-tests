package com.google.cloud.dataflow.example.generator.formats;

import com.google.cloud.dataflow.example.generator.DataGenerator;
import com.google.common.collect.Lists;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.Random;
import java.util.stream.Collectors;
import org.apache.avro.Schema;
import org.apache.avro.file.DataFileStream;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.Encoder;
import org.apache.avro.io.EncoderFactory;
import org.apache.beam.sdk.io.FileSystems;
import org.apache.beam.sdk.values.KV;
import org.apache.commons.lang.NotImplementedException;
import org.joda.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class AvroDataGenerator implements DataGenerator {

  private static final Logger LOG = LoggerFactory.getLogger(AvroDataGenerator.class);
  private static final Random RANDOM = new Random();
  private static final ArrayList<GenericRecord> DATA_CACHE = Lists.newArrayList();

  private String filePath;
  private Schema dataSchema;
  private boolean fromFile;
  private boolean transformTimestamps = true;

  private AvroDataGenerator(String filePath, Schema dataSchema) {
    this.filePath = filePath;
    this.dataSchema = dataSchema;
  }

  private AvroDataGenerator(Schema dataSchema) {
    this.dataSchema = dataSchema;
  }

  private AvroDataGenerator(String filePath) {
    this.filePath = filePath;
  }

  synchronized private void initFromFile() throws IOException {
    if (!DATA_CACHE.isEmpty()) {
      return;
    }
    ReadableByteChannel chan
            = FileSystems.open(
                    FileSystems.matchNewResource(
                            filePath, false));
    DatumReader<GenericRecord> datumReader = new GenericDatumReader<>();
    DataFileStream<GenericRecord> stream = new DataFileStream<>(Channels.newInputStream(chan), datumReader);
    dataSchema = stream.getSchema();
    GenericRecord record = null;
    while (stream.hasNext()) {
      record = stream.next();
      record = modifyTimestamps(record);
      DATA_CACHE.add(record);
    }
  }

  GenericRecord modifyTimestamps(GenericRecord record) {
    for (Schema.Field field : record.getSchema().getFields()) {
      if (!field.schema().getTypes().isEmpty()
              && field.schema()
                      .getTypes()
                      .stream()
                      .anyMatch(s -> s.getLogicalType() != null 
                              && s.getLogicalType().getName().startsWith("timestamp"))
              && transformTimestamps) {
        record.put(field.name(), Instant.now().getMillis());
        LOG.debug("trasformed field name {}, new field value {}, field types [{}]",
                field.name(),
                record.get(field.name()),
                field.schema()
                        .getTypes()
                        .stream()
                        .map(s -> String.format("[type %s, logical type %s]", s.getType(), s.getLogicalType()))
                        .collect(Collectors.joining(",")));
      }
    }
    return record;
  }

  public static AvroDataGenerator createFromFile(String filePath) throws IOException {
    AvroDataGenerator gen = new AvroDataGenerator(filePath);
    gen.fromFile = true;
    return gen;
  }

  public static AvroDataGenerator createFromSchema(String schemaStr) {
    throw new NotImplementedException(
            "generating avro data from an arbitrary schema has not been implemented yet");
  }

  @Override
  public Object createInstance(boolean allFieldsPopulated) {
    return DATA_CACHE.get(RANDOM.nextInt(DATA_CACHE.size()));
  }

  @Override
  public KV<byte[], String> createInstanceAsBytesAndSchemaAsStringIfPresent(
          boolean allFieldsPopulated) throws Exception {
    GenericRecord record = (GenericRecord) createInstance(allFieldsPopulated);
    DatumWriter<GenericRecord> writer = new GenericDatumWriter<>(record.getSchema());
    ByteArrayOutputStream stream = new ByteArrayOutputStream();
    Encoder encoder = EncoderFactory.get().binaryEncoder(stream, null);
    writer.write(record, encoder);
    encoder.flush();
    return KV.of(stream.toByteArray(), record.getSchema().toString());
  }

  @Override
  public void init() throws Exception {
    if (fromFile) {
      initFromFile();
    }
  }

}
