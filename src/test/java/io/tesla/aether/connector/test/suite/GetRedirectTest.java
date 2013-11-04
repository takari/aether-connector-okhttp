/**
 * Copyright (c) 2012 to original author or authors
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.tesla.aether.connector.test.suite;


import io.tesla.aether.connector.test.suite.server.Redirect;
import io.tesla.webserver.WebServer;

import org.eclipse.aether.repository.RemoteRepository;

public class GetRedirectTest extends GetTest {

  @Override
  protected RemoteRepository repository() {
    return new RemoteRepository.Builder(super.repository()).setUrl(url("redirect")).build();
  }

  @Override
  public void configureServer(WebServer server) {
    addBehaviour( "/repo", generate, expect, provide );    
    addBehaviour("/redirect/*", new Redirect("^", "/repo"));
  }
}
