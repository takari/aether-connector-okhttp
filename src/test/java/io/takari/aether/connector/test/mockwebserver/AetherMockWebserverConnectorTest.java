/**
 * Copyright (c) 2012 to original author or authors
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.aether.connector.test.mockwebserver;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.inject.Inject;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.internal.test.util.TestFileProcessor;
import org.eclipse.aether.internal.test.util.TestFileUtils;
import org.eclipse.aether.internal.test.util.TestUtils;
import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.Proxy;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.spi.connector.ArtifactDownload;
import org.eclipse.aether.spi.connector.RepositoryConnector;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.io.FileProcessor;
import org.eclipse.aether.transfer.NoRepositoryConnectorException;
import org.eclipse.aether.util.repository.AuthenticationBuilder;
import org.eclipse.sisu.launch.InjectedTestCase;
import org.junit.Assert;
import org.slf4j.ILoggerFactory;
import org.slf4j.impl.SimpleLoggerFactory;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.google.inject.Binder;

import io.takari.aether.client.AetherClientAuthentication;
import io.takari.aether.client.AetherClientConfig;
import io.takari.aether.client.AetherClientProxy;
import io.takari.aether.client.Response;
import io.takari.aether.okhttp.OkHttpAetherClient;
import okhttp3.Headers;
import okhttp3.internal.SslContextBuilder;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;

//
// 6 cases: 
//   - http direct, 
//   - https direct, 
//   - http through http proxy, 
//   - http through https proxy, 
//   - https through http proxy (not allowed)
//   - https through https proxy
//
// Combination with authentication
//
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
//
public class AetherMockWebserverConnectorTest extends InjectedTestCase {

  private static final String CREDENTIALS = "dXNlcm5hbWU6cGFzc3dvcmQ="; // base64("username:password")
  private static final String ARTIFACT_CONTENT = "i am a secret binary full of magical powers. don't eat me. you will grow a tail.";

  private static final String USERNAME = "username";
  private static final String PASSWORD = "password";

  private static final String PROXY_USERNAME = "pusername";
  private static final String PROXY_PASSWORD = "ppassword";

  private static final RecordingHostnameVerifier hostnameVerifier = new RecordingHostnameVerifier();
  private static final SSLContext sslContext = SslContextBuilder.localhost();

  private MockWebServer server = new MockWebServer();

  private boolean enableSsl;
  private boolean enableAuth;
  private boolean enableProxy;
  private boolean enableProxyWithAuth;
  private String proxyTarget = "repo1.maven.org";

  private RemoteRepository repository;
  private DefaultRepositorySystemSession session;
  private RepositoryConnector connector;

  @Inject
  protected RepositoryConnectorFactory repositoryConnectorFactory;

  protected String protocol() {
    if (enableSsl) {
      return "https";
    } else {
      return "http";
    }
  }

  public String url(String path) {
    String url;
    if (enableProxy) {
      url = String.format("%s://%s/", protocol(), proxyTarget) + path;
    } else {
      url = server.url("/" + path).toString();
    }
    return url;
  }

  @Override
  protected void tearDown() throws Exception {
    Authenticator.setDefault(null);
    System.clearProperty("proxyHost");
    System.clearProperty("proxyPort");
    System.clearProperty("http.proxyHost");
    System.clearProperty("http.proxyPort");
    System.clearProperty("https.proxyHost");
    System.clearProperty("https.proxyPort");
    enableAuth = false;
    enableProxy = false;
    enableProxyWithAuth = false;
    enableSsl = false;
    server.shutdown();
    super.tearDown();
  }

  protected int port() {
    return server.getPort();
  }

  protected String hostname() {
    return server.getHostName();
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

    // once challenged, the client is expected to send credentials eagerly
    assertContainsNoneMatching(server.takeRequest().getHeaders(), "Authorization");
    assertContains(server.takeRequest().getHeaders(), "Authorization", "Basic " + CREDENTIALS);
    assertContains(server.takeRequest().getHeaders(), "Authorization", "Basic " + CREDENTIALS);
  }

  public void testArtifactDownloadWithBasicAuthAndSystemAuthenticator() throws Exception {
    // the point of this test is to validate that default system authenticator is not called

    final AtomicBoolean defaultAuthenticationRequested = new AtomicBoolean(false);
    Authenticator.setDefault(new Authenticator() {
      @Override
      protected PasswordAuthentication getPasswordAuthentication() {
        defaultAuthenticationRequested.set(true);
        throw new UnsupportedOperationException();
      }
    });
    try {
      enableAuthRequests();
      enqueueServerWithSingleArtifactResponse();
      downloadArtifact();

      assertFalse(defaultAuthenticationRequested.get());
    } finally {
      Authenticator.setDefault(null);
    }
  }

  // http://www.squid-cache.org/mail-archive/squid-users/199811/0488.html
  public void testArtifactDownloadViaProxy() throws Exception {

    enableProxyRequests();
    enqueueServerWithSingleArtifactResponse();
    downloadArtifact();
    //
    // Make sure that the client is sending the correct headers for BASIC authentication
    //
    for (int i = 0; i < 2; i++) {
      RecordedRequest request = server.takeRequest();
      assertRequestMatches(request.getRequestLine(), String.format("GET http://%s/repo(.*) HTTP/1.1", proxyTarget));
      assertContains(request.getHeaders(), "Host", proxyTarget);
    }
  }

  public void testArtifactDownloadViaSsl() throws Exception {

    enableSslRequests();
    enqueueServerWithSingleArtifactResponse();
    downloadArtifact();
  }

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
      assertContains(connect.getHeaders(), "Host", proxyTarget);

      RecordedRequest get = server.takeRequest();
      assertRequestMatches(get.getRequestLine(), String.format("GET /repo(.*) HTTP/1.1", proxyTarget));
      assertContains(get.getHeaders(), "Host", proxyTarget);
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
      MockResponse pleaseAuthenticate = new MockResponse() //
          .setResponseCode(401) //
          .addHeader("WWW-Authenticate: Basic realm=\"protected area\"") //
          .setBody("Please authenticate.");
      server.enqueue(pleaseAuthenticate);
    }
    server.enqueue(new MockResponse().setBody(ARTIFACT_CONTENT));

    addResponseForTunnelingSslOverAnHttpProxy();
    server.enqueue(new MockResponse().setBody(sha1(ARTIFACT_CONTENT)));

    server.start();
  }

  private void addResponseForTunnelingSslOverAnHttpProxy() {
    if (enableSsl && (enableProxy || enableProxyWithAuth)) {
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
    Assert.assertEquals(ARTIFACT_CONTENT, Files.toString(artifactFile, Charsets.UTF_8));
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

  private void assertContains(Headers headers, String header, String value) {
    assertTrue(headers.toString(), headers.values(header).contains(value));
  }

  private void assertContainsNoneMatching(Headers headers, String header) {
    assertTrue( headers.values(header).isEmpty());
  }

  private void assertRequestMatches(String request, String pattern) {
    if (!request.matches(pattern)) {
      fail("Request does not match pattern " + pattern);
    }
  }

  //

  /**
   * Reads {@code count} characters from the stream. If the stream is
   * exhausted before {@code count} characters can be read, the remaining
   * characters are returned and the stream is closed.
   */
  private String readAscii(InputStream in, int count) throws IOException {
    StringBuilder result = new StringBuilder();
    for (int i = 0; i < count; i++) {
      int value = in.read();
      if (value == -1) {
        in.close();
        break;
      }
      result.append((char) value);
    }
    return result.toString();
  }

  //
  // Conversation for proxy auth
  //
  // --> GET http://server.com/foo HTTP/1.1
  //
  // <-- HTTP/1.1 407 Proxy Authorization Required
  // <-- Proxy-Authenticate: Basic realm="Secure Realm"
  //
  // --> GET http://server.com/foo HTTP/1.1
  // --> Proxy-Authorization: Basic dXNlcm5hbWU6cGFzc3dvcmQ
  //
  // <-- HTTP/1.1 200 OK
  //
  public void testProxyAuthenticateOnConnect() throws Exception {
    Authenticator.setDefault(new RecordingAuthenticator());
    server.enqueue(new MockResponse().setResponseCode(407).addHeader("Proxy-Authenticate: Basic realm=\"localhost\""));
    server.enqueue(new MockResponse().setBody("A"));
    server.start();

    AetherClientConfig config = new AetherClientConfig();
    AetherClientAuthentication auth = new AetherClientAuthentication("username", "password");
    AetherClientProxy proxy = new AetherClientProxy();
    proxy.setAuthentication(auth);
    proxy.setHost(server.getHostName());
    proxy.setPort(server.getPort());
    config.setUserAgent("Test");
    config.setProxy(proxy);

    OkHttpAetherClient aetherClient = new OkHttpAetherClient(config);
    Response res = aetherClient.get("http://server.com/foo");
    assertEquals(200, res.getStatusCode());
    assertEquals("A", readAscii(res.getInputStream(), Integer.MAX_VALUE));
    
    RecordedRequest connect1 = server.takeRequest();
    assertEquals("GET http://server.com/foo HTTP/1.1", connect1.getRequestLine());
    assertContainsNoneMatching(connect1.getHeaders(), "Proxy-Authorization");

    RecordedRequest connect2 = server.takeRequest();
    assertEquals("GET http://server.com/foo HTTP/1.1", connect2.getRequestLine());
    assertContains(connect2.getHeaders(), "Proxy-Authorization", "Basic " + RecordingAuthenticator.BASE_64_CREDENTIALS);        
  }
  
  //
  // Conversation for proxy auth over SSL
  //
  public void testProxyAuthenticateOnConnectOverSSL() throws Exception {
    Authenticator.setDefault(new RecordingAuthenticator());
    server.useHttps(sslContext.getSocketFactory(), true);
    server.enqueue(new MockResponse().setResponseCode(407).addHeader("Proxy-Authenticate: Basic realm=\"localhost\""));
    server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.UPGRADE_TO_SSL_AT_END).clearHeaders());
    server.enqueue(new MockResponse().setBody("A"));
    server.start();

    AetherClientConfig config = new AetherClientConfig();
    AetherClientAuthentication auth = new AetherClientAuthentication("username", "password");
    AetherClientProxy proxy = new AetherClientProxy(); 
    proxy.setAuthentication(auth);
    proxy.setHost(server.getHostName());
    proxy.setPort(server.getPort());
    config.setUserAgent("Test");
    config.setAuthentication(auth);
    config.setProxy(proxy);
    config.setSslSocketFactory(sslContext.getSocketFactory());
    config.setHostnameVerifier(hostnameVerifier);

    OkHttpAetherClient aetherClient = new OkHttpAetherClient(config);
    Response res = aetherClient.get("https://android.com/foo");
    assertEquals(200, res.getStatusCode());
    assertEquals("A", readAscii(res.getInputStream(), Integer.MAX_VALUE));

    RecordedRequest connect1 = server.takeRequest();
    assertEquals("CONNECT android.com:443 HTTP/1.1", connect1.getRequestLine());
    assertContainsNoneMatching(connect1.getHeaders(), "Proxy-Authorization");
    
    RecordedRequest connect2 = server.takeRequest();
    assertEquals("CONNECT android.com:443 HTTP/1.1", connect2.getRequestLine());
    assertContains(connect2.getHeaders(), "Proxy-Authorization", "Basic " + RecordingAuthenticator.BASE_64_CREDENTIALS);

    RecordedRequest get = server.takeRequest();
    assertEquals("GET /foo HTTP/1.1", get.getRequestLine());
    assertContainsNoneMatching(get.getHeaders(), "Proxy-Authorization");
  }
  
  public void testAetherClientProxyAuthHeaders() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(407).addHeader("Proxy-Authenticate: Basic realm=\"localhost\""));
    server.enqueue(new MockResponse().setResponseCode(401).addHeader("WWW-Authenticate: Basic realm=\"localhost\""));
    server.enqueue(new MockResponse().setBody("A"));
    server.enqueue(new MockResponse().setBody("B"));
    server.start();
    
    AetherClientConfig config = new AetherClientConfig();
    AetherClientAuthentication auth = new AetherClientAuthentication("username", "password");
    AetherClientProxy proxy = new AetherClientProxy();
    proxy.setAuthentication(auth);
    proxy.setHost(server.getHostName());
    proxy.setPort(server.getPort());
    config.setUserAgent("Test");
    config.setAuthentication(auth);
    config.setProxy(proxy);
    
    OkHttpAetherClient aetherClient = new OkHttpAetherClient(config);
    
    Response res = aetherClient.get("http://android.com/foo");
    assertEquals("A", readAscii(res.getInputStream(), Integer.MAX_VALUE));

    res = aetherClient.get("http://android.com/foo2");
    assertEquals("B", readAscii(res.getInputStream(), Integer.MAX_VALUE));
    
    RecordedRequest connect1 = server.takeRequest();
    assertEquals("GET http://android.com/foo HTTP/1.1", connect1.getRequestLine());
    assertContainsNoneMatching(connect1.getHeaders(), "Proxy-Authorization");
    assertContainsNoneMatching(connect1.getHeaders(), "Authorization");
    
    RecordedRequest connect2 = server.takeRequest();
    assertEquals("GET http://android.com/foo HTTP/1.1", connect2.getRequestLine());
    assertContains(connect2.getHeaders(), "Proxy-Authorization", "Basic " + RecordingAuthenticator.BASE_64_CREDENTIALS);
    assertContainsNoneMatching(connect2.getHeaders(), "Authorization");

    RecordedRequest get = server.takeRequest();
    assertEquals("GET http://android.com/foo HTTP/1.1", get.getRequestLine());
    assertContains(get.getHeaders(), "Proxy-Authorization", "Basic " + RecordingAuthenticator.BASE_64_CREDENTIALS);
    assertContains(get.getHeaders(), "Authorization", "Basic " + RecordingAuthenticator.BASE_64_CREDENTIALS);
    
    RecordedRequest get2 = server.takeRequest();
    assertEquals("GET http://android.com/foo2 HTTP/1.1", get2.getRequestLine());
    assertContains(get2.getHeaders(), "Proxy-Authorization", "Basic " + RecordingAuthenticator.BASE_64_CREDENTIALS);
    assertContains(get2.getHeaders(), "Authorization", "Basic " + RecordingAuthenticator.BASE_64_CREDENTIALS);
    
  }
  
  public void testAetherClientSSLProxyAuthHeaders() throws Exception {
    server.useHttps(sslContext.getSocketFactory(), true);
    server.enqueue(new MockResponse().setResponseCode(407).addHeader("Proxy-Authenticate: Basic realm=\"localhost\""));
    server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.UPGRADE_TO_SSL_AT_END).clearHeaders());
    server.enqueue(new MockResponse().setResponseCode(401).addHeader("WWW-Authenticate: Basic realm=\"localhost\""));
    server.enqueue(new MockResponse().setBody("A"));
    server.enqueue(new MockResponse().setBody("B"));
    server.start();
    
    AetherClientConfig config = new AetherClientConfig();
    AetherClientAuthentication auth = new AetherClientAuthentication("username", "password");
    AetherClientProxy proxy = new AetherClientProxy();
    proxy.setAuthentication(auth);
    proxy.setHost(server.getHostName());
    proxy.setPort(server.getPort());
    config.setUserAgent("Test");
    config.setAuthentication(auth);
    config.setProxy(proxy);
    config.setSslSocketFactory(sslContext.getSocketFactory());
    config.setHostnameVerifier(hostnameVerifier);
    
    OkHttpAetherClient aetherClient = new OkHttpAetherClient(config);
    
    Field field = aetherClient.getClass().getDeclaredField("httpClient");
    field.setAccessible(true);

    Response res = aetherClient.get("https://android.com/foo");
    assertEquals("A", readAscii(res.getInputStream(), Integer.MAX_VALUE));

    res = aetherClient.get("https://android.com/foo2");
    assertEquals("B", readAscii(res.getInputStream(), Integer.MAX_VALUE));
    
    RecordedRequest connect1 = server.takeRequest();
    assertEquals("CONNECT android.com:443 HTTP/1.1", connect1.getRequestLine());
    assertContainsNoneMatching(connect1.getHeaders(), "Proxy-Authorization");
    assertContainsNoneMatching(connect1.getHeaders(), "Authorization");
    
    RecordedRequest connect2 = server.takeRequest();
    assertEquals("CONNECT android.com:443 HTTP/1.1", connect2.getRequestLine());
    assertContains(connect2.getHeaders(), "Proxy-Authorization", "Basic " + RecordingAuthenticator.BASE_64_CREDENTIALS);
    assertContainsNoneMatching(connect2.getHeaders(), "Authorization");

    RecordedRequest get = server.takeRequest();
    assertEquals("GET /foo HTTP/1.1", get.getRequestLine());
    assertContainsNoneMatching(get.getHeaders(), "Proxy-Authorization");
    assertContainsNoneMatching(get.getHeaders(), "Authorization");
    
    RecordedRequest get2 = server.takeRequest();
    assertEquals("GET /foo HTTP/1.1", get2.getRequestLine());
    assertContainsNoneMatching(get2.getHeaders(), "Proxy-Authorization");
    assertContains(get2.getHeaders(), "Authorization", "Basic " + RecordingAuthenticator.BASE_64_CREDENTIALS);
    
    RecordedRequest get3 = server.takeRequest();
    assertEquals("GET /foo2 HTTP/1.1", get3.getRequestLine());
    assertContainsNoneMatching(get3.getHeaders(), "Proxy-Authorization");
    assertContains(get3.getHeaders(), "Authorization", "Basic " + RecordingAuthenticator.BASE_64_CREDENTIALS);
  }

  //
  //
  //

  protected DefaultRepositorySystemSession session() {
    if (session == null) {
      session = TestUtils.newSession();
    }
    return session;
  }

  protected RepositoryConnector connector() throws NoRepositoryConnectorException, IOException {
    return connector(false);
  }

  protected RepositoryConnector connector(boolean forceNew) throws NoRepositoryConnectorException, IOException {
    if (connector == null || forceNew) {
      connector = repositoryConnectorFactory.newInstance(session(), remoteRepository());
    }
    return connector;
  }

  private RemoteRepository remoteRepository() {
    if (repository == null) {
      RemoteRepository.Builder builder = new RemoteRepository.Builder("repo", "default", url("repo"));
      if (enableProxyWithAuth) {
        Authentication auth = new AuthenticationBuilder().addUsername(PROXY_USERNAME).addPassword(PROXY_PASSWORD).build();
        Proxy proxy = new Proxy(protocol(), hostname(), port(), auth);
        builder.setProxy(proxy);
      } else if (enableProxy) {
        Proxy proxy = new Proxy(protocol(), hostname(), port());
        builder.setProxy(proxy);
      }
      if (enableAuth) {
        Authentication auth = new AuthenticationBuilder().addUsername(USERNAME).addPassword(PASSWORD).build();
        builder.setAuthentication(auth);
      }
      repository = builder.build();
    }
    return repository;
  }

  //
  // Checksums
  //
  protected String sha1(String string) throws NoSuchAlgorithmException, UnsupportedEncodingException {
    return digest(string, "SHA-1");
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

  //
  // Right now all all tests will have an SSLSocketFactory binding. Ideally we configure this and
  // have a conditional binding but right now I use methods to configure the test setup and at this
  // point it's too late to control the binding. Not ideal, but not horrible by any means.
  //
  @Override
  public void configure(Binder binder) {
    binder.bind(FileProcessor.class).to(TestFileProcessor.class);
    binder.bind(ILoggerFactory.class).to(SimpleLoggerFactory.class);
    binder.bind(SSLSocketFactory.class).toInstance(sslContext.getSocketFactory());
  }

}
