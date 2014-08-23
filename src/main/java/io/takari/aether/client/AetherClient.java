/**
 * Copyright (c) 2012 to original author or authors
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.aether.client;

import java.io.IOException;
import java.util.Map;

import javax.net.ssl.SSLSocketFactory;

public interface AetherClient {
  Response head(String uri) throws IOException;
  Response get(String uri) throws IOException;
  Response get(String uri, Map<String,String> requestHeaders) throws IOException;
  Response put(String uri, RetryableSource source) throws IOException;
  void close() throws IOException;
  void setSSLSocketFactory(SSLSocketFactory sslSocketFactory);
}
