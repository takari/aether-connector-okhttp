/**
 * Copyright (c) 2012 to original author or authors
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
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
