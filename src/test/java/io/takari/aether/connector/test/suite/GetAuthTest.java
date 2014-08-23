/**
 * Copyright (c) 2012 to original author or authors
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.aether.connector.test.suite;

import io.tesla.webserver.WebServer;

public class GetAuthTest extends GetTest {

  @Override
  protected void configureTest() throws Exception {
    enableConnectingWithBasicAuth();
  }
  
  @Override
  protected void configureServer(WebServer server) {
    super.configureServer(server);
    server.addAuthentication("/*", "BASIC");
    server.addUser(USERNAME, PASSWORD);
  }
}
