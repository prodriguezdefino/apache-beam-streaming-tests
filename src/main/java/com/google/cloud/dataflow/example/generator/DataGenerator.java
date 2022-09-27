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
package com.google.cloud.dataflow.example.generator;

import com.google.cloud.dataflow.example.generator.formats.AvroDataGenerator;
import com.google.cloud.dataflow.example.generator.formats.ThriftDataGenerator;
import java.io.IOException;
import java.io.Serializable;
import org.apache.beam.sdk.values.KV;

/**
 *
 */
public interface DataGenerator extends Serializable {

  public enum Format {
    AVRO,
    THRIFT
  }

  default void init() throws Exception {
  }

  Object createInstance(boolean allFieldsPopulated);

  KV<byte[], String> createInstanceAsBytesAndSchemaAsStringIfPresent(
          boolean allFieldsPopulated) throws Exception;

  static DataGenerator createDataGenerator(
          Format format,
          Class clazz,
          int minChars,
          int maxChars,
          int maxSizeCollectionType,
          String filePath) throws IOException {
    switch (format) {
      case AVRO:
        return AvroDataGenerator.createFromFile(filePath);
      case THRIFT:
        return ThriftDataGenerator.create(clazz, minChars, maxChars, maxSizeCollectionType);
      default:
        throw new IllegalArgumentException("The format is not supported.");
    }
  }

}
