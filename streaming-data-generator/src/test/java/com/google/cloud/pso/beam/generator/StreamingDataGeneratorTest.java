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

import com.google.cloud.pso.beam.generator.formats.ThriftDataGenerator;
import com.google.cloud.pso.beam.generator.thrift.CompoundEvent;
import com.google.common.math.Quantiles;
import java.util.ArrayList;
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
    var gen = ThriftDataGenerator.create(CompoundEvent.class, 10, 25, 2);
    for (int i = 0; i < 1000; i++) {
      var start = System.nanoTime();
      var obj = gen.populateNewInstance(true, 0.001D);
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
    System.out.println("Gen size percentiles (bytes): "
            + Quantiles.percentiles().indexes(50, 90, 95).compute(sizes).toString());
    System.out.println("Gen time percentiles (ns): "
            + Quantiles.percentiles().indexes(50, 90, 95).compute(times).toString());
  }

}
