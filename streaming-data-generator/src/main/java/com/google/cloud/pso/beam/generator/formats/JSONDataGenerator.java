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
package com.google.cloud.pso.beam.generator.formats;

import autovalue.shaded.com.google.common.base.Preconditions;
import com.google.cloud.pso.beam.common.Utilities;
import com.google.cloud.pso.beam.common.formats.JsonUtils;
import com.google.cloud.pso.beam.generator.DataGenerator;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.beam.repackaged.core.org.apache.commons.lang3.RandomStringUtils;
import org.apache.beam.sdk.values.KV;
import org.everit.json.schema.ArraySchema;
import org.everit.json.schema.BooleanSchema;
import org.everit.json.schema.CombinedSchema;
import org.everit.json.schema.EnumSchema;
import org.everit.json.schema.NullSchema;
import org.everit.json.schema.NumberSchema;
import org.everit.json.schema.ObjectSchema;
import org.everit.json.schema.ReferenceSchema;
import org.everit.json.schema.Schema;
import org.everit.json.schema.StringSchema;
import org.json.JSONObject;

/** */
public class JSONDataGenerator implements DataGenerator {
  private static final Map<String, Map<Integer, String>> skewedStringValuesForProperties =
      new ConcurrentHashMap<>();
  private static final Map<String, Object> valueCache = new ConcurrentHashMap<>();

  private static final Random RANDOM = new Random();
  private static final Integer MAX_GENERATED_STRING_LENGTH = 20;
  private static final Integer MIN_GENERATED_STRING_LENGTH = 1;
  private static final Double RANDOM_ENABLER_THRESHOLD = 0.001D;
  private static final Integer MAX_SIZE_COLLECTION_TYPE = 10;

  private List<String> skewedProperties = List.of();
  private Integer skewDegree = 0;
  private Integer skewBuckets = 1000;
  private Schema schema;
  private final double randomFreq = RANDOM_ENABLER_THRESHOLD;
  private final String dataSchemaPath;
  private final int maxChars;
  private final int minChars;
  private final int maxSizeCollection;

  public JSONDataGenerator(
      String dataSchemaPath, int minChars, int maxChars, int maxSizeCollection) {
    this.dataSchemaPath = dataSchemaPath;
    this.maxChars = maxChars;
    this.minChars = minChars;
    this.maxSizeCollection = maxSizeCollection;
  }

  public static JSONDataGenerator create(
      String dataSchemaPath, int minChars, int maxChars, int maxSizeCollection) {
    return new JSONDataGenerator(dataSchemaPath, minChars, maxChars, maxSizeCollection);
  }

  @Override
  public void init() throws Exception {
    skewedProperties.forEach(
        name -> {
          skewedStringValuesForProperties.computeIfAbsent(name, n -> new ConcurrentHashMap<>());
        });
    initFromSchema();
  }

  private synchronized void initFromSchema() {
    if (schema != null) {
      return;
    }
    schema = JsonUtils.retrieveJsonSchemaFromLocation(dataSchemaPath);
  }

  @Override
  public void configureSkewedProperties(
      List<String> propertyNames, Integer skewDegree, Integer skewBuckets) {
    this.skewDegree = skewDegree;
    this.skewBuckets = skewBuckets;
    this.skewedProperties = List.copyOf(propertyNames);
  }

