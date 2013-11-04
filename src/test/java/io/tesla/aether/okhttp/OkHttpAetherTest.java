/**
 * Copyright (c) 2012 to original author or authors
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.tesla.aether.okhttp;

import io.tesla.aether.connector.test.suite.AetherConnectorTest;
import io.tesla.aether.connector.test.suite.AetherMockWebserverConnectorTest;
import io.tesla.aether.connector.test.suite.GetAuthSslTest;
import io.tesla.aether.connector.test.suite.GetAuthTest;
import io.tesla.aether.connector.test.suite.GetAuthWithNonAsciiCredentialsTest;
import io.tesla.aether.connector.test.suite.GetDownloadWhoseSizeExceedsMaxHeapSizeTest;
import io.tesla.aether.connector.test.suite.GetProxyAuthTest;
import io.tesla.aether.connector.test.suite.GetProxyTest;
import io.tesla.aether.connector.test.suite.GetRedirectTest;
import io.tesla.aether.connector.test.suite.GetResumeTest;
import io.tesla.aether.connector.test.suite.GetSslTest;
import io.tesla.aether.connector.test.suite.GetStutteringTest;
import io.tesla.aether.connector.test.suite.GetTest;
import io.tesla.aether.connector.test.suite.PutAuthSslTest;
import io.tesla.aether.connector.test.suite.PutAuthTest;
import io.tesla.aether.connector.test.suite.PutAuthWithNonAsciiCredentialsTest;
import io.tesla.aether.connector.test.suite.PutProxyTest;
import io.tesla.aether.connector.test.suite.PutSslTest;
import io.tesla.aether.connector.test.suite.PutTest;
import io.tesla.aether.connector.test.suite.ResumeWithClientFailureTest;
import io.tesla.aether.connector.test.suite.TimeoutTest;
import junit.framework.TestSuite;

import org.eclipse.sisu.containers.InjectedTestCase;

public class OkHttpAetherTest extends InjectedTestCase {
  
  public static TestSuite suite() {
    
    TestSuite suite = new TestSuite();
    suite.addTestSuite(AetherMockWebserverConnectorTest.class);
    suite.addTestSuite(AetherConnectorTest.class);
    //
    // GET
    //
    suite.addTestSuite(GetTest.class);
    suite.addTestSuite(GetSslTest.class);
    suite.addTestSuite(GetAuthTest.class);
    suite.addTestSuite(GetAuthSslTest.class);
    //TODO This does seem to have ever been activated and there's no facility in Maven for using certs right now
    //TODO suite.addTestSuite(GetAuthSslCertTest.class);
    suite.addTestSuite(GetRedirectTest.class);
    suite.addTestSuite(GetProxyTest.class);
    //TODO SSL and Proxies aren't setup correctly
    //suite.addTestSuite(GetProxySslTest.class);    
    suite.addTestSuite(GetProxyAuthTest.class);
    //TODO SSL and Proxies aren't setup correctly
    //TODO suite.addTestSuite(GetProxyAuthSslTest.class);
    suite.addTestSuite(GetAuthWithNonAsciiCredentialsTest.class);
    suite.addTestSuite(GetResumeTest.class);
    suite.addTestSuite(ResumeWithClientFailureTest.class);    
    suite.addTestSuite(GetStutteringTest.class);
    //
    // PUT
    //
    suite.addTestSuite(PutTest.class);
    suite.addTestSuite(PutSslTest.class);
    suite.addTestSuite(PutAuthTest.class);
    suite.addTestSuite(PutAuthSslTest.class);
    suite.addTestSuite(PutProxyTest.class);
    suite.addTestSuite(PutAuthWithNonAsciiCredentialsTest.class);
    //
    // DAV
    //
    //TODO probably be easy enough to implement DAV support
    //suite.addTestSuite(GetDavUrlTest.class);
    //suite.addTestSuite(GetDavUrlTest.class);
    
    // Timeout
    suite.addTestSuite(TimeoutTest.class);
    
    // This will not run on Hudson
    suite.addTestSuite(GetDownloadWhoseSizeExceedsMaxHeapSizeTest.class);

    return suite;
  }
    
  protected boolean connectorSupportsDav() {
    return false;
  }
  
  protected boolean connectorSupportsSsh() {
    return false;
  }
}
