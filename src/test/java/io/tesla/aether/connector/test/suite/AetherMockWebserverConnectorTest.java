package io.tesla.aether.connector.test.suite;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.Authenticator;
import java.net.InetAddress;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import javax.inject.Inject;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.internal.test.util.TestFileProcessor;
import org.eclipse.aether.internal.test.util.TestFileUtils;
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
import org.eclipse.sisu.containers.InjectedTestCase;
import org.slf4j.ILoggerFactory;
import org.slf4j.impl.SimpleLoggerFactory;

import com.google.inject.Binder;
import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.MockWebServer;
import com.google.mockwebserver.RecordedRequest;
import com.google.mockwebserver.SocketPolicy;

//TODO Even through Maven and Aether can be configured with auth only it is probably something we should strongly warn against
//     or simply not allow. We should probably never send an authorization header unless the server asks for it.
//

public class AetherMockWebserverConnectorTest extends InjectedTestCase {

  private static final String CREDENTIALS = "dXNlcm5hbWU6cGFzc3dvcmQ="; // base64("username:password")
  private static final String PROXY_CREDENTIALS = "cHVzZXJuYW1lOnBwYXNzd29yZA=="; //base64("pusername:ppassword")
  private static final String ARTIFACT_CONTENT = "i am a secret binary full of magical powers. don't eat me. you will grow a tail.";
  private MockWebServer server = new MockWebServer();
  private String hostName;

  //
  // SSL support
  //
  private static final SSLContext sslContext;
  private static RecordingHostnameVerifier hostnameVerifier;

  //
  // Connection type flags
  //
  private boolean enableSsl;
  private boolean enableAuth;
  private boolean enableProxy;
  private boolean enableProxyAuth;

  private String proxyTarget = "repo1.maven.org";

  @Inject
  private RepositoryConnectorFactory connectorFactory;

  static {
    try {
      sslContext = new SslContextBuilder(InetAddress.getLocalHost().getHostName()).build();
      hostnameVerifier = new RecordingHostnameVerifier();
    } catch (GeneralSecurityException e) {
      throw new RuntimeException(e);
    } catch (UnknownHostException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    hostName = server.getHostName();
    System.out.println();
    System.out.println(getName());
    System.out.println();
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
    server.shutdown();
    enableAuth = false;
    enableProxy = false;
    enableProxyAuth = false;
    // SSL
    enableSsl = false;
    hostnameVerifier.reset();
    super.tearDown();
  }

  //  @Override
  //  public String getName() {
  //    return super.getName().substring(4).replaceAll("([A-Z])", " $1").toLowerCase();
  //  }

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
      assertEquals(Arrays.asList(String.format("verify %s", proxyTarget)), hostnameVerifier.calls);
    }
  }

  //
  // We setup the server to respond to a request for a primary artifact and its
  // corresponding SHA1 and MD5 checksums.
  //
  protected void enqueueServerWithSingleArtifactResponse() throws Exception {
    addResponseForTunnelingSslOverAnHttpProxy();    
    if(enableAuth) {
      MockResponse pleaseAuthenticate = new MockResponse().setResponseCode(401)
        .addHeader("WWW-Authenticate: Basic realm=\"protected area\"")
        .setBody("Please authenticate.");
      server.enqueue(pleaseAuthenticate);
    }    
    server.enqueue(new MockResponse().setBody(ARTIFACT_CONTENT));
    
    addResponseForTunnelingSslOverAnHttpProxy();
    if(enableAuth) {
      MockResponse pleaseAuthenticate = new MockResponse().setResponseCode(401)
        .addHeader("WWW-Authenticate: Basic realm=\"protected area\"")
        .setBody("Please authenticate.");
      server.enqueue(pleaseAuthenticate);
    }    
    server.enqueue(new MockResponse().setBody(sha1(ARTIFACT_CONTENT)));
    
    addResponseForTunnelingSslOverAnHttpProxy();
    if(enableAuth) {
      MockResponse pleaseAuthenticate = new MockResponse().setResponseCode(401)
        .addHeader("WWW-Authenticate: Basic realm=\"protected area\"")
        .setBody("Please authenticate.");
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
    RepositoryConnector aetherConnector = connector(remoteRepository());
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
    
    server.useHttps(sslContext.getSocketFactory(), enableProxy);
  }

  private static class RecordingHostnameVerifier implements HostnameVerifier {
    private List<String> calls = new ArrayList<String>();
    public boolean verify(String hostname, SSLSession session) {
      calls.add("verify " + hostname);
      return true;
    }
    public void reset() {
      calls = new ArrayList<String>();
    }
  }

  protected RepositoryConnectorFactory getConnectorFactory() {
    return connectorFactory;
  }

  private Artifact artifact(String content) throws IOException {
    Artifact artifact = new DefaultArtifact("gid", "aid", "classifier", "extension", "version", null);
    artifact.setFile(TestFileUtils.createTempFile(content));
    return artifact;
  }

  private RemoteRepository remoteRepository() {

    RemoteRepository.Builder builder = new RemoteRepository.Builder("async-test-repo", "default", url("repo"));

    if (enableAuth) {
      Authentication auth = new AuthenticationBuilder().addUsername("username").addPassword("password").build();
      builder.setAuthentication(auth);
    }

    if (enableProxy) {
      Proxy proxy;
      Authentication auth = null;
      if (enableProxyAuth) {
        auth = new AuthenticationBuilder().addUsername("pusername").addPassword("ppassword").build();
      }

      proxy = new Proxy(protocol(), "localhost", server.getPort(), auth);
      builder.setProxy(proxy);
    }

    return builder.build();
  }

  private String protocol() {
    if (enableSsl) {
      return "https";
    } else {
      return "http";
    }
  }

  //
  // Need to account for connecting directly or through a proxy
  //
  public String url(String path, String... parts) {

    try {
      String url;
      if (enableProxy) {
        url = String.format("%s://%s/", protocol(), proxyTarget) + path;
      } else {
        url = server.getUrl("/" + path).toExternalForm();
      }
      for (String part : parts) {
        part = URLEncoder.encode(part, "UTF-8");
        url += "/" + part;
      }

      System.out.println("Using request URL of " + url);

      return url;
    } catch (UnsupportedEncodingException e) {
      throw new IllegalArgumentException(e);
    }
  }

  private RepositoryConnector connector(RemoteRepository remoteRepository) throws NoRepositoryConnectorException, IOException {
    RepositorySystemSession repositorySystemSession = new DefaultRepositorySystemSession();
    RepositoryConnectorFactory connectorFactory = getConnectorFactory();
    return connectorFactory.newInstance(repositorySystemSession, remoteRepository);
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

  @Override
  public void configure(Binder binder) {
    binder.bind(FileProcessor.class).to(TestFileProcessor.class);
    binder.bind(ILoggerFactory.class).to(SimpleLoggerFactory.class);
  }
}
