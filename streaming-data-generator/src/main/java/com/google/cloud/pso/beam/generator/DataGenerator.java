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
package com.google.cloud.pso.beam.generator;

import com.google.cloud.pso.beam.generator.formats.AvroDataGenerator;
import com.google.cloud.pso.beam.generator.formats.ThriftDataGenerator;
import java.io.IOException;
import java.io.Serializable;
import org.apache.beam.sdk.values.KV;

/**
 *
 */
public interface DataGenerator extends Serializable {

  public enum Format {
    AVRO_FROM_FILE,
    AVRO_FROM_SCHEMA,
    THRIFT
  }

  default void init() throws Exception {
  }

  Object createInstance(boolean allFieldsPopulated);

  Iterable<Object> createInstance(boolean allFieldsPopulated, Integer count);

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
      case AVRO_FROM_FILE:
        return AvroDataGenerator.createFromFile(filePath);
      case AVRO_FROM_SCHEMA:
        return AvroDataGenerator.createFromSchema(filePath);
      case THRIFT:
        return ThriftDataGenerator.create(clazz, minChars, maxChars, maxSizeCollectionType);
      default:
        throw new IllegalArgumentException("The format is not supported.");
    }
  }

}
