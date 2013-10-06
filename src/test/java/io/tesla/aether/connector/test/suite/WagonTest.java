/*******************************************************************************
 * Copyright (c) 2013 Igor Fedorenko
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Igor Fedorenko - initial API and implementation
 *******************************************************************************/
package io.tesla.aether.connector.test.suite;

import io.tesla.aether.wagon.OkHttpsWagon;

import java.io.File;

import org.apache.maven.wagon.repository.Repository;

public class WagonTest extends AetherTestCase {

  public void testNoHeaders() throws Exception {
    addDelivery("path", "contents");

    OkHttpsWagon wagon = new OkHttpsWagon();
    wagon.connect(new Repository("test", "http://localhost:" + provider().getPort() + "/repo"));
    wagon.openConnection();
    File dest = File.createTempFile("okhttp", ".tmp");
    wagon.get("path", dest);
  }

}
