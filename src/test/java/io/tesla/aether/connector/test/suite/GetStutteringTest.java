/*******************************************************************************
 * Copyright (c) 2010, 2011 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package io.tesla.aether.connector.test.suite;


import io.tesla.webserver.WebServer;

public class GetStutteringTest extends GetTest {

  @Override
  public void configureServer(WebServer provider) {
    super.configureServer(provider);
    provide.setLatency(100);
  }

}
