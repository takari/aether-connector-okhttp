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

import io.tesla.aether.connector.test.suite.server.Behaviour;
import io.tesla.aether.connector.test.suite.server.BehaviourServlet;
import io.tesla.aether.connector.test.suite.server.Expect;
import io.tesla.aether.connector.test.suite.server.Generate;
import io.tesla.aether.connector.test.suite.server.Provide;
import io.tesla.webserver.Jetty8WebServer;
import io.tesla.webserver.WebServer;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.inject.Inject;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.internal.test.util.TestFileProcessor;
import org.eclipse.aether.internal.test.util.TestFileUtils;
import org.eclipse.aether.internal.test.util.TestUtils;
import org.eclipse.aether.metadata.DefaultMetadata;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.Proxy;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.RepositoryConnector;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.io.FileProcessor;
import org.eclipse.aether.transfer.NoRepositoryConnectorException;
import org.eclipse.aether.util.repository.AuthenticationBuilder;
import org.eclipse.sisu.containers.InjectedTestCase;
import org.junit.After;
import org.slf4j.ILoggerFactory;
import org.slf4j.impl.SimpleLoggerFactory;

import com.google.inject.Binder;

//
//you really have 6 cases: http direct, https direct, http through http proxy, http through https proxy, https through http proxy, https through https proxy
//
//client to http://target
//client to https://target
//client to http://target via http://proxy
//client to http://target via https://proxy
//client to https://target via http://proxy
//client to https://target via https://proxy

public abstract class AetherTestCase extends InjectedTestCase {

  protected RemoteRepository repository;
  private Artifact artifact;
  protected Metadata metadata;
  protected Generate generate;
  protected Expect expect;
  protected Provide provide;
  protected RepositoryConnector connector;
  protected WebServer server;
  protected WebServer proxyServer;
  protected static SSLContext sslContext;

  private boolean enableConnectingWithBasicAuth;
  private boolean enableProxy;
  private boolean enableProxyWithAuth;
  private boolean enableSsl;

  static {
    try {
      sslContext = new SslContextBuilder("localhost").build();
    } catch (GeneralSecurityException e) {
      throw new RuntimeException(e);
    }
  }

  protected DefaultRepositorySystemSession session;

  @Inject
  protected RepositoryConnectorFactory factory;

  protected RemoteRepository repository() {
    return new RemoteRepository.Builder("remote-repo", "default", url("repo")).build();
  }

  protected RemoteRepository repositoryConnectingViaProxy() {
    Proxy proxy = new Proxy(getHttpProtocol(), "localhost", server.getPort(), null);
    return new RemoteRepository.Builder(repository()).setProxy(proxy).build();
  }

  protected RemoteRepository repositoryConnectingViaProxyWithAuth() {
    Authentication auth = new AuthenticationBuilder().addUsername("puser").addPassword("password").build();
    Proxy proxy = new Proxy(getHttpProtocol(), "localhost", server.getPort(), auth);
    return new RemoteRepository.Builder(repository()).setProxy(proxy).build();
  }

  protected RemoteRepository repositoryConnectingWithBasicAuth() {
    Authentication auth = new AuthenticationBuilder().addUsername("user").addPassword("password").build();
    return new RemoteRepository.Builder(repository()).setAuthentication(auth).build();
  }

  private String getHttpProtocol() {
    if (enableSsl) {
      return "https";
    } else {
      return "http";
    }
  }

  public void setUp() throws Exception {

    super.setUp();

    connector = null;
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

    //
    //
    //
    session = session();

    if (enableProxy || enableProxyWithAuth) {
      proxyServer = new Jetty8WebServer();
      proxyServer.enableProxy();
      proxyServer.start();
      repository = repositoryConnectingViaProxy();
    }

    if (enableConnectingWithBasicAuth) {
      repository = repositoryConnectingWithBasicAuth();
    } else if (enableProxy) {
      repository = repositoryConnectingViaProxy();
    } else if (enableProxyWithAuth) {
      repository = repositoryConnectingViaProxyWithAuth();
    } else {
      repository = repository();
    }

    artifact = new DefaultArtifact("gid", "aid", "classifier", "extension", "version", null);
    metadata = new DefaultMetadata("gid", "aid", "version", "maven-metadata.xml", Metadata.Nature.RELEASE_OR_SNAPSHOT, null);
    //
    //
    //
  }

  @After
  public void tearDown() throws Exception {
    connector().close();
    session = null;
    TestFileUtils.deleteTempFiles();
    if (server != null) {
      server.stop();
    }

    if (proxyServer != null) {
      proxyServer.stop();
    }
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

  protected void enableSsl() throws Exception {
    enableSsl = true;
    server.enableSsl(sslContext);
    //
    // You would never do this in production code but it's way easier to test with a generated
    // SSLContext and a hostname verifier that just returns true.
    //
    SSLContext.setDefault(sslContext);
    HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
    HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() {
      @Override
      public boolean verify(String host, SSLSession session) {
        return true;
      }
    });
  }

  protected void enableConnectingWithBasicAuth() {
    enableConnectingWithBasicAuth = true;
  }

  protected void enableProxy() {
    enableProxy = true;
  }

  protected void enableProxyWithAuth() {
    enableProxyWithAuth = true;
  }

  protected RepositoryConnector connector() throws NoRepositoryConnectorException {
    if (connector == null) {
      connector = factory.newInstance(session, repository);
    }
    return connector;
  }

  protected DefaultRepositorySystemSession session() {
    return TestUtils.newSession();
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

  protected String md5(String string) throws NoSuchAlgorithmException, UnsupportedEncodingException {
    String algo = "MD5";
    return digest(string, algo);
  }

  private String digest(String string, String algo) throws NoSuchAlgorithmException, UnsupportedEncodingException {
    MessageDigest digest = MessageDigest.getInstance(algo);
    byte[] bytes = digest.digest(string.getBytes("UTF-8"));
    StringBuilder buffer = new StringBuilder(64);

    for (int i = 0; i < bytes.length; i++) {
      int b = bytes[i] & 0xFF;
      if (b < 0x10) {
        buffer.append('0');
      }
      buffer.append(Integer.toHexString(b));
    }
    return buffer.toString();
  }

  protected String sha1(String string) throws NoSuchAlgorithmException, UnsupportedEncodingException {
    return digest(string, "SHA-1");
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

  private String url() {
    try {
      if (enableProxy || enableProxyWithAuth) {
        return proxyServer.getUrl().toExternalForm();
      } else {
        return server.getUrl().toExternalForm();
      }
    } catch (MalformedURLException e) {
      throw new IllegalArgumentException("Provider was set up with wrong url", e);
    }
  }

  public WebServer provider() {
    return server;
  }

  protected String url(String path, String... parts) {
    try {
      String url = url() + "/" + path;
      for (String part : parts) {
        part = URLEncoder.encode(part, "UTF-8");
        url += "/" + part;
      }

      return url;
    } catch (UnsupportedEncodingException e) {
      throw new IllegalArgumentException(e);
    }
  }

  protected void addDelivery(String path, String content) throws Exception {
    addDelivery(path, content.getBytes("UTF-8"));
  }

  protected void addDelivery(String path, byte[] content) {
    provide.addPath(path, content);
  }

  @Override
  public void configure(Binder binder) {
    binder.bind(FileProcessor.class).to(TestFileProcessor.class);
    binder.bind(ILoggerFactory.class).to(SimpleLoggerFactory.class);
  }
}
