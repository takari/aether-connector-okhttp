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
package io.tesla.aether.connector;

import javax.inject.Inject;
import javax.inject.Named;
import javax.net.ssl.SSLSocketFactory;

import org.codehaus.plexus.component.annotations.Component;
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
@Named
@Component(role = RepositoryConnectorFactory.class, hint = "okhttp")
public final class AetherRepositoryConnectorFactory implements RepositoryConnectorFactory, Service {

  private FileProcessor fileProcessor;
  private SSLSocketFactory sslSocketFactory;

  @Inject
  public AetherRepositoryConnectorFactory(FileProcessor fileProcessor, @Nullable SSLSocketFactory sslSocketFactory) {
    this.fileProcessor = fileProcessor;
    this.sslSocketFactory = sslSocketFactory;
  }

  public float getPriority() {
    return Float.MAX_VALUE;
  }

  public RepositoryConnector newInstance(RepositorySystemSession repositorySystemSession, RemoteRepository remoteRepository) throws NoRepositoryConnectorException {
    if (sslSocketFactory != null) {
      return new AetherRepositoryConnector(remoteRepository, repositorySystemSession, fileProcessor, sslSocketFactory);
    } else {
      return new AetherRepositoryConnector(remoteRepository, repositorySystemSession, fileProcessor);
    }
  }

  public void initService(ServiceLocator locator) {
    setFileProcessor(locator.getService(FileProcessor.class));
  }

  public void setFileProcessor(FileProcessor fileProcessor) {
    this.fileProcessor = fileProcessor;
  }
}
