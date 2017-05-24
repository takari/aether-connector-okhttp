/*******************************************************************************
 * Copyright (c) 2012 Jason van Zyl
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package io.takari.aether.connector;

import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.RepositoryConnector;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.io.FileProcessor;
import org.eclipse.aether.spi.locator.Service;
import org.eclipse.aether.spi.locator.ServiceLocator;
import org.eclipse.aether.transfer.NoRepositoryConnectorException;
import org.eclipse.sisu.Nullable;

/**
 * A repository connector factory that uses OkHttp for the transfers.
 */
@Named("okhttp")
@Singleton
public final class AetherRepositoryConnectorFactory implements RepositoryConnectorFactory, Service {

  // see org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout
  private static final String LAYOUT_DEFAULT = "default";

  private FileProcessor fileProcessor;
  private final SSLSocketFactory sslSocketFactory;
  private final X509TrustManager trustManager;

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

  // Default constructor required for the service locator to work with you use
  // this factory outside the confines of Guice.
  public AetherRepositoryConnectorFactory() throws NoSuchAlgorithmException {
    this(null, null, null);
  }

  @Inject
  public AetherRepositoryConnectorFactory(FileProcessor fileProcessor, @Nullable SSLSocketFactory sslSocketFactory,
      @Nullable X509TrustManager trustManager) throws NoSuchAlgorithmException {
    this.fileProcessor = fileProcessor;

    // explicitly create new ssl socket factory if none is provided externally
    // this is necessary because okhttp default socket factory does not honour
    // javax.net.ssl.keyStore/trustStore system properties, which are the only
    // way
    // to use custom ket/trust stores in maven in m2e
    if (sslSocketFactory != null && trustManager != null) {
      this.sslSocketFactory = sslSocketFactory;
      this.trustManager = trustManager;
    } else {
      // can not use use jdk-default ssl socket factory like previous because
      // we can not get trust manager without reflection. So create new ssl
      // socket factory using system properties to create fake jdk-default
      // ssl socket factory.
      try {
        String trustManagerFactoryAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
        // Clearly specify TrustManagerFactory provider to SunJSSE.
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(trustManagerFactoryAlgorithm, "SunJSSE");
        trustManagerFactory.init((KeyStore) null);
        if (!(trustManagerFactory.getTrustManagers()[0] instanceof X509TrustManager))
          throw new UnsupportedOperationException("Only X509TrustManager supported by implementation");

        String keyStoreProviderProp = System.getProperty("javax.net.ssl.keyStoreProvider");
        String keyStoreTypeProp = System.getProperty("javax.net.ssl.keyStoreType", KeyStore.getDefaultType());
        String keyStoreProp = System.getProperty("javax.net.ssl.keyStore");
        String keyStorePasswordProp = System.getProperty("javax.net.ssl.keyStorePassword");

        // TODO more key store arguments combination check
        if (caseInsensitiveEequals(keyStoreTypeProp, "PKCS11") && caseInsensitiveEequals(keyStoreProp, "NONE"))
          throw new IllegalArgumentException(
              "Illegal key store arguments combination (keyStoreTypeProp PKCS11): keyStoreProp can not be NONE");

        char[] password = isEmpty(keyStorePasswordProp) ? null : keyStorePasswordProp.toCharArray();

        KeyStore keyStore;
        // If keyStoreTypeProp not specified by system property. leave key
        // store null.
        if (isEmpty(keyStoreTypeProp))
          keyStore = null;
        else {
          keyStore = isEmpty(keyStoreProviderProp) ? KeyStore.getInstance(keyStoreTypeProp)
              : KeyStore.getInstance(keyStoreTypeProp, keyStoreProviderProp);

          // Create FileInputStream from keyStoreProp if specified by system
          // property and not NONE.
          if (!isEmpty(keyStoreProp) && !caseInsensitiveEequals(keyStoreProp, "NONE")) {
            try (InputStream stream = new FileInputStream(keyStoreProp)) {
              keyStore.load(stream, password);
            }
          } else
            keyStore.load(null, password);
        }

        String keyManagerFactoryAlgorithm = KeyManagerFactory.getDefaultAlgorithm();
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(keyManagerFactoryAlgorithm);

        // When key store is PKCS11. Password is already used by token of key
        // store. So we should give null here. Otherwise it may throw an
        // exception.
        // See sun.security.pkcs11.P11KeyStore.engineGetKey(String, char[])
        keyManagerFactory.init(keyStore, caseInsensitiveEequals(keyStoreTypeProp, "PKCS11") ? null : password);

        TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();
        KeyManager[] keyManagers = keyManagerFactory.getKeyManagers();

        SSLContext sslContext = SSLContext.getInstance("SSL");
        sslContext.init(keyManagers, trustManagers, null);
        this.sslSocketFactory = sslContext.getSocketFactory();
        this.trustManager = (X509TrustManager) trustManagers[0];
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }

  private boolean caseInsensitiveEequals(String s1, String s2) {
    return (s1 == s2) || (s1 != null && s1.equalsIgnoreCase(s2));
  }

  private boolean isEmpty(String s) {
    return s == null || s.isEmpty();
  }

  public float getPriority() {
    return 100; // let other factories take over, if they want to
  }

  public RepositoryConnector newInstance(RepositorySystemSession repositorySystemSession,
      RemoteRepository remoteRepository) throws NoRepositoryConnectorException {
    if (!LAYOUT_DEFAULT.equals(remoteRepository.getContentType())) {
      throw new NoRepositoryConnectorException(remoteRepository);
    }
    ConnectorKey key = new ConnectorKey(remoteRepository);
    RepositoryConnector connector = (RepositoryConnector) repositorySystemSession.getData().get(key);
    if (connector == null) {
      connector = new AetherRepositoryConnector(remoteRepository, repositorySystemSession, fileProcessor,
          sslSocketFactory, trustManager);
      if (!repositorySystemSession.getData().set(key, null, connector)) {
        connector = (RepositoryConnector) repositorySystemSession.getData().get(key);
      }
    }
    return connector;
  }

  public void initService(ServiceLocator locator) {
    setFileProcessor(locator.getService(FileProcessor.class));
  }

  public void setFileProcessor(FileProcessor fileProcessor) {
    this.fileProcessor = fileProcessor;
  }
}
