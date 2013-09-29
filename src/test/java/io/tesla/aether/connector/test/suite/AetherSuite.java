/*******************************************************************************
 * Copyright (c) 2010, 2012 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package io.tesla.aether.connector.test.suite;

import junit.framework.TestSuite;

import org.eclipse.sisu.containers.InjectedTestCase;

public class AetherSuite extends InjectedTestCase {
  
  public static TestSuite suite() {
    
    TestSuite suite = new TestSuite();
    suite.addTestSuite(AetherMockWebserverConnectorTest.class);
    suite.addTestSuite(AetherConnectorTest.class);
    suite.addTestSuite(TimeoutTest.class);
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
    //TODO suite.addTestSuite(GetProxySslTest.class);    
    suite.addTestSuite(GetProxyAuthTest.class);
    //TODO SSL and Proxies aren't setup correctly
    //TODO suite.addTestSuite(GetProxyAuthSslTest.class);
    suite.addTestSuite(GetAuthWithNonAsciiCredentialsTest.class);
    suite.addTestSuite(GetResumeTest.class);
    // Uncomment when Jetty bug is fixed with handling Range requests in the ResourceHandler
    //suite.addTestSuite(ResumeWithClientFailureTest.class);    
    suite.addTestSuite(GetStutteringTest.class);
    //TODO another Jetty flush bug
    //suite.addTestSuite(GetDownloadWhoseSizeExceedsMaxHeapSizeTest.class);
    //
    // PUT
    //
    suite.addTestSuite(PutTest.class);
    suite.addTestSuite(PutSslTest.class);
    suite.addTestSuite(PutAuthTest.class);
    suite.addTestSuite(PutAuthSslTest.class);
    suite.addTestSuite(PutProxyTest.class);
    suite.addTestSuite(PutAuthWithNonAsciiCredentialsTest.class);
    
    return suite;
  }
    
  protected boolean connectorSupportsDav() {
    return false;
  }
  
  protected boolean connectorSupportsSsh() {
    return false;
  }
}
