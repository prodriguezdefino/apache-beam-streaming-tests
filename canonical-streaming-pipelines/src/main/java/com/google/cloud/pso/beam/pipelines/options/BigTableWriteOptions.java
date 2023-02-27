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

import org.apache.beam.sdk.options.Default;
import org.apache.beam.sdk.options.Description;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.options.Validation;

/** A collection of options needed when writing to BigQuery. */
public interface BigTableWriteOptions extends PipelineOptions {

  @Description("The project id hosting the BigTable instance.")
  @Validation.Required
  String getBTProjectId();

  void setBTProjectId(String value);

  @Description("The BigTable instance id.")
  @Validation.Required
  String getBTInstanceId();

  void setBTInstanceId(String value);

  @Description("The BigTable table identifier.")
  @Validation.Required
  String getBTTableId();

  void setBTTableId(String value);

  @Description("The BigTable table column family name.")
  @Default.String("aggregation_results")
  String getBTColumnFamilyName();

  void setBTColumnFamilyName(String value);
}
