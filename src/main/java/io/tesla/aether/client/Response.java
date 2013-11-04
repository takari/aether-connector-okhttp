/**
 * Copyright (c) 2012 to original author or authors
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.tesla.aether.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

public interface Response {  
  int getStatusCode() throws IOException;
  String getStatusMessage() throws IOException;
  String getHeader(String name);
  Map<String, List<String>> getHeaders();
  InputStream getInputStream() throws IOException;
  OutputStream getOutputStream() throws IOException;
}
