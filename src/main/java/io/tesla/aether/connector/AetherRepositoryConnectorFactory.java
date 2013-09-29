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

import org.codehaus.plexus.component.annotations.Component;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.RepositoryConnector;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.io.FileProcessor;
import org.eclipse.aether.spi.locator.Service;
import org.eclipse.aether.spi.locator.ServiceLocator;
import org.eclipse.aether.spi.log.LoggerFactory;
import org.eclipse.aether.spi.log.NullLoggerFactory;
import org.eclipse.aether.transfer.NoRepositoryConnectorException;

/**
 * A repository connector factory that uses OkHttp for the transfers.
 */
@Named
@Component(role = RepositoryConnectorFactory.class, hint = "okhttp")
public final class AetherRepositoryConnectorFactory implements RepositoryConnectorFactory, Service {

  private LoggerFactory loggerFactory;
  private FileProcessor fileProcessor;

  public AetherRepositoryConnectorFactory() {
  }

  @Inject
  public AetherRepositoryConnectorFactory(LoggerFactory loggerFactory, FileProcessor fileProcessor) {
    this.loggerFactory = loggerFactory;
    this.fileProcessor = fileProcessor;
  }

  public float getPriority() {
    return Float.MAX_VALUE;
  }

  public RepositoryConnector newInstance(RepositorySystemSession repositorySystemSession, RemoteRepository remoteRepository) throws NoRepositoryConnectorException {
    return new AetherRepositoryConnector(remoteRepository, repositorySystemSession, fileProcessor, NullLoggerFactory.getSafeLogger(loggerFactory, getClass()));
  }

  public void initService(ServiceLocator locator) {
    setLoggerFactory(locator.getService(LoggerFactory.class));
    setFileProcessor(locator.getService(FileProcessor.class));
  }

  public void setLoggerFactory(LoggerFactory loggerFactory) {
    this.loggerFactory = loggerFactory;
  }

  public void setFileProcessor(FileProcessor fileProcessor) {
    this.fileProcessor = fileProcessor;
  }

}
