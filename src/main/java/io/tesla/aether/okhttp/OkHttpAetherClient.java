/**
 * Copyright (c) 2012 to original author or authors All rights reserved. This program and the
 * accompanying materials are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.tesla.aether.okhttp;

import io.tesla.aether.client.AetherClient;
import io.tesla.aether.client.AetherClientAuthentication;
import io.tesla.aether.client.AetherClientConfig;
import io.tesla.aether.client.AetherClientProxy;
import io.tesla.aether.client.Response;
import io.tesla.aether.client.RetryableSource;
import io.tesla.aether.okhttp.ssl.SslContextFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpRetryException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.ProtocolException;
import java.net.SocketAddress;
import java.net.URL;
import java.util.List;
import java.util.Map;

import javax.net.ssl.SSLSocketFactory;

import com.squareup.okhttp.OkAuthenticator.Credential;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.internal.tls.OkHostnameVerifier;

public class OkHttpAetherClient implements AetherClient {

  private Map<String, String> headers;
  private AetherClientConfig config;
  private OkHttpClient httpClient;
  private SSLSocketFactory sslSocketFactory;

  public OkHttpAetherClient(AetherClientConfig config) {
    this.config = config;

    headers = config.getHeaders();

    //
    // If the User-Agent has been overriden in the headers then we will use that
    //
    if (headers != null && !headers.containsKey("User-Agent")) {
      headers.put("User-Agent", config.getUserAgent());
    }

    httpClient = new OkHttpClient();
    httpClient.setProxy(getProxy(config.getProxy()));
    httpClient.setHostnameVerifier(OkHostnameVerifier.INSTANCE);

    if (config.getSslSocketFactory() != null) {
      this.sslSocketFactory = config.getSslSocketFactory();
    }
  }

  @Override
  public Response head(String uri) throws IOException {
    HttpURLConnection ohc;
    do {
      ohc = httpClient.open(new URL(uri));
      ohc.setRequestMethod("HEAD");
    } while (authenticate(ohc));
    return new ResponseAdapter(ohc);
  }

  @Override
  public Response get(String uri) throws IOException {
    HttpURLConnection ohc;
    do {
      ohc = getConnection(uri, null);
      ohc.setRequestMethod("GET");
    } while (authenticate(ohc));
    return new ResponseAdapter(ohc);
  }

  @Override
  public Response get(String uri, Map<String, String> requestHeaders) throws IOException {
    HttpURLConnection ohc;
    do {
      ohc = getConnection(uri, requestHeaders);
      ohc.setRequestMethod("GET");
    } while (authenticate(ohc));
    return new ResponseAdapter(ohc);
  }

  @Override
  // i need the response
  public Response put(String uri, RetryableSource source) throws IOException {
    HttpURLConnection ohc;
    do {
      ohc = getConnection(uri, null);
      ohc.setUseCaches(false);
      ohc.setRequestProperty("Content-Type", "application/octet-stream");
      ohc.setRequestMethod("PUT");
      ohc.setDoOutput(true);

      if (source.length()>0) {
        // setFixedLengthStreamingMode(long) was introduced in java7
        // use setFixedLengthStreamingMode(int) to maintain compatibility with java6
        ohc.setFixedLengthStreamingMode((int)source.length());
      }
      // TODO investigate if we want/need to use chunked upload
      // ohc.setChunkedStreamingMode();

      OutputStream os = ohc.getOutputStream();
      try {
        source.copyTo(os);
      } finally {
        os.close();
      }
    } while (authenticate(ohc));
    return new ResponseAdapter(ohc);
  }

  private boolean authenticate(HttpURLConnection ohc) throws IOException {
    int status;
    try {
      status = ohc.getResponseCode();
    } catch (HttpRetryException e) {
      status = e.responseCode();
    }
    switch (status) {
      case HttpURLConnection.HTTP_PROXY_AUTH:
        if (config.getProxy() == null) {
          throw new ProtocolException("Received HTTP_PROXY_AUTH (407) code while not using proxy");
        }
        if (config.getProxy().getAuthentication() != null
            && !headers.containsKey("Proxy-Authorization")) {
          headers.put("Proxy-Authorization", toHeaderValue(config.getProxy().getAuthentication()));
          return true; // retry
        }
        break;
      case HttpURLConnection.HTTP_UNAUTHORIZED:
        if (config.getAuthentication() != null && !headers.containsKey("Authorization")) {
          headers.put("Authorization", toHeaderValue(config.getAuthentication()));
          return true; // retry
        }
        break;
    }
    return false; // do not retry
  }

  private String toHeaderValue(AetherClientAuthentication auth) {
    return Credential.basic(auth.getUsername(), auth.getPassword()).getHeaderValue();
  }

  public void setSSLSocketFactory(SSLSocketFactory sslSocketFactory) {
    this.sslSocketFactory = sslSocketFactory;
  }

  private java.net.Proxy getProxy(AetherClientProxy proxy) {
    java.net.Proxy ohp;
    if (proxy == null) {
      ohp = java.net.Proxy.NO_PROXY;
    } else {
      SocketAddress addr = new InetSocketAddress(proxy.getHost(), proxy.getPort());
      ohp = new java.net.Proxy(java.net.Proxy.Type.HTTP, addr);
    }
    return ohp;
  }

  private void checkForSslSystemProperties() {

    if (sslSocketFactory == null) {
      String keyStorePath = System.getProperty("javax.net.ssl.keyStore");
      String keyStorePassword = System.getProperty("javax.net.ssl.keyStorePassword");
      String keyStoreType = System.getProperty("javax.net.ssl.keyStoreType");
      String trustStorePath = System.getProperty("javax.net.ssl.trustStore");
      String trustStorePassword = System.getProperty("javax.net.ssl.trustStorePassword");
      String trustStoreType = System.getProperty("javax.net.ssl.trustStoreType");

      SslContextFactory scf = new SslContextFactory();
      if (keyStorePath != null && keyStorePassword != null) {
        scf.setKeyStorePath(keyStorePath);
        scf.setKeyStorePassword(keyStorePassword);
        scf.setKeyStoreType(keyStoreType);
        if (trustStorePath != null && trustStorePassword != null) {
          scf.setTrustStore(trustStorePath);
          scf.setTrustStorePassword(trustStorePassword);
          scf.setTrustStoreType(trustStoreType);
        }
        try {
          sslSocketFactory = scf.getSslContext().getSocketFactory();
        } catch (Exception e) {
          // do nothing
        }
      }
    }
  }

  private HttpURLConnection getConnection(String uri, Map<String, String> requestHeaders)
      throws IOException {

    checkForSslSystemProperties();

    if (sslSocketFactory != null) {
      httpClient.setSslSocketFactory(sslSocketFactory);
    }

    HttpURLConnection ohc = httpClient.open(new URL(uri));

    // Headers
    if (headers != null) {
      for (String headerName : headers.keySet()) {
        ohc.addRequestProperty(headerName, headers.get(headerName));
      }
    }

    if (requestHeaders != null) {
      for (String headerName : requestHeaders.keySet()) {
        ohc.addRequestProperty(headerName, requestHeaders.get(headerName));
      }
    }

    // Timeouts
    ohc.setConnectTimeout(config.getConnectionTimeout());
    ohc.setReadTimeout(config.getRequestTimeout());

    return ohc;
  }

  class ResponseAdapter implements Response {

    HttpURLConnection conn;

    ResponseAdapter(HttpURLConnection conn) {
      this.conn = conn;
    }

    @Override
    public int getStatusCode() throws IOException {
      return conn.getResponseCode();
    }

    @Override
    public String getStatusMessage() throws IOException {
      return conn.getResponseMessage();
    }

    @Override
    public String getHeader(String name) {
      return conn.getHeaderField(name);
    }

    @Override
    public Map<String, List<String>> getHeaders() {
      return conn.getHeaderFields();
    }

    @Override
    public InputStream getInputStream() throws IOException {
      return conn.getInputStream();
    }
  }

  @Override
  public void close() {}
}
