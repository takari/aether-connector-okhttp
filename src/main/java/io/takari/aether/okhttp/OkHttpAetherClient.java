/**
 * Copyright (c) 2012 to original author or authors All rights reserved. This program and the
 * accompanying materials are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.aether.okhttp;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Proxy.Type;
import java.net.SocketAddress;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import io.takari.aether.client.AetherClient;
import io.takari.aether.client.AetherClientAuthentication;
import io.takari.aether.client.AetherClientConfig;
import io.takari.aether.client.AetherClientProxy;
import io.takari.aether.client.Response;
import io.takari.aether.client.RetryableSource;
import okhttp3.Authenticator;
import okhttp3.Credentials;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Route;
import okhttp3.internal.tls.OkHostnameVerifier;
import okio.BufferedSink;

public class OkHttpAetherClient implements AetherClient {

  private final Authenticator PROXY_AUTH =  new Authenticator() {

    @Override
    public Request authenticate(Route route, okhttp3.Response response) throws IOException {
      if (response.code() == HttpURLConnection.HTTP_PROXY_AUTH) {
        return authenticateProxy(route.proxy(), response);
      }
      return null;
    }

    private Request authenticateProxy(Proxy proxy, okhttp3.Response response)
        throws IOException {
        Request req = response.request();
        if(req.header("Proxy-Authorization") == null && config.getProxy() != null && 
            config.getProxy().getAuthentication() != null) {
          String value = toHeaderValue(config.getProxy().getAuthentication());
          
          boolean tunneled = req.isHttps() && proxy.type() == Type.HTTP;
          if(!tunneled) {
            // start including proxy-auth on each request
            headers.put("Proxy-Authorization", value);
          }
          
          return req.newBuilder().header("Proxy-Authorization", value).build();
        }
        return null;
    }
  };

  private final Map<String, String> headers;
  private final AetherClientConfig config;
  private final OkHttpClient httpClient;

  public OkHttpAetherClient(AetherClientConfig config) {
    this.config = config;

    // headers are modified during http auth handshake
    // make a copy to avoid cross-talk among client instances
    headers = new HashMap<String, String>();
    if (config.getHeaders() != null) {
      headers.putAll(config.getHeaders());
    }

    //
    // If the User-Agent has been overriden in the headers then we will use that
    //
    if (!headers.containsKey("User-Agent")) {
      headers.put("User-Agent", config.getUserAgent());
    }

    OkHttpClient.Builder builder = new OkHttpClient.Builder() //
        .proxy(getProxy(config.getProxy())) //
        .hostnameVerifier(OkHostnameVerifier.INSTANCE) //
        // TODO looks odd, why do I need to use the same Authenticator twice?
        .authenticator(PROXY_AUTH) // see #authenticate below
        .proxyAuthenticator(PROXY_AUTH) //
        .connectTimeout(config.getConnectionTimeout(), TimeUnit.MILLISECONDS) //
        .readTimeout(config.getRequestTimeout(), TimeUnit.MILLISECONDS);
    if (config.getSslSocketFactory() != null) {
    	X509TrustManager trustManager = getX509TrustManager();
    	if (trustManager != null) {
    		builder.sslSocketFactory(config.getSslSocketFactory(), trustManager);
    	} else {
    		builder.sslSocketFactory(config.getSslSocketFactory());
    	}
    }
    if (config.getHostnameVerifier() != null) {
      builder.hostnameVerifier(config.getHostnameVerifier());
    }
    this.httpClient = builder.build();
  }

  @Override
  public Response head(String uri) throws IOException {
    Response response;
    do {
      response = execute(httpClient, builder(uri, null).head().build());
    } while (response == null);
    return response;
  }
  
  //
  // Try and get an X509TrustManager using the JVM default algorithms and types.  If this fails, return null, so we fall back to OkHTTP's reflection solution.
  //
	private X509TrustManager getX509TrustManager() {
		try {
			TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
			KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
			trustManagerFactory.init(keyStore);
			
			for (TrustManager trustManager : trustManagerFactory.getTrustManagers()) {
				if (trustManager instanceof X509TrustManager) {
					return (X509TrustManager) trustManager;
				}
			}
			return null;
		} catch (NoSuchAlgorithmException e) {
			return null;
		} catch (KeyStoreException e) {
			return null;
		}
	}

  @Override
  public Response get(String uri) throws IOException {
    Response response;
    do {
      response = execute(httpClient, builder(uri, null).get().build());
    } while (response == null);
    return response;
  }

  @Override
  public Response get(String uri, Map<String, String> requestHeaders) throws IOException {
    Response response;
    do {
      response = execute(httpClient, builder(uri, requestHeaders).get().build());
    } while (response == null);
    return response;
  }

  @Override
  // i need the response
  public Response put(String uri, final RetryableSource source) throws IOException {
    Response response;
    do {
      // disable response caching
      // connection.addRequestProperty("Cache-Control", "no-cache") may work too
      OkHttpClient httpClient = this.httpClient.newBuilder().cache(null).build();

      final MediaType mediaType = MediaType.parse("application/octet-stream");
      final RequestBody body = new RequestBody() {

        @Override
        public void writeTo(BufferedSink sink) throws IOException {
          source.copyTo(sink.outputStream());
        }

        @Override
        public MediaType contentType() {
          return mediaType;
        }
        
        @Override
        public long contentLength() throws IOException {
          return source.length();
        }
      };

      Request.Builder builder = builder(uri, null).put(body);
      
      if (source.length() > 0) {
        builder.header("Content-Length", String.valueOf(source.length()));
      }

      response = execute(httpClient, builder.build());
    } while (response == null);
    return response;
  }

  private Response execute(OkHttpClient httpClient, Request request) throws IOException {
    okhttp3.Response response = httpClient.newCall(request).execute();
    switch (response.code()) {
      case HttpURLConnection.HTTP_UNAUTHORIZED:
        if (config.getAuthentication() != null && !headers.containsKey("Authorization")) {
          headers.put("Authorization", toHeaderValue(config.getAuthentication()));
          response.body().close(); // help connection pool reclaim the connection
          return null; // retry
        }
        break;
    }
    return new ResponseAdapter(response); // do not retry
  }

  private String toHeaderValue(AetherClientAuthentication auth) {
    return Credentials.basic(auth.getUsername(), auth.getPassword());
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

  private Request.Builder builder(String uri, Map<String, String> requestHeaders)
      throws IOException {
    Request.Builder builder = new Request.Builder().url(uri);

    // Headers
    if (headers != null) {
      for (String headerName : headers.keySet()) {
        builder.addHeader(headerName, headers.get(headerName));
      }
    }

    if (requestHeaders != null) {
      for (String headerName : requestHeaders.keySet()) {
        builder.addHeader(headerName, requestHeaders.get(headerName));
      }
    }

    return builder;
  }

  class ResponseAdapter implements Response {

    okhttp3.Response conn;

    ResponseAdapter(okhttp3.Response conn) {
      this.conn = conn;
    }

    @Override
    public int getStatusCode() throws IOException {
      return conn.code();
    }

    @Override
    public String getStatusMessage() throws IOException {
      return conn.message();
    }

    @Override
    public String getHeader(String name) {
      return conn.header(name);
    }

    @Override
    public Map<String, List<String>> getHeaders() {
      Map<String, List<String>> headers = new HashMap<String, List<String>>();
      for (String header : conn.headers().names()) {
        headers.put(header, conn.headers(header));
      }
      return headers;
    }

    @Override
    public InputStream getInputStream() throws IOException {
      return conn.body().byteStream();
    }

    @Override
    public void close() {
      if (conn != null) {
        conn.close();
      }
    }
  }

  @Override
  public void close() {}
}
