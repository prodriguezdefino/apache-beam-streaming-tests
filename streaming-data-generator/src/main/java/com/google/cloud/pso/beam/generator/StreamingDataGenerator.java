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
package com.google.cloud.pso.beam.generator;

import com.google.cloud.pso.beam.common.compression.CompressionUtils;
import com.google.cloud.pso.beam.common.compression.thrift.ThriftCompression;
import com.google.cloud.pso.beam.common.formats.TransportFormats.Format;
import com.google.cloud.pso.beam.common.transport.CommonTransport;
import com.google.cloud.pso.beam.common.transport.EventTransport;
import com.google.cloud.pso.beam.common.transport.coder.CommonTransportCoder;
import com.google.cloud.pso.beam.options.StreamingSinkOptions;
import com.google.cloud.pso.beam.transforms.WriteStreamingSink;
import com.google.common.base.Splitter;
import com.google.common.math.Quantiles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.beam.runners.dataflow.options.DataflowPipelineOptions;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.io.GenerateSequence;
import org.apache.beam.sdk.metrics.Distribution;
import org.apache.beam.sdk.metrics.Metrics;
import org.apache.beam.sdk.options.Default;
import org.apache.beam.sdk.options.Description;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.values.KV;
import org.joda.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Generator pipeline which outputs PubSub messages. */
public class StreamingDataGenerator {

  private static final Logger LOG = LoggerFactory.getLogger(StreamingDataGenerator.class);
  private static final Boolean PRINT_GEN_LOGS = false;

  /** Options for the streaming data generator */
  public interface StreamingDataGeneratorOptions
      extends DataflowPipelineOptions, StreamingSinkOptions {

    @Description("How many raw events will be generated every second")
    @Default.Integer(200000)
    Integer getGeneratorRatePerSec();

    void setGeneratorRatePerSec(Integer value);

    @Description("How many events will be batched together")
    @Default.Integer(4500)
    Integer getMaxRecordsPerBatch();

    void setMaxRecordsPerBatch(Integer value);

    @Description("FQCN of the type that should be generated")
    @Default.String("com.google.cloud.pso.beam.generator.thrift.CompoundEvent")
    String getClassName();

    void setClassName(String value);

    @Description("Min char count for a generated String")
    @Default.Integer(20)
    Integer getMinStringLength();

    void setMinStringLength(Integer value);

    @Description("Max char count for a generated String")
    @Default.Integer(80)
    Integer getMaxStringLength();

    void setMaxStringLength(Integer value);

    @Description("Max elements on a collection type")
    @Default.Integer(20)
    Integer getMaxSizeCollection();

    void setMaxSizeCollection(Integer value);

    @Description("Enables ZLIB compression and batched envelope payloads")
    @Default.Boolean(false)
    Boolean isCompressionEnabled();

    void setCompressionEnabled(Boolean value);

    @Description("Object generation produces complete populated instances")
    @Default.Boolean(true)
    Boolean isCompleteObjects();

    void setCompleteObjects(Boolean value);

    @Description("Object generation produces complete populated instances")
    @Default.Integer(1)
    Integer getCompressionLevel();

    void setCompressionLevel(Integer value);

    @Description("Format of the generated messages")
    @Default.Enum("THRIFT")
    Format getFormat();

    void setFormat(Format value);

    @Description("File path for the already generated AVRO file with data to use.")
    @Default.String("")
    String getAvroFileLocation();

    void setAvroFileLocation(String value);

    @Description("File location for the schema used to generate data (JSON or AVRO).")
    @Default.String("")
    String getSchemaFileLocation();

    void setSchemaFileLocation(String value);

    @Description("A comma separated value string that indicates the fields that have certain skew.")
    @Default.String("")
    String getFieldsWithSkew();

    void setFieldsWithSkew(String value);

    @Description(
        "How skew the identified fields are, the bigger the number "
            + "the tighter the skew cluster is.")
    @Default.Integer(0)
    Integer getSkewDegree();

    void setSkewDegree(Integer value);

    @Description("How many different values will be generated for the skewed fields.")
    @Default.Integer(1000)
    Integer getSkewBuckets();

    void setSkewBuckets(Integer value);
  }

  static final Map<String, String> EMPTY_ATTRS = new HashMap<>();

  /**
   * Sets up and starts generator pipeline.
   *
   * @param args
   * @throws java.lang.ClassNotFoundException
   */
  public static void main(String[] args) throws Exception {
    var options =
        PipelineOptionsFactory.fromArgs(args)
            .withValidation()
            .as(StreamingDataGeneratorOptions.class);
    // setting as a streaming pipeline
    options.setStreaming(true);

    // Run generator pipeline
    var generator = Pipeline.create(options);

    // create a data generator for this class and based on the configured options
    var gen = DataGenerator.createDataGenerator(options);

    gen.configureSkewedProperties(
        Arrays.asList(options.getFieldsWithSkew().split(",")),
        options.getSkewDegree(),
        options.getSkewBuckets());

    var seqGeneratorRate =
        options.isCompressionEnabled()
            ? options.getGeneratorRatePerSec() / options.getMaxRecordsPerBatch()
            : options.getGeneratorRatePerSec();

    LOG.info("Generating {} raw elements per second.", options.getGeneratorRatePerSec());
    if (options.isCompressionEnabled()) {
      LOG.info("Batch {} elements together", options.getMaxRecordsPerBatch());
    }
    LOG.info("Sequence gen rate at {}", seqGeneratorRate);

    generator
        .getCoderRegistry()
        .registerCoderForClass(EventTransport.class, CommonTransportCoder.of());

    generator
        .apply(GenerateSequence.from(0L).withRate(seqGeneratorRate, Duration.standardSeconds(1)))
        .apply(
            String.format("Create%sPayload", options.getFormat().name()),
            ParDo.of(
                new CreateDataPayload(
                    gen,
                    options.isCompressionEnabled(),
                    options.isCompleteObjects(),
                    options.getCompressionLevel(),
                    options.getMaxRecordsPerBatch())))
        .apply(
            "WriteToStreamingSink",
            WriteStreamingSink.create(options.getOutputTopic(), options.getSinkType()));
    generator.run();
  }