  public Object generateJSONObjectWithSchema(Schema jsonSchema, String propertyName) {
    if (jsonSchema instanceof ReferenceSchema refSchema) {
      return generateJSONObjectWithSchema(refSchema.getReferredSchema(), propertyName);
    } else if (jsonSchema instanceof ArraySchema arraySchema) {
      // we assume here that all the items have the same schema
      Preconditions.checkArgument(
          arraySchema.getItemSchemas().size() == 1,
          "There should be only 1 common schema for the array items: " + jsonSchema.toString());

      return IntStream.range(0, RANDOM.nextInt(maxSizeCollection))
          .mapToObj(i -> generateJSONObjectWithSchema(arraySchema.getAllItemSchema(), propertyName))
          .toList();
    } else if (jsonSchema instanceof ObjectSchema objSchema) {
      var propsAndSchemas =
          objSchema.getPropertySchemas().entrySet().stream()
              .map(
                  entry ->
                      KV.of(
                          entry.getKey(),
                          generateJSONObjectWithSchema(entry.getValue(), entry.getKey())))
              .collect(Collectors.toMap(KV::getKey, KV::getValue));

      return new JSONObject(propsAndSchemas);
    } else if (jsonSchema instanceof CombinedSchema combSchema) {
      // we support only null + other simple schema here, if this definition has more, then we fail
      // the translation
      if (combSchema.getSubschemas().size() > 2)
        throw new IllegalArgumentException(
            "The provided JSON schema has more than one option for the field "
                + "(besides the null option) and is not supported: "
                + combSchema.toString());
      var hasNullSchema =
          combSchema.getSubschemas().stream().filter(schema -> schema instanceof NullSchema).count()
              == 1;
      if (!hasNullSchema)
        throw new IllegalArgumentException(
            "The provided JSON schema does not have a null schema as part of the combined schema, "
                + "that's not supported: "
                + combSchema.toString());
      return generateJSONObjectWithSchema(
          combSchema.getSubschemas().stream()
              .filter(schema -> !(schema instanceof NullSchema))
              .findFirst()
              .orElseThrow(
                  () ->
                      new IllegalArgumentException(
                          "The provided JSON schema should have at least a non null schema as part "
                              + "of the combined schema: "
                              + combSchema.toString())),
          propertyName);
    } else if (jsonSchema instanceof EnumSchema enumSchema) {
      var possibleValues = enumSchema.getPossibleValuesAsList();
      return possibleValues.get(RANDOM.nextInt(possibleValues.size()));
    } else if (jsonSchema instanceof NumberSchema numSchema) {
      if (numSchema.requiresInteger()) {
        if (JsonUtils.shouldSchemaUseLongInsteadOfInteger(numSchema)) {
          return RANDOM.nextLong();
        } else return RANDOM.nextInt();
      } else return RANDOM.nextDouble();
    } else if (jsonSchema instanceof BooleanSchema) {
      return RANDOM.nextBoolean();
    } else if (jsonSchema instanceof StringSchema stringSchema) {
      var isTimestamp =
          Optional.ofNullable(stringSchema.getDefaultValue())
              .map(value -> ((String) value).equals("current-timestamp"))
              .orElse(false);
      return getStringValue(propertyName, randomFreq, isTimestamp);
    } else
      throw new IllegalArgumentException("JSON schema not supported: " + jsonSchema.toString());
  }

  private String getStringValue(String fieldName, double randomFreq, boolean isTimestampInMillis) {
    if (isTimestampInMillis) {
      return Instant.now().toString();
    }
    if (skewedStringValuesForProperties.containsKey(fieldName)) {
      return getSkewedStringValue(fieldName);
    }
    if (RANDOM.nextGaussian() < randomFreq) {
      return RandomStringUtils.randomAlphabetic(minChars, maxChars);
    } else {
      return (String)
          valueCache.computeIfAbsent(
              fieldName, b -> RandomStringUtils.randomAlphabetic(minChars, maxChars));
    }
  }

  private String getSkewedStringValue(String fieldName) {
    var bucket = Utilities.nextSkewedBoundedInteger(0, skewBuckets, skewDegree, 0);
    return skewedStringValuesForProperties
        .get(fieldName)
        .computeIfAbsent(bucket, n -> RandomStringUtils.randomAlphabetic(minChars, maxChars));
  }

  @Override
  public Object createInstance(boolean allFieldsPopulated) {
    return generateJSONObjectWithSchema(schema, null);
  }

  @Override
  public Iterable<Object> createInstance(boolean allFieldsPopulated, Integer count) {
    return IntStream.range(0, count - 1)
        .mapToObj(i -> generateJSONObjectWithSchema(schema, null))
        .toList();
  }

  @Override
  public KV<byte[], String> createInstanceAsBytesAndSchemaAsStringIfPresent(
      boolean allFieldsPopulated) throws Exception {
    return KV.of(
        generateJSONObjectWithSchema(schema, null).toString().getBytes(StandardCharsets.UTF_8),
        schema.toString());
  }
}
