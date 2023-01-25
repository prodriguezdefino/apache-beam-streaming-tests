package com.google.cloud.pso.beam.generator.transport;

import com.google.cloud.pso.beam.common.transport.EventTransport;
import java.util.Map;
import java.util.UUID;

/**
 * A transport object for the streaming data generator.
 */
public class GeneratorTransport implements EventTransport {

  private final String id;
  private final Map<String, String> headers;
  private final byte[] data;

  public GeneratorTransport(byte[] data, Map<String, String> headers) {
    this.id = UUID.randomUUID().toString();
    this.headers = headers;
    this.data = data;
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public Map<String, String> getHeaders() {
    return headers;
  }

  @Override
  public byte[] getData() {
    return data;
  }

}
