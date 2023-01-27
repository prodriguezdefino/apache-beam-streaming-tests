package com.google.cloud.pso.beam.generator.formats;

import com.google.cloud.pso.beam.generator.DataGenerator;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.util.ArrayList;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.avro.Schema;
import org.apache.avro.file.DataFileStream;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.util.RandomData;
import org.apache.beam.sdk.io.FileSystems;
import org.apache.beam.sdk.values.KV;
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

  private final String filePath;
  private final String dataSchemaPath;
  private Schema schema;
  private boolean fromFile;
  private final boolean transformTimestamps = true;

  private AvroDataGenerator(String dataSchema, String filePath) {
    this.dataSchemaPath = dataSchema;
    this.filePath = filePath;
  }

  public Schema getSchema() {
    return schema;
  }

  synchronized private void initFromFile() throws IOException {
    if (!DATA_CACHE.isEmpty()) {
      return;
    }
    Preconditions.checkState(filePath != null, "A file path should be provided");
    var chan
            = FileSystems.open(
                    FileSystems.matchNewResource(
                            filePath, false));
    var stream
            = new DataFileStream<GenericRecord>(
                    Channels.newInputStream(chan), new GenericDatumReader<>());
    schema = stream.getSchema();
    GenericRecord record = null;
    while (stream.hasNext()) {
      record = stream.next();
      record = modifyTimestamps(record);
      DATA_CACHE.add(record);
    }
  }

  synchronized private void initFromSchema() throws IOException {
    if (schema != null) {
      return;
    }
    InputStream iStream = null;
    if (dataSchemaPath.startsWith("classpath://")) {
      iStream = this.getClass().getResourceAsStream(dataSchemaPath.replace("classpath://", "/"));
    } else {
      iStream
              = Channels.newInputStream(FileSystems.open(
                      FileSystems.matchNewResource(
                              dataSchemaPath, false)));
    }
    schema = new Schema.Parser().parse(iStream);
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
    var gen = new AvroDataGenerator(null, filePath);
    gen.fromFile = true;
    return gen;
  }

  public static AvroDataGenerator createFromSchema(String schemaPath) {
    var gen = new AvroDataGenerator(schemaPath, null);
    gen.fromFile = false;
    return gen;
  }

  @Override
  public Object createInstance(boolean allFieldsPopulated) {
    if (fromFile) {
      return DATA_CACHE.get(RANDOM.nextInt(DATA_CACHE.size()));
    }
    return new RandomData(schema, 1, true).iterator().next();
  }

  @Override
  public Iterable<Object> createInstance(boolean allFieldsPopulated, Integer count) {
    if (fromFile) {
      return IntStream
              .range(0, count - 1)
              .mapToObj(i -> DATA_CACHE.get(RANDOM.nextInt(DATA_CACHE.size())))
              .collect(Collectors.toList());
    }
    return new RandomData(schema, count, true);
  }

  @Override
  public KV<byte[], String> createInstanceAsBytesAndSchemaAsStringIfPresent(
          boolean allFieldsPopulated) throws Exception {
    var record = (GenericRecord) createInstance(allFieldsPopulated);
    var writer = new GenericDatumWriter<GenericRecord>(record.getSchema());
    var stream = new ByteArrayOutputStream();
    var encoder = EncoderFactory.get().binaryEncoder(stream, null);
    writer.write(record, encoder);
    encoder.flush();
    return KV.of(stream.toByteArray(), record.getSchema().toString());
  }

  @Override
  public void init() throws Exception {
    if (fromFile) {
      initFromFile();
    } else {
      initFromSchema();
    }
  }

}
