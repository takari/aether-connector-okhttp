package io.takari.aether.connector.test.suite;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.Authenticator;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.internal.test.util.TestFileProcessor;
import org.eclipse.aether.internal.test.util.TestFileUtils;
import org.eclipse.aether.internal.test.util.TestUtils;
import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.Proxy;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.RepositoryConnector;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.io.FileProcessor;
import org.eclipse.aether.transfer.NoRepositoryConnectorException;
import org.eclipse.aether.util.repository.AuthenticationBuilder;
import org.eclipse.sisu.launch.InjectedTestCase;
import org.slf4j.ILoggerFactory;
import org.slf4j.impl.SimpleLoggerFactory;

import com.google.inject.Binder;
import com.squareup.okhttp.internal.SslContextBuilder;

public abstract class AetherBaseTestCase extends InjectedTestCase {

  protected static final String USERNAME = "username";
  protected static final String PASSWORD = "password";

  protected static final String PROXY_USERNAME = "pusername";
  protected static final String PROXY_PASSWORD = "ppassword";

  // ssl-enabled tests require server and client agree on server hostname
  protected static final String hostname;
  protected static final SSLContext sslContext;
  static {
    try {
      hostname = InetAddress.getByName(null).getHostName();
      sslContext = new SslContextBuilder(hostname).build();
    } catch (UnknownHostException | GeneralSecurityException e) {
      throw new RuntimeException(e);
    }
  }

  protected static final RecordingHostnameVerifier hostnameVerifier = new RecordingHostnameVerifier();

  protected boolean enableSsl;
  protected boolean enableAuth;
  protected boolean enableProxy;
  protected boolean enableProxyWithAuth;

  protected String proxyTarget = "repo1.maven.org";

  protected abstract int port();

  protected abstract String url(String path);

  private RemoteRepository repository;
  private DefaultRepositorySystemSession session;
  private RepositoryConnector connector;

  @Inject
  protected RepositoryConnectorFactory repositoryConnectorFactory;

  @Override
  protected void tearDown() throws Exception {
    connector().close();
    connector = null;
    TestFileUtils.deleteTempFiles();
    
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
    hostnameVerifier.reset();
    
    super.tearDown();
  }
  
  protected DefaultRepositorySystemSession session() {
    if(session== null) {
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

  // This should be private
  protected RemoteRepository remoteRepository() {
    if (repository == null) {
      RemoteRepository.Builder builder = new RemoteRepository.Builder("async-test-repo", "default", url("repo"));
      if (enableAuth) {
        Authentication auth = new AuthenticationBuilder().addUsername(USERNAME).addPassword(PASSWORD).build();
        builder.setAuthentication(auth);
      }
      if (enableProxy || enableProxyWithAuth) {
        Proxy proxy;
        Authentication auth = null;
        if (enableProxyWithAuth) {
          auth = new AuthenticationBuilder().addUsername(PROXY_USERNAME).addPassword(PROXY_PASSWORD).build();
        }
        proxy = new Proxy(protocol(), "localhost", port(), auth);
        builder.setProxy(proxy);
      }
      repository = builder.build();
    }
    return repository;
  }

  protected String protocol() {
    if (enableSsl) {
      return "https";
    } else {
      return "http";
    }
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

  protected static class RecordingHostnameVerifier implements HostnameVerifier {
    private List<String> calls = new ArrayList<String>();

    public boolean verify(String hostname, SSLSession session) {
      calls.add("verify " + hostname);
      return true;
    }

    public List<String> calls() {
      return calls;
    }

    public void reset() {
      calls = new ArrayList<String>();
    }
  }

}
