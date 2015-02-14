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

import java.security.NoSuchAlgorithmException;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;

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

  private FileProcessor fileProcessor;
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

  // Default constructor required for the service locator to work with you use this factory outside the confines of Guice.
  public  AetherRepositoryConnectorFactory() throws NoSuchAlgorithmException {
    this(null, null);
  }
  
  @Inject
  public AetherRepositoryConnectorFactory(FileProcessor fileProcessor, @Nullable SSLSocketFactory sslSocketFactory) throws NoSuchAlgorithmException {
    this.fileProcessor = fileProcessor;
    
    // explicitly use jdk-default ssl socket factory if none is provided externally
    // this is necessary because okhttp default socket factory does not honour
    // javax.net.ssl.keyStore/trustStore system properties, which are the only way
    // to use custom ket/trust stores in maven in m2e
    this.sslSocketFactory = sslSocketFactory != null? sslSocketFactory: SSLContext.getDefault().getSocketFactory();
  }

  public float getPriority() {
    return Float.MAX_VALUE;
  }

  public RepositoryConnector newInstance(RepositorySystemSession repositorySystemSession, RemoteRepository remoteRepository) throws NoRepositoryConnectorException {
    ConnectorKey key = new ConnectorKey(remoteRepository);
    RepositoryConnector connector = (RepositoryConnector) repositorySystemSession.getData().get(key);
    if (connector == null) {
      connector = new AetherRepositoryConnector(remoteRepository, repositorySystemSession, fileProcessor, sslSocketFactory);
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
