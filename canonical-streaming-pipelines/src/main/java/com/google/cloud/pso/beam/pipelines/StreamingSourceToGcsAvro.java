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
package com.google.cloud.pso.beam.pipelines;

import com.google.cloud.pso.beam.common.formats.AvroUtils;
import com.google.cloud.pso.beam.common.formats.TransportFormats;
import com.google.cloud.pso.beam.common.transport.EventTransport;
import com.google.cloud.pso.beam.options.StreamingSourceOptions;
import com.google.cloud.pso.beam.transforms.ReadStreamingSource;
import com.google.cloud.pso.beam.transforms.WriteFormatToFileDestination;
import org.apache.avro.generic.GenericRecord;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.extensions.avro.coders.AvroCoder;
import org.apache.beam.sdk.extensions.avro.io.AvroIO;
import org.apache.beam.sdk.options.Description;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.options.Validation;
import org.apache.beam.sdk.options.ValueProvider;
import org.apache.beam.sdk.transforms.MapElements;
import org.apache.beam.sdk.transforms.windowing.FixedWindows;
import org.apache.beam.sdk.transforms.windowing.Window;
import org.apache.beam.sdk.values.TypeDescriptor;
import org.joda.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Ingestion pipeline for BigQuery, reads data from a specified StreamingSource. */
public class StreamingSourceToGcsAvro {

  private static final Logger LOG = LoggerFactory.getLogger(StreamingSourceToGcsAvro.class);

  /** Options for the ingestion pipeline */
  public interface StreamingSourceToBigQueryOptions extends StreamingSourceOptions {

    @Description("A GCS location to write data.")
    @Validation.Required
    String getGcsLocation();

    void setGcsLocation(String value);

    @Description("File location for the schema used to generate data (AVRO).")
    @Validation.Required
    String getSchemaFileLocation();

    void setSchemaFileLocation(String value);
  }

  /**
   * Sets up and starts ingestion pipeline.
   *
   * @param args
   * @throws
   */
  public static void main(String[] args) throws Exception {
    var options =
        PipelineOptionsFactory.fromArgs(args)
            .withValidation()
            .as(StreamingSourceToBigQueryOptions.class);

    // Create the pipeline
    var pipeline = Pipeline.create(options);

    var schemaLocation = options.getSchemaFileLocation();
    var schema = AvroUtils.retrieveAvroSchemaFromLocation(schemaLocation);
    var schemaStr = schema.toString();

    // read from the streaming sources and maybe decompress payloads
    pipeline
        .apply("ReadFromStreamingSource", ReadStreamingSource.create())
        .apply(
            "ExtractGenericRecord",
            MapElements.into(TypeDescriptor.of(GenericRecord.class))
                .via(
                    (EventTransport transport) ->
                        TransportFormats.<GenericRecord>handlerFactory(TransportFormats.Format.AVRO)
                            .apply(schemaLocation)
                            .decode(transport.getData())))
        .setCoder(AvroCoder.generic(schema))
        .apply(
            "WindowOf5m",
            Window.<GenericRecord>into(FixedWindows.of(Duration.standardMinutes(5)))
                .discardingFiredPanes())
        .apply(
            "WriteToGcs",
            WriteFormatToFileDestination.<GenericRecord>create()
                .withOutputDirectory(ValueProvider.StaticValueProvider.of(options.getGcsLocation()))
                .withTempDirectory(ValueProvider.StaticValueProvider.of(options.getTempLocation()))
                .withSinkProvider(() -> AvroIO.sink(schemaStr))
                .withOutputFilenamePrefix(ValueProvider.StaticValueProvider.of("data"))
                .withOutputFilenameSuffix(ValueProvider.StaticValueProvider.of(".avro"))
                .withHourlySuccessFiles(true)
                .withCreateSuccessFile(false));
    pipeline.run();
  }
}
