/**
 * Copyright (c) 2012 to original author or authors
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.tesla.aether.connector.test.suite;

import java.io.File;
import java.io.IOException;
import java.net.Authenticator;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.internal.test.util.TestFileUtils;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.spi.connector.ArtifactDownload;
import org.eclipse.aether.spi.connector.RepositoryConnector;

import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.MockWebServer;
import com.squareup.okhttp.mockwebserver.RecordedRequest;
import com.squareup.okhttp.mockwebserver.SocketPolicy;

public class AetherMockWebserverConnectorTest extends AetherBaseTestCase {

  private static final String CREDENTIALS = "dXNlcm5hbWU6cGFzc3dvcmQ="; // base64("username:password")
  private static final String ARTIFACT_CONTENT = "i am a secret binary full of magical powers. don't eat me. you will grow a tail.";
  private MockWebServer server = new MockWebServer();

  public String url(String path) {
    String url;
    if (enableProxy) {
      url = String.format("%s://%s/", protocol(), proxyTarget) + path;
    } else {
      url = server.getUrl("/" + path).toExternalForm();
    }
    return url;
  }

  @Override
  protected void tearDown() throws Exception {
    server.shutdown();
    super.tearDown();
  }

  protected int port() {
    return server.getPort();
  }

  public void testArtifactDownload() throws Exception {
    enqueueServerWithSingleArtifactResponse();
    downloadArtifact();
  }

  //
  // Testing only with authentication really doesn't make sense in the absence of SSL because
  // sending your credentials in the clear is a bad idea.
  //
  public void testArtifactDownloadWithBasicAuth() throws Exception {

    enableAuthRequests();
    enqueueServerWithSingleArtifactResponse();
    downloadArtifact();

    String authorizationHeader = "Authorization: Basic " + CREDENTIALS;

    assertContainsNoneMatching(server.takeRequest().getHeaders(), "Authorization: .*");
    assertContains(server.takeRequest().getHeaders(), authorizationHeader);
    assertContainsNoneMatching(server.takeRequest().getHeaders(), "Authorization: .*");
    assertContains(server.takeRequest().getHeaders(), authorizationHeader);
    assertContainsNoneMatching(server.takeRequest().getHeaders(), "Authorization: .*");
    assertContains(server.takeRequest().getHeaders(), authorizationHeader);

    //    String expectedAuthHeader = "Authorization: Basic " + CREDENTIALS;
    //    for (int i = 0; i < 3; i++) {
    //      assertContains(server.takeRequest().getHeaders(), expectedAuthHeader);
    //    }
  }

  // http://www.squid-cache.org/mail-archive/squid-users/199811/0488.html
  public void testArtifactDownloadViaProxy() throws Exception {

    enableProxyRequests();
    enqueueServerWithSingleArtifactResponse();
    downloadArtifact();
    //
    // Make sure that the client is sending the correct headers for BASIC authentication
    //
    for (int i = 0; i < 3; i++) {
      RecordedRequest request = server.takeRequest();
      assertRequestMatches(request.getRequestLine(), String.format("GET http://%s/repo(.*) HTTP/1.1", proxyTarget));
      assertContains(request.getHeaders(), String.format("Host: %s", proxyTarget));
    }
  }

  public void testArtifactDownloadViaSsl() throws Exception {

    enableSslRequests();
    enqueueServerWithSingleArtifactResponse();
    downloadArtifact();
  }

  //you really have 6 cases: http direct, https direct, http through http proxy, http through https proxy, https through http proxy, https through https proxy

  // client to http://target
  // client to https://target
  // client to http://target via http://proxy
  // client to http://target via https://proxy
  // client to https://target via http://proxy
  // client to https://target via https://proxy

  //
  // Used for CONNECT messages to tunnel SSL over an HTTP proxy.
  //
  // CLIENT ---[HTTP]---> PROXY ---[HTTPS]---> TARGET
  //
  public void XXXtestArtifactDownloadViaHttpProxyToHttpsEndPoint() throws Exception {

    // these are not quite right, it's not sslTargetRequests and enableHttpProxy
    enableProxyRequests();
    enableSslRequests();
    enqueueServerWithSingleArtifactResponse();
    downloadArtifact();

    //
    // Make sure that the client is sending the correct headers for BASIC authentication
    //
    for (int i = 0; i < 3; i++) {

      RecordedRequest connect = server.takeRequest();
      assertEquals("Connect line failure on proxy", String.format("CONNECT %s:443 HTTP/1.1", proxyTarget), connect.getRequestLine());
      assertContains(connect.getHeaders(), String.format("Host: %s", proxyTarget));

      RecordedRequest get = server.takeRequest();
      assertRequestMatches(get.getRequestLine(), String.format("GET /repo(.*) HTTP/1.1", proxyTarget));
      assertContains(get.getHeaders(), String.format("Host: %s", proxyTarget));
      assertEquals(Arrays.asList(String.format("verify %s", proxyTarget)), hostnameVerifier.calls());
    }
  }

  //
  // We setup the server to respond to a request for a primary artifact and its
  // corresponding SHA1 and MD5 checksums.
  //
  protected void enqueueServerWithSingleArtifactResponse() throws Exception {
    addResponseForTunnelingSslOverAnHttpProxy();
    if (enableAuth) {
      MockResponse pleaseAuthenticate = new MockResponse().setResponseCode(401).addHeader("WWW-Authenticate: Basic realm=\"protected area\"").setBody("Please authenticate.");
      server.enqueue(pleaseAuthenticate);
    }
    server.enqueue(new MockResponse().setBody(ARTIFACT_CONTENT));

    addResponseForTunnelingSslOverAnHttpProxy();
    if (enableAuth) {
      MockResponse pleaseAuthenticate = new MockResponse().setResponseCode(401).addHeader("WWW-Authenticate: Basic realm=\"protected area\"").setBody("Please authenticate.");
      server.enqueue(pleaseAuthenticate);
    }
    server.enqueue(new MockResponse().setBody(sha1(ARTIFACT_CONTENT)));

    addResponseForTunnelingSslOverAnHttpProxy();
    if (enableAuth) {
      MockResponse pleaseAuthenticate = new MockResponse().setResponseCode(401).addHeader("WWW-Authenticate: Basic realm=\"protected area\"").setBody("Please authenticate.");
      server.enqueue(pleaseAuthenticate);
    }
    server.enqueue(new MockResponse().setBody(md5(ARTIFACT_CONTENT)));

    server.play();
  }

  private void addResponseForTunnelingSslOverAnHttpProxy() {
    if (enableSsl && enableProxy) {
      server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.UPGRADE_TO_SSL_AT_END).clearHeaders());
    }
  }

  protected void downloadArtifact() throws Exception {

    File artifactFile = TestFileUtils.createTempFile("");
    Artifact artifact = artifact(ARTIFACT_CONTENT);
    ArtifactDownload download = new ArtifactDownload(artifact, null, artifactFile, RepositoryPolicy.CHECKSUM_POLICY_FAIL);
    Collection<? extends ArtifactDownload> downloads = Arrays.asList(download);
    //
    //
    //
    RepositoryConnector aetherConnector = connector();
    aetherConnector.get(downloads, null);
    assertNull(String.valueOf(download.getException()), download.getException());
    TestFileUtils.assertContent(ARTIFACT_CONTENT, artifactFile);
  }

  private void enableAuthRequests() {

    enableAuth = true;
  }

  private void enableProxyRequests() {

    enableProxy = true;
  }

  private void enableSslRequests() {
    enableSsl = true;
    server.useHttps(sslContext.getSocketFactory(), enableProxy);
  }

  private Artifact artifact(String content) throws IOException {
    Artifact artifact = new DefaultArtifact("gid", "aid", "classifier", "extension", "version", null);
    artifact.setFile(TestFileUtils.createTempFile(content));
    return artifact;
  }

  //
  // Helper test methods
  //

  private void assertContains(List<String> headers, String header) {
    assertTrue(headers.toString(), headers.contains(header));
  }

  private void assertContainsNoneMatching(List<String> headers, String pattern) {
    for (String header : headers) {
      if (header.matches(pattern)) {
        fail("Header " + header + " matches " + pattern);
      }
    }
  }

  private void assertRequestMatches(String request, String pattern) {
    if (!request.matches(pattern)) {
      fail("Request does not match pattern " + pattern);
    }
  }
}
