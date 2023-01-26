package com.google.cloud.pso.beam.pipelines.transforms;

import com.google.cloud.pso.beam.common.transport.CommonTransport;
import com.google.cloud.pso.beam.generator.thrift.Carrier;
import com.google.cloud.pso.beam.generator.thrift.CompoundEvent;
import com.google.cloud.pso.beam.generator.thrift.SimpleEvent;
import java.time.Instant;
import java.util.HashMap;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.beam.sdk.schemas.Schema;
import org.apache.beam.sdk.values.KV;
import org.apache.thrift.TBase;
import org.apache.thrift.TException;
import org.apache.thrift.TSerializer;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 */
public class PrepareBQIngestionTest {

  public PrepareBQIngestionTest() {
  }

  /**
   * Test of retrieveRowSchema method, of class PrepareBQIngestion.
   */
  @Test
  public void testRetrieveRowSchema() {
    String className = "com.google.cloud.pso.beam.generator.thrift.CompoundEvent";
    Schema result = PrepareBQIngestion.retrieveRowSchema(className);
    assertNotNull(result);
  }

  /**
   * Test of retrieveAvroSchema method, of class PrepareBQIngestion.
   */
  @Test
  public void testRetrieveAvroSchema() throws Exception {
    var className = "com.google.cloud.pso.beam.generator.thrift.CompoundEvent";
    var result = PrepareBQIngestion.retrieveAvroSchema(className);
    assertNotNull(result);
  }

  /**
   * Test of retrieveThriftClass method, of class PrepareBQIngestion.
   */
  @Test
  public void testRetrieveThriftClass() throws Exception {
    var className = "com.google.cloud.pso.beam.generator.thrift.CompoundEvent";
    var result = PrepareBQIngestion.retrieveThriftClass(className);
    assertNotNull(result);
  }

  @Test
  public void testTransformThriftToRow() throws Exception {
    var className = "com.google.cloud.pso.beam.generator.thrift.CompoundEvent";
    var avroSchema = PrepareBQIngestion.retrieveAvroSchema(className);
    var beamSchema = PrepareBQIngestion.retrieveRowSchema(className);
    var thriftClass = PrepareBQIngestion.retrieveThriftClass(className);
    var random = new Random();

    var compoundEvent = new CompoundEvent();
    compoundEvent.setUuid(UUID.randomUUID().toString());
    compoundEvent.setClientEpoch(Instant.now().toEpochMilli());
    compoundEvent.setData("some specific data for this compound stuff");
    compoundEvent.setDestination("a destination");
    compoundEvent.setExternalId(random.nextLong());
    compoundEvent.setName("a classy name");
    compoundEvent.setSource("some weird source");
    compoundEvent.setEvents(
            IntStream.range(0, random.nextInt(10))
                    .mapToObj(i -> {
                      var event = new SimpleEvent();
                      event.setDescription("a description");
                      event.setLocation("another dimension");
                      event.setStartup(Instant.now().toEpochMilli());
                      return event;
                    })
                    .collect(Collectors.toSet()));
    compoundEvent.setCarriers(IntStream.range(0, random.nextInt(20))
            .mapToObj(i -> {
              var carrier = new Carrier();
              carrier.setId(UUID.randomUUID().toString());
              carrier.setValue(random.nextLong());
              return carrier;
            })
            .collect(Collectors.toList()));

    var thriftData = getBytesFromThriftObject(compoundEvent);
    var transport = new CommonTransport("someid", new HashMap<>(), thriftData);

    var row = PrepareBQIngestion.TransformTransportToRow.retrieveRowFromTransport(
            transport, thriftClass, beamSchema, avroSchema);

    assertNotNull(row);
  }

  static byte[] getBytesFromThriftObject(TBase<?, ?> instance) {
    try {
      TSerializer serializer = null;
      try {
        serializer = new TSerializer(new TBinaryProtocol.Factory());
      } catch (Exception e) {
        throw new RuntimeException("Error while creating a TSerializer.", e);
      }
      return serializer.serialize(instance);
    } catch (TException ex) {
      throw new RuntimeException("Can't serialize instance.", ex);
    }
  }

}
