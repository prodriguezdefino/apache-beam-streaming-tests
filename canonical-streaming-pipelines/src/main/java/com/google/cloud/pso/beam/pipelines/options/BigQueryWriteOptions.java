/*
 * Copyright (C) 2022 Google Inc.
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
package com.google.cloud.pso.beam.pipelines.options;

import org.apache.beam.sdk.io.gcp.bigquery.BigQueryIO;
import org.apache.beam.sdk.options.Default;
import org.apache.beam.sdk.options.Description;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.options.Validation;

/**
 * A collection of options needed when writing to BigQuery.
 */
public interface BigQueryWriteOptions extends PipelineOptions, EventPayloadOptions {

  @Description("The BigQuery destination table. When using table destination count > 1 this will be used as the base table name.")
  @Validation.Required
  String getOutputTable();

  void setOutputTable(String value);

  @Description("The quantity of destination tables the data will be written to (Random distribution).")
  @Default.Integer(1)
  Integer getTableDestinationCount();

  void setTableDestinationCount(Integer value);

  @Description("Configures if the pipeline should create BQ tables.")
  @Default.Boolean(true)
  Boolean isCreateBQTable();

  void setCreateBQTable(Boolean value);

  @Description("Configures the write method used to ingest data into BigQuery.")
  @Default.Enum("STORAGE_API_AT_LEAST_ONCE")
  BigQueryIO.Write.Method getBigQueryWriteMethod();

  void setBigQueryWriteMethod(BigQueryIO.Write.Method value);

  @Description("The number of storage write api stream to create to write into BQ (Exactly Once mode).")
  @Default.Integer(1)
  Integer getNumStorageWriteEOStreams();

  void setNumStorageWriteEOStreams(Integer value);

  @Description("Enables the use of Avro GenericRecords to store in BigQuery")
  @Default.Boolean(false)
  Boolean isUsingAvroToStore();

  void setUsingAvroToStore(Boolean value);

}
