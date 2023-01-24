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
package com.google.cloud.pso.beam.generator.formats;

import com.google.cloud.pso.beam.generator.DataGenerator;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.beam.repackaged.core.org.apache.commons.lang3.RandomStringUtils;
import org.apache.beam.sdk.values.KV;
import org.apache.commons.lang.StringUtils;
import org.apache.thrift.TBase;
import org.apache.thrift.TException;
import org.apache.thrift.TFieldRequirementType;
import org.apache.thrift.TSerializer;
import org.apache.thrift.meta_data.EnumMetaData;
import org.apache.thrift.meta_data.FieldMetaData;
import org.apache.thrift.meta_data.FieldValueMetaData;
import org.apache.thrift.meta_data.ListMetaData;
import org.apache.thrift.meta_data.MapMetaData;
import org.apache.thrift.meta_data.SetMetaData;
import org.apache.thrift.meta_data.StructMetaData;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ThriftDataGenerator implements DataGenerator {

  private static final Logger LOG = LoggerFactory.getLogger(ThriftDataGenerator.class);
  private static final Random RANDOM = new Random();
  private static final Integer MAX_GENERATED_STRING_LENGTH = 20;
  private static final Integer MIN_GENERATED_STRING_LENGTH = 1;
  private static final Double RANDOM_ENABLER_THRESHOLD = 0.001D;
  private static final Integer MAX_SIZE_COLLECTION_TYPE = 10;

  private static final Map<String, Class> classCache = new ConcurrentHashMap<>();
  private static final Map<String, Object> valueCache = new ConcurrentHashMap<>();

  private final int maxChars;
  private final int minChars;
  private final int maxSizeCollection;
  private final boolean allFieldPopulated = true;
  private final double randomFreq = RANDOM_ENABLER_THRESHOLD;
  private final Class clazz;

  ThriftDataGenerator(Class clazz, Optional<Integer> minChars, Optional<Integer> maxChars, Optional<Integer> maxSizeCollectionType) {
    this.clazz = clazz;
    this.maxChars = maxChars.orElse(MAX_GENERATED_STRING_LENGTH);
    this.minChars = minChars.orElse(MIN_GENERATED_STRING_LENGTH);
    this.maxSizeCollection = maxSizeCollectionType.orElse(MAX_SIZE_COLLECTION_TYPE);
  }

  public static ThriftDataGenerator create(Class clazz, int minChars, int maxChars, int maxSizeCollectionType) {
    return new ThriftDataGenerator(clazz, Optional.of(minChars), Optional.of(maxChars), Optional.of(maxSizeCollectionType));
  }

  public static ThriftDataGenerator create(Class clazz, int maxSizeCollectionType) {
    return new ThriftDataGenerator(clazz, Optional.empty(), Optional.empty(), Optional.of(maxSizeCollectionType));
  }

  public static ThriftDataGenerator create(Class clazz, int minChars, int maxChars) {
    return new ThriftDataGenerator(clazz, Optional.of(minChars), Optional.of(maxChars), Optional.empty());
  }

  public static ThriftDataGenerator create(Class clazz) {
    return new ThriftDataGenerator(clazz, Optional.empty(), Optional.empty(), Optional.empty());
  }

  public TBase populateNewInstance() {
    return populateNewInstance(clazz, allFieldPopulated, randomFreq);
  }

  public TBase populateNewInstance(boolean allFieldsPopulated) {
    return populateNewInstance(clazz, allFieldsPopulated, randomFreq);
  }

  public TBase populateNewInstance(boolean allFieldsPopulated, double randomFreq) {
    return populateNewInstance(clazz, allFieldsPopulated, randomFreq);
  }

  private boolean containsField(Class cls, String fieldName) {
    return Stream.of(cls.getFields()).anyMatch(f -> f.getName().equals(fieldName));
  }

  private Optional<Method> findMethodByName(Class cls, String fieldName) {
    return Stream.of(cls.getMethods()).filter(f -> {
      return f.getName().equals(fieldName);
    }).findAny();
  }

  private TBase populateNewInstance(Class clazz, boolean allFieldsPopulated, double randomFreq) {
    TBase value = null;
    try {
      if (TBase.class.isAssignableFrom(clazz)) { // struct
        value = (TBase) clazz.getDeclaredConstructor().newInstance();
        for (FieldMetaData f : ((Map<? extends TBase, FieldMetaData>) FieldMetaData.getStructMetaDataMap((Class<? extends TBase>) clazz)).values()) {
          Object fieldValue = createValue(f, f.valueMetaData, allFieldsPopulated, randomFreq);

          // check for container types, because we won't be able to use reflection on those.
          if (f.valueMetaData.isContainer() && containsField(clazz, f.fieldName)) {
            Field field = clazz.getDeclaredField(f.fieldName);
            field.setAccessible(true);
            field.set(value, fieldValue);
          } else if (f.valueMetaData.isContainer()) {
            Method setter
                    = findMethodByName(
                            clazz,
                            "set" + StringUtils.capitalize(f.fieldName)).get();
            if (fieldValue != null) {
              setter.invoke(value, fieldValue);
            }
          } else {
            Method setter
                    = clazz.getDeclaredMethod(
                            "set" + StringUtils.capitalize(f.fieldName),
                            getClassFromField(f.valueMetaData));
            if (fieldValue != null) {
              setter.invoke(value, fieldValue);
            }
          }
        }
      } else {
        throw new RuntimeException("Not a Thrift-generated class: " + clazz);
      }
    } catch (IllegalAccessException
            | IllegalArgumentException
            | InstantiationException
            | NoSuchFieldException
            | NoSuchMethodException
            | InvocationTargetException e) {
      throw new RuntimeException("Error while generating value for class " + clazz.toString(), e);
    }
    return value;
  }

  private Optional<Object> getValueToAssign(Supplier<Object> fieldValue, FieldMetaData f, boolean allFieldsPopulated) {
    if (!allFieldsPopulated
            && f.requirementType != TFieldRequirementType.REQUIRED
            && !RANDOM.nextBoolean()) {
      return Optional.empty();
    }
    return Optional.of(fieldValue.get());
  }

  private static Class getClassFromField(FieldValueMetaData f) {
    switch (f.type) {
      case TType.BOOL:
        return boolean.class;
      case TType.BYTE:
        return byte.class;
      case TType.I16:
        return short.class;
      case TType.I32:
        return int.class;
      case TType.I64:
        return long.class;
      case TType.DOUBLE:
        return double.class;
      case TType.ENUM:
        EnumMetaData enumMeta = (EnumMetaData) f;
        return enumMeta.enumClass;
      case TType.STRING:
        return f.isBinary() ? ByteBuffer.class : String.class;
      case TType.STRUCT:
        Class structClass;
        if (f instanceof StructMetaData) {
          StructMetaData structMeta = (StructMetaData) f;
          structClass = structMeta.structClass;
        } else {
          // Some thrift version will use structMeta + typedefname as struct.
          structClass
                  = classCache.computeIfAbsent(
                          f.getTypedefName(),
                          cName -> {
                            try {
                              return Class.forName(cName);
                            } catch (ClassNotFoundException ex) {
                              throw new IllegalArgumentException(ex);
                            }
                          });
        }
        return structClass;
      case TType.VOID:
        return null;
      default:
        return null;
    }
  }

  private Object createList(
          FieldMetaData fieldMetadata,
          ListMetaData listMeta,
          double randomFreq) {
    int maybeRange = RANDOM.nextInt(maxSizeCollection);
    return IntStream
            .range(0, maybeRange == 0 ? 1 : maybeRange)
            .mapToObj(i -> createValue(fieldMetadata, listMeta.elemMetaData, true, randomFreq))
            .collect(Collectors.toList());
  }

  private Object createSet(
          FieldMetaData fieldMetadata,
          SetMetaData setMeta,
          double randomFreq) {
    int maybeRange = RANDOM.nextInt(maxSizeCollection);
    return IntStream
            .range(0, maybeRange == 0 ? 1 : maybeRange)
            .mapToObj(i -> createValue(fieldMetadata, setMeta.elemMetaData, true, randomFreq))
            .collect(Collectors.toSet());
  }

  private Object createMap(
          FieldMetaData fieldMetadata,
          MapMetaData mapMeta,
          double randomFreq) {
    int maybeRange = RANDOM.nextInt(maxSizeCollection);
    return IntStream
            .range(0, maybeRange == 0 ? 1 : maybeRange)
            .mapToObj(i -> Map.entry(
            createValue(fieldMetadata, mapMeta.keyMetaData, true, randomFreq),
            createValue(fieldMetadata, mapMeta.valueMetaData, true, randomFreq)))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a));
  }

  private Object createValue(
          FieldMetaData fieldMetadata,
          FieldValueMetaData fieldValueMetadata,
          boolean allFieldsPopulated,
          double randomFreq) {

    switch (fieldValueMetadata.type) {
      case TType.BOOL:
        return getValueToAssign(() -> RANDOM.nextBoolean(), fieldMetadata, allFieldsPopulated).orElse(null);
      case TType.BYTE:
        byte[] rbytes = new byte[1];
        RANDOM.nextBytes(rbytes);
        return getValueToAssign(() -> rbytes[0], fieldMetadata, allFieldsPopulated).orElse(null);
      case TType.I16:
        return getValueToAssign(() -> (short) RANDOM.nextInt(Short.MAX_VALUE + 1), fieldMetadata, allFieldsPopulated).orElse(null);
      case TType.I32:
        return getValueToAssign(() -> (int) RANDOM.nextInt(), fieldMetadata, allFieldsPopulated).orElse(null);
      case TType.I64:
        return getValueToAssign(() -> RANDOM.nextLong(), fieldMetadata, allFieldsPopulated).orElse(null);
      case TType.DOUBLE:
        return getValueToAssign(() -> RANDOM.nextDouble(), fieldMetadata, allFieldsPopulated).orElse(null);
      case TType.ENUM:
        EnumMetaData enumMeta = (EnumMetaData) fieldValueMetadata;
        List<Enum> symbols = new ArrayList<>();
        symbols.addAll(Arrays.asList(((Class<? extends Enum>) enumMeta.enumClass).getEnumConstants()));
        return getValueToAssign(() -> symbols.get(RANDOM.nextInt(symbols.size())), fieldMetadata, allFieldsPopulated).orElse(null);
      case TType.LIST:
        ListMetaData listMeta = (ListMetaData) fieldValueMetadata;
        return getValueToAssign(
                () -> createList(fieldMetadata, listMeta, randomFreq),
                fieldMetadata,
                allFieldsPopulated)
                .orElse(List.of());
      case TType.MAP:
        MapMetaData mapMeta = (MapMetaData) fieldValueMetadata;
        return getValueToAssign(
                () -> createMap(fieldMetadata, mapMeta, randomFreq),
                fieldMetadata,
                allFieldsPopulated)
                .orElse(Map.of());
      case TType.SET:
        SetMetaData setMeta = (SetMetaData) fieldValueMetadata;
        return getValueToAssign(
                () -> createSet(fieldMetadata, setMeta, randomFreq),
                fieldMetadata,
                allFieldsPopulated)
                .orElse(Set.of());
      case TType.STRING:
        String value = getStringValue(fieldMetadata.fieldName, randomFreq);
        if (fieldValueMetadata.isBinary()) {
          return ByteBuffer.wrap(value.getBytes());
        }
        return getValueToAssign(
                () -> fieldValueMetadata.isBinary() ? ByteBuffer.wrap(value.getBytes()) : value,
                fieldMetadata,
                allFieldsPopulated).orElse(null);
      case TType.STRUCT:
        return getValueToAssign(
                () -> {
                  try {
                    return populateNewInstance(
                            fieldValueMetadata instanceof StructMetaData
                                    ? ((StructMetaData) fieldValueMetadata).structClass
                                    : Class.forName(fieldValueMetadata.getTypedefName()),
                            allFieldsPopulated,
                            randomFreq);
                  } catch (Exception e) {
                    throw new IllegalArgumentException("Couldn't find class:" + ((StructMetaData) fieldValueMetadata).structClass);
                  }
                },
                fieldMetadata,
                allFieldsPopulated
        ).orElse(null);
      case TType.VOID:
        return null;
      default:
        throw new RuntimeException("Unexpected type in field: " + fieldValueMetadata);
    }
  }

  private String getStringValue(String fieldName, double randomFreq) {
    if (RANDOM.nextGaussian() < randomFreq) {
      return RandomStringUtils.randomAlphabetic(minChars, maxChars);
    } else {
      return (String) valueCache.computeIfAbsent(
              fieldName,
              b -> RandomStringUtils.randomAlphabetic(minChars, maxChars));
    }
  }

  @Override
  public Object createInstance(boolean allFieldsPopulated) {
    return populateNewInstance(allFieldsPopulated);
  }

  @Override
  public Iterable<Object> createInstance(boolean allFieldsPopulated, Integer count) {
    return IntStream
            .range(0, count - 1)
            .mapToObj(i -> populateNewInstance(allFieldsPopulated))
            .collect(Collectors.toList());
  }

  @Override
  public KV<byte[], String> createInstanceAsBytesAndSchemaAsStringIfPresent(boolean allFieldsPopulated) throws Exception {
    TSerializer serializer = null;
    try {
      serializer = new TSerializer(new TBinaryProtocol.Factory());
    } catch (Exception e) {
      throw new RuntimeException("Error while creating a TSerializer.", e);
    }
    byte[] serializedMessage;
    try {
      serializedMessage = serializer.serialize((TBase) createInstance(allFieldsPopulated));
      // no schema definition for the thrift object so we send an empty string
      return KV.of(serializedMessage, "");
    } catch (TException e) {
      throw new RuntimeException("Error while serializing the object.", e);
    }
  }

}
