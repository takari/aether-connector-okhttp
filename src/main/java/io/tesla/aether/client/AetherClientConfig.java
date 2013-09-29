package io.tesla.aether.client;

import java.util.Map;

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
  
  public Map<String, String> getHeaders() {
    return headers;
  }
  
  public void setHeaders(Map<String, String> headers) {
    this.headers = headers;
  }
}
