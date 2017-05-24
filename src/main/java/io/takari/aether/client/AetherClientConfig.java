/**
 * Copyright (c) 2012 to original author or authors
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.aether.client;

import java.util.Map;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

public class AetherClientConfig {
  
  private String userAgent;
  private int connectionTimeout;
  private int requestTimeout;
  private AetherClientProxy proxy;
  private AetherClientAuthentication authentication;
  private Map<String,String> headers;
  
  public String getUserAgent() {
    return userAgent;
  }
  
  public void setUserAgent(String userAgent) {
    this.userAgent = userAgent;
  }
  
  public int getConnectionTimeout() {
    return connectionTimeout;
  }
  
  public void setConnectionTimeout(int connectionTimeout) {
    this.connectionTimeout = connectionTimeout;
  }
  
  public int getRequestTimeout() {
    return requestTimeout;  
  }
  
  public void setRequestTimeout(int requestTimeout) {
    this.requestTimeout = requestTimeout;
  }
  
  public AetherClientProxy getProxy() {
    return proxy;  
  }
  
  public void setProxy(AetherClientProxy proxy) {
    this.proxy = proxy;
  }
  
  public AetherClientAuthentication getAuthentication() {
    return authentication;
  }
  
  public void setAuthentication(AetherClientAuthentication authentication) {
    this.authentication = authentication;
  }
  
  public Map<String,String> getHeaders() {
    return headers;
  }
  
  public void setHeaders(Map<String,String> headers) {
    this.headers = headers;
  }

  //
  // for test purposes
  //

  private SSLSocketFactory sslSocketFactory;

  private X509TrustManager trustManager;

  private HostnameVerifier hostnameVerifier;
  
  public SSLSocketFactory getSslSocketFactory() {
    return sslSocketFactory;
  }

  public void setSslSocketFactory(SSLSocketFactory sslSocketFactory) {
    this.sslSocketFactory = sslSocketFactory;
  }

  public X509TrustManager getTrustManager() {
    return trustManager;
  }

  public void setTrustManager(X509TrustManager trustManager) {
    this.trustManager = trustManager;
  }

  public HostnameVerifier getHostnameVerifier() {
    return hostnameVerifier;
  }

  public void setHostnameVerifier(HostnameVerifier hostnameVerifier) {
    this.hostnameVerifier = hostnameVerifier;
  }
}
