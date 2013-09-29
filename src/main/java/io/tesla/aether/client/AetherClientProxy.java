package io.tesla.aether.client;

public class AetherClientProxy {
  
  private String host;
  private int port;
  
  private AetherClientAuthentication authentication;
    
  public String getHost() {
    return host;
  }
  
  public void setHost(String host) {
    this.host = host;
  }
  
  public int getPort() {
    return port;
  }
  
  public void setPort(int port) {
    this.port = port;
  }
  
  public AetherClientAuthentication getAuthentication() {
    return authentication;
  }
  
  public void setAuthentication(AetherClientAuthentication authentication) {
    this.authentication = authentication;
  }
}
