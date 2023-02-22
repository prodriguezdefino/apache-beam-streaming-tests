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
import com.google.cloud.pso.beam.generator.thrift.CompoundEvent;
import com.google.common.collect.Lists;
import com.google.common.math.Quantiles;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import org.apache.avro.file.CodecFactory;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.thrift.TSerializer;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 *
 */
@RunWith(JUnit4.class)
public class StreamingDataGeneratorTest {

  public StreamingDataGeneratorTest() {
  }

  @Test
  public void testMakeThriftMessage() {
    var sizes = new ArrayList<Long>();
    var times = new ArrayList<Long>();
    var gen = ThriftDataGenerator.create(CompoundEvent.class, 5, 25, 20);
    gen.configureSkewedProperties(List.of("uuid"), 3, 10);
    List<Object> gens = Lists.newArrayList();
    for (int i = 0; i < 1000; i++) {
      var start = System.nanoTime();
      var obj = gen.populateNewInstance(true, 0.001D);
      gens.add(obj);
      times.add(System.nanoTime() - start);
      Assert.assertNotNull(obj);
      Assert.assertTrue(obj instanceof CompoundEvent);
      TSerializer serializer = null;
      try {
        serializer = new TSerializer(new TBinaryProtocol.Factory());
        var serialized = serializer.serialize(obj);
        sizes.add((long) serialized.length);
      } catch (Exception e) {
        throw new RuntimeException("Error while creating a TSerializer.", e);
      }
    }
    System.out.println("Thrift gen size percentiles (bytes): "
            + Quantiles.percentiles().indexes(50, 90, 95).compute(sizes).toString());
    System.out.println("Thrift gen time percentiles (ns): "
            + Quantiles.percentiles().indexes(50, 90, 95).compute(times).toString());
  }

  @Test
  public void testMakeAvroMessage() throws Exception {
    var sizes = new ArrayList<Long>();
    var times = new ArrayList<Long>();
    var gen = AvroDataGenerator.createFromSchema("classpath://complex-event.avsc");
    gen.init();
    var schema = gen.getSchema();
    var datumWriter = new GenericDatumWriter(schema);
    var fileWriter = new DataFileWriter(datumWriter);
    fileWriter.setCodec(CodecFactory.snappyCodec());
    for (int i = 0; i < 1000; i++) {
      var start = System.nanoTime();
      var obj = gen.createInstance(true);
      times.add(System.nanoTime() - start);
      Assert.assertNotNull(obj);
      var out = new ByteArrayOutputStream();
      fileWriter.create(schema, out);
      fileWriter.append(obj);
      fileWriter.close();
      var serialized = out.toByteArray();
      sizes.add((long) serialized.length);
    }
    System.out.println("Avro gen size percentiles (bytes): "
            + Quantiles.percentiles().indexes(50, 90, 95).compute(sizes).toString());
    System.out.println("Avro gen time percentiles (ns): "
            + Quantiles.percentiles().indexes(50, 90, 95).compute(times).toString());
  }

}
