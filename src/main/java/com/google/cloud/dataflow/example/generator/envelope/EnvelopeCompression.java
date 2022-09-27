package com.google.cloud.dataflow.example.generator.envelope;

import com.google.cloud.dataflow.example.Element;
import com.google.cloud.dataflow.example.Envelope;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

import com.google.common.annotations.VisibleForTesting;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.apache.commons.compress.utils.IOUtils;

import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TIOStreamTransport;
import org.apache.thrift.transport.TMemoryInputTransport;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TZlibTransport;

/**
 * Util functions for compression and uncompression of thrift records using zlib.
 */
public class EnvelopeCompression {

  public static Element constructThriftRecord(byte[] data, Map<String, String> headers) {
    Element element = new Element();
    element.setHeaders(headers);
    element.setData(ByteBuffer.wrap(data));
    return element;
  }

  public static Envelope constructThriftBatchRecord(List<Element> element, Map<String, String> headers) {
    Envelope envelope = new Envelope();
    envelope.setHeaders(headers);
    envelope.setRecords(element);
    return envelope;
  }

  @VisibleForTesting
  public static byte[] compressBatchRecords(Envelope envelope, int compressionLevel) throws TException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    TTransport transport = new TIOStreamTransport(baos);
    transport = new TZlibTransport(transport, compressionLevel);
    TProtocol protocol = new TBinaryProtocol.Factory().getProtocol(transport);
    baos.reset();
    envelope.write(protocol);
    transport.flush();
    return baos.toByteArray();
  }

  @VisibleForTesting
  public static Envelope decompressBatchRecords(byte[] data) throws TException {
    TMemoryInputTransport tMemoryInputTransport = new TMemoryInputTransport();
    TTransport transport = new TZlibTransport(tMemoryInputTransport);
    TProtocol protocol = new TBinaryProtocol.Factory().getProtocol(transport);
    tMemoryInputTransport.reset(data, 0, data.length);
    Envelope records = new Envelope();
    records.read(protocol);
    tMemoryInputTransport.clear();
    protocol.reset();
    return records;
  }

  public static String compressString(String srcTxt)
          throws IOException {
    ByteArrayOutputStream rstBao = new ByteArrayOutputStream();
    GZIPOutputStream zos = new GZIPOutputStream(rstBao);
    zos.write(srcTxt.getBytes());
    IOUtils.closeQuietly(zos);
    byte[] bytes = rstBao.toByteArray();
    return Base64.getEncoder().encodeToString(bytes);
  }

  public static String uncompressString(String zippedBase64Str)
          throws IOException {
    String result = null;

    byte[] bytes = Base64.getDecoder().decode(zippedBase64Str);
    GZIPInputStream zi = null;
    try {
      zi = new GZIPInputStream(new ByteArrayInputStream(bytes));
      result = new String(IOUtils.toByteArray(zi));
    } finally {
      IOUtils.closeQuietly(zi);
    }
    return result;
  }
}
