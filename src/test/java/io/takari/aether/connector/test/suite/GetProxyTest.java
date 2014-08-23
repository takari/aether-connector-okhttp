/**
 * Copyright (c) 2012 to original author or authors
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.aether.connector.test.suite;

// The HTTP request is sent from Client to port 8080 of the Proxy Server. The Proxy Server then 
// originates a new HTTP request to the destination site. The proxy, depending on the configuration, 
// will often add a "X-Forwarded-For" header to the HTTP request. The log files on the destination 
// web site will show the proxy's IP address, but may or may not be configured to 
// log the "X-Forwarded-For" address.

// Client <-----------> Proxy <-----------> Server

public class GetProxyTest extends GetTest {
  
  @Override
  protected void configureTest() throws Exception {
    enableProxy();
  }
}
