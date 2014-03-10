/**
 * Copyright (c) 2012 to original author or authors
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.tesla.aether.connector.test.suite;

import io.tesla.webserver.WebServer;

import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.util.repository.AuthenticationBuilder;

public class PutAuthWithNonAsciiCredentialsTest extends PutAuthTest {

  @Override
  public void configureServer(WebServer provider) {
    super.configureServer(provider);
    provider.addUser("user-non-ascii", "\u00E4\u00DF");
  }

  @Override
  public RemoteRepository remoteRepository() {
    Authentication auth = new AuthenticationBuilder().addUsername("user-non-ascii").addPassword("\u00E4\u00DF").build();
    return new RemoteRepository.Builder("async-test-repo", "default", url("repo")).setAuthentication(auth).build();
  }
}
