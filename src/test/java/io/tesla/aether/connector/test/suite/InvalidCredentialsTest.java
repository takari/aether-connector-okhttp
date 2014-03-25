package io.tesla.aether.connector.test.suite;

import io.tesla.aether.connector.AuthorizationException;
import io.tesla.webserver.WebServer;

import java.io.File;
import java.util.Arrays;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.internal.test.util.TestFileUtils;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.spi.connector.ArtifactDownload;
import org.eclipse.aether.spi.connector.ArtifactUpload;

public class InvalidCredentialsTest extends AetherTestCase {

  public void testGet() throws Exception {
    File f = TestFileUtils.createTempFile("");
    Artifact a = artifact("bla");
    ArtifactDownload down =
        new ArtifactDownload(a, null, f, RepositoryPolicy.CHECKSUM_POLICY_IGNORE);
    connector().get(Arrays.asList(down), null);
    assertTrue(down.getException().getCause() instanceof AuthorizationException);
  }

  public void testPut() throws Exception {
    Artifact artifact = artifact("artifact");
    ArtifactUpload up = new ArtifactUpload(artifact, artifact.getFile());
    connector().put(Arrays.asList(up), null);
    assertTrue(up.getException().getCause() instanceof AuthorizationException);
  }

  @Override
  protected void configureTest() throws Exception {
    enableConnectingWithBasicAuth();
  }

  @Override
  protected void configureServer(WebServer server) {
    super.configureServer(server);
    server.addAuthentication("/*", "BASIC");
    server.addUser(USERNAME, PASSWORD + "-invalid");
  }
}
