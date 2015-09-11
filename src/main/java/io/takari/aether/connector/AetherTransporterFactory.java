package io.takari.aether.connector;

import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;

import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.AuthenticationContext;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.transport.Transporter;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transfer.NoTransporterException;
import org.eclipse.aether.util.ConfigUtils;
import org.eclipse.sisu.Nullable;

import io.takari.aether.client.AetherClientAuthentication;
import io.takari.aether.client.AetherClientConfig;
import io.takari.aether.client.AetherClientProxy;
import io.takari.aether.okhttp.OkHttpAetherClient;

@Named("okhttp")
@Singleton
public class AetherTransporterFactory implements TransporterFactory {

  private final SSLSocketFactory sslSocketFactory;

  private static final class ConnectorKey {
    private final RemoteRepository repository;

    public ConnectorKey(RemoteRepository repository) {
      this.repository = repository;
    }

    @Override
    public int hashCode() {
      return repository.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof ConnectorKey && repository.equals(((ConnectorKey) obj).repository);
    }
  }

  @Inject
  public AetherTransporterFactory(@Nullable SSLSocketFactory sslSocketFactory)
      throws NoSuchAlgorithmException {

    // explicitly use jdk-default ssl socket factory if none is provided externally
    // this is necessary because okhttp default socket factory does not honour
    // javax.net.ssl.keyStore/trustStore system properties, which are the only way
    // to use custom key/trust stores in maven in m2e
    this.sslSocketFactory =
        sslSocketFactory != null ? sslSocketFactory : SSLContext.getDefault().getSocketFactory();
  }

  @Override
  public Transporter newInstance(RepositorySystemSession session, RemoteRepository repository)
      throws NoTransporterException {
    final ConnectorKey key = new ConnectorKey(repository);
    OkHttpAetherClient aetherClient = (OkHttpAetherClient) session.getData().get(key);
    if (aetherClient == null) {
      aetherClient = newAetherClient(repository, session, sslSocketFactory);
      if (!session.getData().set(key, null, aetherClient)) {
        aetherClient = (OkHttpAetherClient) session.getData().get(key);
      }
    }
    return new AetherTransporter(repository.getUrl(), aetherClient);
  }

  @Override
  public float getPriority() {
    return Float.MAX_VALUE;
  }

  private static OkHttpAetherClient newAetherClient(RemoteRepository repository,
      RepositorySystemSession session, SSLSocketFactory sslSocketFactory) {
    AetherClientConfig config = new AetherClientConfig();

    Map<String, String> commonHeaders = new HashMap<>();

    @SuppressWarnings("unchecked")
    Map<String, String> headers = (Map<String, String>) ConfigUtils.getMap(session, null,
        ConfigurationProperties.HTTP_HEADERS + "." + repository.getId(),
        ConfigurationProperties.HTTP_HEADERS);
    if (headers != null) {
      commonHeaders.putAll(headers);
    }

    PlexusConfiguration wagonConfig = (PlexusConfiguration) ConfigUtils.getObject(session, null,
        "aether.connector.wagon.config" + "." + repository.getId());
    //
    // <configuration>
    // <httpHeaders>
    // <property>
    // <name>User-Agent</name>
    // <value>Maven Fu</value>
    // </property>
    // <property>
    // <name>Custom-Header</name>
    // <value>My wonderful header</value>
    // </property>
    // </httpHeaders>
    // </configuration>
    //
    if (wagonConfig != null) {
      PlexusConfiguration httpHeaders = wagonConfig.getChild("httpHeaders");
      if (httpHeaders != null) {
        PlexusConfiguration[] properties = httpHeaders.getChildren("property");
        if (properties != null) {
          for (PlexusConfiguration property : properties) {
            commonHeaders.put(property.getChild("name").getValue(),
                property.getChild("value").getValue());
          }
        }
      }
    }

    config.setHeaders(commonHeaders);
    config.setUserAgent(ConfigUtils.getString(session, ConfigurationProperties.DEFAULT_USER_AGENT,
        ConfigurationProperties.USER_AGENT));

    if (repository.getProxy() != null) {
      AetherClientProxy proxy = new AetherClientProxy();
      proxy.setHost(repository.getProxy().getHost());
      proxy.setPort(repository.getProxy().getPort());
      //
      // Proxy authorization
      //
      AuthenticationContext proxyAuthenticationContext =
          AuthenticationContext.forProxy(session, repository);
      if (proxyAuthenticationContext != null) {
        try {
          String username = proxyAuthenticationContext.get(AuthenticationContext.USERNAME);
          String password = proxyAuthenticationContext.get(AuthenticationContext.PASSWORD);
          proxy.setAuthentication(new AetherClientAuthentication(username, password));
        } finally {
          AuthenticationContext.close(proxyAuthenticationContext);
        }
      }
      config.setProxy(proxy);
    }

    //
    // Authorization
    //
    AuthenticationContext repoAuthenticationContext =
        AuthenticationContext.forRepository(session, repository);
    if (repoAuthenticationContext != null) {
      try {
        String username = repoAuthenticationContext.get(AuthenticationContext.USERNAME);
        String password = repoAuthenticationContext.get(AuthenticationContext.PASSWORD);
        AetherClientAuthentication authentication =
            new AetherClientAuthentication(username, password);
        config.setAuthentication(authentication);
      } finally {
        AuthenticationContext.close(repoAuthenticationContext);
      }
    }

    int connectTimeout = ConfigUtils.getInteger(session,
        ConfigurationProperties.DEFAULT_CONNECT_TIMEOUT, ConfigurationProperties.CONNECT_TIMEOUT);
    int readTimeout = ConfigUtils.getInteger(session,
        ConfigurationProperties.DEFAULT_REQUEST_TIMEOUT, ConfigurationProperties.REQUEST_TIMEOUT);

    config.setConnectionTimeout(connectTimeout);
    config.setRequestTimeout(readTimeout);
    config.setSslSocketFactory(sslSocketFactory);

    return new OkHttpAetherClient(config);
  }

}
