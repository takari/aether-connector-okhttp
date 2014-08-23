/**
 * Copyright (c) 2012 to original author or authors
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.aether.connector.test.suite;

import io.tesla.webserver.WebServer;

import org.eclipse.jetty.util.security.Constraint;

public class GetAuthSslCertTest extends GetAuthTest {

  @Override
  protected void configureTest() throws Exception {
    enableSsl();
  }
  
  @Override
  protected void configureServer(WebServer server) {
    addBehaviour("/repo", generate, expect, provide);    
    server.addAuthentication("/*", Constraint.__CERT_AUTH2);
    server.addUser("user", "password");    
  }
}
