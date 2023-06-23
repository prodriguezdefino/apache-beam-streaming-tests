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
package com.google.cloud.pso.beam.pipelines.options;

import com.google.cloud.pso.beam.common.formats.options.TransportFormatOptions;
import org.apache.beam.sdk.io.gcp.bigquery.BigQueryIO;
import org.apache.beam.sdk.options.Default;
import org.apache.beam.sdk.options.Description;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.options.Validation;

/** A collection of options needed when writing to BigQuery. */
public interface BigQueryWriteOptions extends PipelineOptions, TransportFormatOptions {

  public enum BigQueryInputFormat {
    TABLE_ROW,
    AVRO_GENERIC_RECORD,
    BEAM_ROW
  }

  @Description(
      "The BigQuery destination table. When using table destination count > 1 "
          + "this will be used as the base table name.")
  @Validation.Required
  String getOutputTable();

  void setOutputTable(String value);

  @Description(
      "The quantity of destination tables the data will be written to " + "(Random distribution).")
  @Default.Integer(1)
  Integer getTableDestinationCount();

  void setTableDestinationCount(Integer value);

  @Description("Configures if the pipeline should create BQ tables.")
  @Default.Boolean(true)
  Boolean isCreateBQTable();

  void setCreateBQTable(Boolean value);

  @Description("Configures the time partitioning column in BigQuery.")
  @Default.String("")
  String getTimePartitioningColumn();

  void setTimePartitioningColumn(String value);

  @Description("Configures partitioning type (HOUR, DAY, MONTH, YEAR).")
  @Default.String("HOUR")
  String getTimePartitioningType();

  void setTimePartitioningType(String value);

  @Description("Configures the write method used to ingest data into BigQuery.")
  @Default.Enum("STORAGE_API_AT_LEAST_ONCE")
  BigQueryIO.Write.Method getBigQueryWriteMethod();

  void setBigQueryWriteMethod(BigQueryIO.Write.Method value);

  @Description(
      "The number of storage write api stream to create to write into BQ " + "(Exactly Once mode).")
  @Default.Integer(1)
  Integer getNumStorageWriteEOStreams();

  void setNumStorageWriteEOStreams(Integer value);

  @Description("Enables data to written with skew when using multiple destination tables.")
  @Default.Boolean(false)
  Boolean isDestinationTableLoadSkewed();

  void setDestinationTableLoadSkewed(Boolean value);

  @Description("Defines the format to use as BigQueryIO input type.")
  @Default.Enum("AVRO_GENERIC_RECORD")
  BigQueryInputFormat getFormatToStore();

  void setFormatToStore(BigQueryInputFormat value);

  @Description("Enables auto-shard when using StreamingInserts into BigQuery")
  @Default.Boolean(false)
  Boolean isEnableBigQueryAutoshard();

  void setEnableBigQueryAutoshard(Boolean value);
}
