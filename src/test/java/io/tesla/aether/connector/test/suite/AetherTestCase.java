/**
 * Copyright (c) 2012 to original author or authors
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.tesla.aether.connector.test.suite;

import io.tesla.aether.connector.test.suite.server.Behaviour;
import io.tesla.aether.connector.test.suite.server.BehaviourServlet;
import io.tesla.aether.connector.test.suite.server.Expect;
import io.tesla.aether.connector.test.suite.server.Generate;
import io.tesla.aether.connector.test.suite.server.Provide;
import io.tesla.webserver.Jetty8WebServer;
import io.tesla.webserver.WebServer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.internal.test.util.TestFileUtils;
import org.eclipse.aether.metadata.DefaultMetadata;
import org.eclipse.aether.metadata.Metadata;
import org.junit.After;

//
//you really have 6 cases: http direct, https direct, http through http proxy, http through https proxy, https through http proxy, https through https proxy
//
//client to http://target
//client to https://target
//client to http://target via http://proxy
//client to http://target via https://proxy
//client to https://target via http://proxy
//client to https://target via https://proxy

public abstract class AetherTestCase extends AetherBaseTestCase {

  private Artifact artifact;
  protected Metadata metadata;
  protected Generate generate;
  protected Expect expect;
  protected Provide provide;
  protected WebServer server;
  protected WebServer proxyServer;

  //protected DefaultRepositorySystemSession session;

  /// Methods that need go to the super class
  
  protected String url(String path) {
    String url = url() + "/" + path;
    return url;
  }  
  
  private String url() {
    try {
      String hostname = InetAddress.getLocalHost().getHostName();
      if (enableProxy || enableProxyWithAuth) {
        URL url = new URL(((Jetty8WebServer) proxyServer).getProtocol(), hostname, proxyServer.getPort(), "");
        return url.toExternalForm();
      } else {
        URL url = new URL(((Jetty8WebServer) server).getProtocol(), hostname, server.getPort(), "");
        return url.toExternalForm();
      }
    } catch (MalformedURLException e) {
      throw new IllegalArgumentException("Provider was set up with wrong url", e);
    } catch (UnknownHostException e) {
      throw new IllegalArgumentException("Provider was set up with wrong url", e);
    }
  }

  protected int port() {
    return server.getPort();
  }

  public WebServer provider() {
    return server;
  }

  protected void enableSsl() throws Exception {
    enableSsl = true;
    server.enableSsl(sslContext);
  }

  protected void enableConnectingWithBasicAuth() {
    enableAuth = true;
  }

  protected void enableProxy() {
    enableProxy = true;
  }

  protected void enableProxyWithAuth() {
    enableProxyWithAuth = true;
  }
    
  /// end
  
  public void setUp() throws Exception {

    super.setUp();

    expect = new Expect();
    provide = new Provide();
    generate = new Generate();
    //
    // This is the content server
    //
    server = new Jetty8WebServer();
    configureTest();
    configureServer(server);
    server.start();

    if (enableProxy || enableProxyWithAuth) {
      proxyServer = new Jetty8WebServer();
      proxyServer.enableProxy();
      proxyServer.start();
    }

    artifact = new DefaultArtifact("gid", "aid", "classifier", "extension", "version", null);
    metadata = new DefaultMetadata("gid", "aid", "version", "maven-metadata.xml", Metadata.Nature.RELEASE_OR_SNAPSHOT, null);
  }

  @After
  public void tearDown() throws Exception {
    if (server != null) {
      server.stop();
    }

    if (proxyServer != null) {
      proxyServer.stop();
    }
    
    super.tearDown();
  }

  /**
   * Add the given chain of Behaviour to execute for the given pathspec.
   * 
   * @param pathspec e.g. "/path/*"
   */
  void addBehaviour(String pathspec, Behaviour... behaviour) {
    BehaviourServlet servlet = new BehaviourServlet(pathspec, behaviour);
    server.addServlet(servlet, servlet.getPath());
  }

  protected void configureServer(WebServer server) {
    addBehaviour("/repo/*", generate, expect, provide);
  }

  protected void configureTest() throws Exception {
  }

  protected Artifact artifact() {
    return artifact;
  }

  protected Artifact artifact(String content) throws IOException {
    return artifact().setFile(TestFileUtils.createTempFile(content));
  }

  protected Metadata metadata() {
    return metadata;
  }

  protected Metadata metadata(String content) throws IOException {
    return metadata().setFile(TestFileUtils.createTempFile(content));
  }

  protected void assertExpectations() {
    expect.assertExpectations();
  }

  protected Expect addExpectation(String path, String content) throws Exception {
    byte[] bytes = content.getBytes("UTF-8");
    return addExpectation(path, bytes);
  }

  private Expect addExpectation(String path, byte[] content) {
    expect.addExpectation(path, content);
    return expect;
  }

  protected void addDelivery(String path, String content) throws Exception {
    addDelivery(path, content.getBytes("UTF-8"));
  }

  protected void addDelivery(String path, byte[] content) {
    provide.addPath(path, content);
  }
}
