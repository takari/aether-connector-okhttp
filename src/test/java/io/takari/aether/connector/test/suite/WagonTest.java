/**
 * Copyright (c) 2012 to original author or authors
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.aether.connector.test.suite;

import io.takari.aether.connector.test.suite.server.ErrorBehaviour;
import io.takari.aether.wagon.OkHttpsWagon;
import io.tesla.webserver.WebServer;

import java.io.File;

import org.apache.maven.wagon.ResourceDoesNotExistException;
import org.apache.maven.wagon.repository.Repository;
import org.eclipse.aether.internal.test.util.TestFileUtils;

public class WagonTest extends AetherTestCase {

  @Override
  public void configureServer(WebServer provider) {
    super.configureServer(provider);
    addBehaviour("/notfound/*", new ErrorBehaviour(404, "Not Found"));
  }

  public void testNoHeaders() throws Exception {
    addDelivery("path", "contents");

    OkHttpsWagon wagon = new OkHttpsWagon();
    wagon.connect(new Repository("test", "http://localhost:" + provider().getPort() + "/repo"));

    File f = TestFileUtils.createTempFile("");
    wagon.get("path", f);
    wagon.disconnect();
    
    GetTest.assertContent("contents", f);
  }

  public void testResourceNotFound() throws Exception {
      OkHttpsWagon wagon = new OkHttpsWagon();
      wagon.connect(new Repository("test", "http://localhost:" + provider().getPort() + "/notfound"));

      File f = TestFileUtils.createTempFile("");
      boolean exceptionThrown = false;
      try {
        wagon.get("path", f);
      } catch (ResourceDoesNotExistException e) {
        exceptionThrown = true;
      } finally {
        wagon.disconnect();
      }
      
      GetTest.assertContent("", f);
      assertTrue(exceptionThrown);
    }
}