  static class CreateDataPayload extends DoFn<Long, EventTransport> {

    private static final Logger LOG = LoggerFactory.getLogger(CreateDataPayload.class);

    private static final Distribution TIME_TO_GENERATE_BATCH =
        Metrics.distribution(CreateDataPayload.class, "batch_generation_ms");
    private static final Distribution BATCH_SIZE =
        Metrics.distribution(CreateDataPayload.class, "batch_compressed_size_bytes");
    private static final Distribution BATCH_RAW_SIZE =
        Metrics.distribution(CreateDataPayload.class, "batch_raw_size_bytes");
    private static final Distribution RAW_SIZE =
        Metrics.distribution(CreateDataPayload.class, "object_raw_size_bytes");

    private final boolean compressionEnabled;
    private final boolean generateCompleteObjects;
    private final DataGenerator gen;
    private final int compressionLevel;
    private final int recordsPerImpulse;
    private final List<Long> sizes = new ArrayList<>();
    private final List<Long> times = new ArrayList<>();

    public CreateDataPayload(
        DataGenerator dataGenerator,
        boolean compressionEnabled,
        boolean generateCompleteObjects,
        int compressionLevel,
        int recordsPerImpulse) {
      this.gen = dataGenerator;
      this.generateCompleteObjects = generateCompleteObjects;
      this.compressionLevel = compressionLevel;
      this.compressionEnabled = compressionEnabled;
      this.recordsPerImpulse = recordsPerImpulse;
    }

    @Setup
    public void setup() throws Exception {
      gen.init();
    }

    @StartBundle
    public void startBundle() {
      sizes.clear();
      times.clear();
    }

    @ProcessElement
    public void processElement(ProcessContext context) throws Exception {
      if (compressionEnabled) {
        var startTimeBatch = System.currentTimeMillis();
        var rawDataSize = 0;
        var elements = new ArrayList<com.google.cloud.pso.beam.envelope.Element>();
        for (int i = 0; i < recordsPerImpulse; i++) {
          var message = makeMessage().getKey();
          rawDataSize += message.length;
          RAW_SIZE.update(message.length);
          elements.add(ThriftCompression.constructElement(message, EMPTY_ATTRS));
        }
        var compressedData =
            ThriftCompression.compressEnvelope(
                ThriftCompression.constructEnvelope(elements, EMPTY_ATTRS), compressionLevel);
        TIME_TO_GENERATE_BATCH.update(System.currentTimeMillis() - startTimeBatch);
        BATCH_RAW_SIZE.update(rawDataSize);
        BATCH_SIZE.update(compressedData.length);
        context.output(
            new CommonTransport(
                UUID.randomUUID().toString(),
                Map.of(
                    CompressionUtils.COMPRESSION_TYPE_HEADER_KEY,
                    CompressionUtils.CompressionType.THRIFT_ZLIB.name()),
                compressedData));

      } else {
        var messageAndSchema = makeMessage();
        // compress the schema
        var compressedSchema = CompressionUtils.compressString(messageAndSchema.getValue());
        // partition the schema in multiple strings of ~1000 bytes (under the limit of 1k per map
        // entry)
        // and build an attribute map with it
        var attributeMap =
            Splitter.fixedLength(500).splitToList(compressedSchema).stream()
                .collect(
                    HashMap::new,
                    (Map<Integer, String> m, String s) -> m.put(m.size() + 1, s),
                    (m1, m2) -> {
                      int offset = m1.size();
                      m2.forEach((i, s) -> m1.put(i + offset, s));
                    })
                .entrySet()
                .stream()
                .map(e -> KV.of(EventTransport.SCHEMA_ATTRIBUTE_KEY + e.getKey(), e.getValue()))
                .collect(Collectors.toMap(KV::getKey, KV::getValue));
        context.output(
            new CommonTransport(
                UUID.randomUUID().toString(), attributeMap, messageAndSchema.getKey()));
      }
    }

    @FinishBundle
    public void finalizeBundle() {
      if (PRINT_GEN_LOGS) {
        LOG.info(
            "Gen size percentiles (bytes): {}",
            Quantiles.percentiles().indexes(50, 90, 95).compute(sizes).toString());
        LOG.info(
            "Gen time percentiles (ns): {}",
            Quantiles.percentiles().indexes(50, 90, 95).compute(times).toString());
      }
    }

    KV<byte[], String> makeMessage() {
      try {
        var objectStartTime = System.nanoTime();
        var serializedMessage =
            gen.createInstanceAsBytesAndSchemaAsStringIfPresent(generateCompleteObjects);
        times.add(System.nanoTime() - objectStartTime);
        sizes.add((long) serializedMessage.getKey().length);

        return serializedMessage;
      } catch (Exception e) {
        throw new RuntimeException("Error while serializing the object.", e);
      }
    }
  }
}
