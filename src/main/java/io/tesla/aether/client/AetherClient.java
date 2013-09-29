package io.tesla.aether.client;

import java.io.IOException;
import java.util.Map;

import javax.net.ssl.SSLSocketFactory;

public interface AetherClient {
  Response head(String uri) throws IOException;
  Response get(String uri) throws IOException;
  Response get(String uri, Map<String,String> requestHeaders) throws IOException;
  Response put(String uri) throws IOException;
  void close() throws IOException;
  void setSSLSocketFactory(SSLSocketFactory sslSocketFactory);
}
