package io.tesla.aether.connector.test.suite;

import io.tesla.aether.connector.AetherRepositoryConnectorFactory;
import io.tesla.aether.connector.AuthorizationException;

import java.util.Arrays;
import java.util.Collection;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.internal.impl.DefaultFileProcessor;
import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.Proxy;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.spi.connector.ArtifactDownload;
import org.eclipse.aether.spi.connector.RepositoryConnector;
import org.eclipse.aether.util.repository.AuthenticationBuilder;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.MockWebServer;


public class FailedAuthTest {

  @Rule
  public final TemporaryFolder temp = new TemporaryFolder();

  @Test
  public void getGet() throws Exception {
    MockWebServer server = new MockWebServer();
    server.enqueue(new MockResponse().setResponseCode(401)
        .addHeader("WWW-Authenticate: Basic realm=\"protected area\"")
        .setBody("Please authenticate."));
    server.enqueue(new MockResponse().setResponseCode(401)
        .addHeader("WWW-Authenticate: Basic realm=\"protected area\"")
        .setBody("Please authenticate."));
    server.play();

    String url = server.getUrl("/repo").toExternalForm();
    RemoteRepository.Builder builder =
        new RemoteRepository.Builder("async-test-repo", "default", url);
    Authentication auth =
        new AuthenticationBuilder().addUsername("username").addPassword("password").build();
    builder.setAuthentication(auth);
    RemoteRepository repository = builder.build();

    RepositorySystemSession repositorySystemSession = new DefaultRepositorySystemSession();
    AetherRepositoryConnectorFactory factory =
        new AetherRepositoryConnectorFactory(new DefaultFileProcessor());
    RepositoryConnector connector = factory.newInstance(repositorySystemSession, repository);

    Artifact artifact =
        new DefaultArtifact("gid", "aid", "classifier", "extension", "version", null);
    ArtifactDownload download =
        new ArtifactDownload(artifact, null, temp.newFile("test"),
            RepositoryPolicy.CHECKSUM_POLICY_FAIL);
    Collection<? extends ArtifactDownload> downloads = Arrays.asList(download);
    connector.get(downloads, null);

    Assert.assertTrue(String.valueOf(download.getException()),
        download.getException().getCause() instanceof AuthorizationException);
  }

  @Test
  public void getGetProxy() throws Exception {
    MockWebServer server = new MockWebServer();
    server.enqueue(new MockResponse().setResponseCode(407)
        .addHeader("Proxy-Authenticate: Basic realm=\"protected area\"")
        .setBody("Please authenticate."));
    server.enqueue(new MockResponse().setResponseCode(407)
        .addHeader("Proxy-Authenticate: Basic realm=\"protected area\"")
        .setBody("Please authenticate."));
    server.play();

    String url = server.getUrl("/repo").toExternalForm();
    RemoteRepository.Builder builder =
        new RemoteRepository.Builder("async-test-repo", "default", url);
    Authentication auth =
        new AuthenticationBuilder().addUsername("username").addPassword("password").build();
    builder.setAuthentication(auth);
    Proxy proxy = new Proxy("http", "localhost", server.getPort(), auth);
    builder.setProxy(proxy);
    RemoteRepository repository = builder.build();

    RepositorySystemSession repositorySystemSession = new DefaultRepositorySystemSession();
    AetherRepositoryConnectorFactory factory =
        new AetherRepositoryConnectorFactory(new DefaultFileProcessor());
    RepositoryConnector connector = factory.newInstance(repositorySystemSession, repository);

    Artifact artifact =
        new DefaultArtifact("gid", "aid", "classifier", "extension", "version", null);
    ArtifactDownload download =
        new ArtifactDownload(artifact, null, temp.newFile("test"),
            RepositoryPolicy.CHECKSUM_POLICY_FAIL);
    Collection<? extends ArtifactDownload> downloads = Arrays.asList(download);
    connector.get(downloads, null);

    Assert.assertTrue(String.valueOf(download.getException()),
        download.getException().getCause() instanceof AuthorizationException);
  }

}
