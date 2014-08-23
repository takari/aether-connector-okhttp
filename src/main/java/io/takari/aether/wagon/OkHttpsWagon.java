/**
 * Copyright (c) 2012 to original author or authors
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.aether.wagon;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import javax.inject.Named;

import org.apache.maven.wagon.ConnectionException;
import org.apache.maven.wagon.Wagon;
import org.apache.maven.wagon.authentication.AuthenticationException;
import org.apache.maven.wagon.proxy.ProxyInfo;
import org.codehaus.plexus.component.annotations.Component;

/**
 * LightweightHttpsWagon, using JDK's HttpURLConnection.
 *
 * @author <a href="mailto:joakim@erdfelt.com">Joakim Erdfelt</a>
 *
 * 
 * @plexus.component role="org.apache.maven.wagon.Wagon" 
 *   role-hint="https"
 *   instantiation-strategy="per-lookup"
 */
@Named("https")
@Component(role = Wagon.class, hint = "https")
public class OkHttpsWagon extends OkHttpWagon {
  private String previousHttpsProxyHost;

  private String previousHttpsProxyPort;

  private String previousHttpsProxyExclusions;

  public OkHttpsWagon() {
    super();
  }

  public void openConnection() throws ConnectionException, AuthenticationException {
    previousHttpsProxyHost = System.getProperty("https.proxyHost");
    previousHttpsProxyPort = System.getProperty("https.proxyPort");
    previousHttpsProxyExclusions = System.getProperty("https.nonProxyHosts");

    final ProxyInfo proxyInfo = getProxyInfo("https", getRepository().getHost());
    if (proxyInfo != null) {
      setSystemProperty("https.proxyHost", proxyInfo.getHost());
      setSystemProperty("https.proxyPort", String.valueOf(proxyInfo.getPort()));
      setSystemProperty("https.nonProxyHosts", proxyInfo.getNonProxyHosts());
    } else {
      setSystemProperty("https.proxyHost", null);
      setSystemProperty("https.proxyPort", null);
    }

    super.openConnection();
  }

  public void closeConnection() throws ConnectionException {
    super.closeConnection();

    setSystemProperty("https.proxyHost", previousHttpsProxyHost);
    setSystemProperty("https.proxyPort", previousHttpsProxyPort);
    setSystemProperty("https.nonProxyHosts", previousHttpsProxyExclusions);
  }
}
