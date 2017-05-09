package io.takari.aether.connector.test.suite;

import javax.inject.Inject;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.internal.test.util.TestUtils;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.transfer.NoRepositoryConnectorException;
import org.eclipse.sisu.launch.InjectedTestCase;

public class AetherConnectorFactoryTest extends InjectedTestCase {

  @Inject
  protected RepositoryConnectorFactory testee;

  public void testUnsupportedRepositoryLayout() throws Exception {
    RemoteRepository repository = new RemoteRepository.Builder("test", "p2", "http://p2").build();
    DefaultRepositorySystemSession session = TestUtils.newSession();
    try {
      testee.newInstance(session, repository);
      fail();
    } catch (NoRepositoryConnectorException expected) {
      assertTrue(expected.getRepository() == repository);
    }
  }

}
