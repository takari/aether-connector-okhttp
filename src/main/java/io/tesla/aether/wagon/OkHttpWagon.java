/**
 * Copyright (c) 2012 to original author or authors
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.tesla.aether.wagon;

import io.tesla.aether.client.AetherClient;
import io.tesla.aether.client.AetherClientAuthentication;
import io.tesla.aether.client.AetherClientConfig;
import io.tesla.aether.client.AetherClientProxy;
import io.tesla.aether.client.Response;
import io.tesla.aether.client.RetryableSource;
import io.tesla.aether.okhttp.OkHttpAetherClient;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.inject.Named;

import org.apache.maven.wagon.ConnectionException;
import org.apache.maven.wagon.InputData;
import org.apache.maven.wagon.OutputData;
import org.apache.maven.wagon.ResourceDoesNotExistException;
import org.apache.maven.wagon.StreamWagon;
import org.apache.maven.wagon.TransferFailedException;
import org.apache.maven.wagon.Wagon;
import org.apache.maven.wagon.authentication.AuthenticationException;
import org.apache.maven.wagon.authorization.AuthorizationException;
import org.apache.maven.wagon.events.TransferEvent;
import org.apache.maven.wagon.resource.Resource;
import org.codehaus.plexus.component.annotations.Component;
import org.eclipse.aether.ConfigurationProperties;

import com.google.common.io.Closer;

@Named("http")
@Component(role = Wagon.class, hint = "http", instantiationStrategy = "per-lookup")
public class OkHttpWagon extends StreamWagon {

  private Map<String,String> httpHeaders;
  
  private AetherClient client;

  @Override
  public void fillInputData(InputData inputData) throws TransferFailedException, ResourceDoesNotExistException, AuthorizationException {
    Resource resource = inputData.getResource();
    String url = buildUrl(resource.getName());
    try {
      Response response = client.get(url);
      inputData.setInputStream(response.getInputStream());
    } catch (IOException e) {
      StringBuilder message = new StringBuilder("Error transferring file: ");
      message.append(e.getMessage());
      message.append(" from " + url);
      if (getProxyInfo() != null && getProxyInfo().getHost() != null) {
        message.append(" with proxyInfo ").append(getProxyInfo().toString());
      }
      throw new TransferFailedException(message.toString(), e);
    }
  }

  @Override
  public void fillOutputData(OutputData outputData) throws TransferFailedException {
    throw new UnsupportedOperationException();
  }

  @Override
  protected OutputStream getOutputStream(Resource resource) throws TransferFailedException {
    throw new UnsupportedOperationException();
  }
 
  
  abstract class RetryableResource implements RetryableSource {
    private final Resource resource;

    protected RetryableResource(Resource resource) {
      this.resource = resource;
    }

    protected void copy(InputStream is, OutputStream os) throws IOException {
      TransferEvent transferEvent =
          new TransferEvent(OkHttpWagon.this, resource, TransferEvent.TRANSFER_PROGRESS,
              TransferEvent.REQUEST_PUT);
      transferEvent.setTimestamp(System.currentTimeMillis());
      Closer closer = Closer.create();
      try {
        closer.register(is);
        closer.register(os);
        int n = 0;
        final byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
        while (-1 != (n = is.read(buffer))) {
          write(os, n, buffer);
          fireTransferProgress(transferEvent, buffer, n);
        }
      } finally {
        closer.close();
      }
    }

    protected void write(OutputStream os, int n, final byte[] buffer) throws IOException {
      os.write(buffer, 0, n);
    }
  }

  class FileSource extends RetryableResource {
    private final File file;

    public FileSource(Resource resource, File file) {
      super( resource);
      this.file = file;
    }

    @Override
    public void copyTo(OutputStream os) throws IOException {
      copy(new FileInputStream(file), os);
    }

    @Override
    public long length() {
      return file.length();
    }
  }

  class InputStreamSource extends RetryableResource {

    private final InputStream is;

    public InputStreamSource(Resource resource, InputStream is) {
      super(resource);
      this.is = is;
    }

    @Override
    public void copyTo(OutputStream os) throws IOException {
      copy(is, os);
    }

    @Override
    protected void write(OutputStream os, int n, byte[] buffer) throws IOException {
      super.write(os, n, buffer);
    }
    
    @Override
    public long length() {
      return -1; // unknown
    }
  }

  @Override
  public void put(File file, String resourceName) throws TransferFailedException,
      ResourceDoesNotExistException, AuthorizationException {
    Resource resource = new Resource(resourceName);

    firePutInitiated(resource, file);

    resource.setContentLength(file.length());

    resource.setLastModified(file.lastModified());

    RetryableSource source = new FileSource(resource, file);

    put(source, file, resource);
  }

  @Override
  protected void putFromStream(InputStream stream, Resource resource)
      throws TransferFailedException, AuthorizationException, ResourceDoesNotExistException {
    put(new InputStreamSource(resource, stream), null, resource);
  }

  private void put(RetryableSource source, File file, Resource resource) throws TransferFailedException {
    firePutStarted( resource, file );

    String url = buildUrl(resource.getName());

    try {
      Response response = client.put(url, source);
      // TODO do I need to worry about response status?
    } catch (FileNotFoundException e) {
      fireTransferError(resource, e, TransferEvent.REQUEST_PUT);
      throw new TransferFailedException("Specified source file does not exist: " + source, e);
    } catch (IOException e) {
      fireTransferError(resource, e, TransferEvent.REQUEST_PUT);
      String msg =
          "PUT request to: " + resource.getName() + " in " + repository.getName() + " failed";
      throw new TransferFailedException(msg, e);
    }

    firePutCompleted( resource, file );
  }
  
  @Override
  public void closeConnection() throws ConnectionException {
    if (client != null) {
      try {
        client.close();
      } catch (IOException e) {
        throw new ConnectionException(e.getMessage(), e);
      }
    }
  }

  @Override
  protected void openConnectionInternal() throws ConnectionException, AuthenticationException {

    AetherClientConfig config = new AetherClientConfig();
    config.setUserAgent("Maven-Wagon/1.0");

    // headers
    if (httpHeaders != null) {
      config.setHeaders(httpHeaders);
    }

    if (getProxyInfo() != null) {
      AetherClientProxy proxy = new AetherClientProxy();
      proxy.setHost(getProxyInfo().getHost());
      proxy.setPort(getProxyInfo().getPort());
      //
      // Proxy authorization
      //
      if (getProxyInfo().getUserName() != null && getProxyInfo().getPassword() != null) {
        String username = getProxyInfo().getUserName();
        String password = getProxyInfo().getPassword();
        proxy.setAuthentication(new AetherClientAuthentication(username, password));
      }
      config.setProxy(proxy);
    }

    //
    // Authorization
    //
    if (getAuthenticationInfo() != null) {
      String username = getAuthenticationInfo().getUserName();
      String password = getAuthenticationInfo().getPassword();
      AetherClientAuthentication authentication = new AetherClientAuthentication(username, password);
      config.setAuthentication(authentication);
    }

    config.setConnectionTimeout(ConfigurationProperties.DEFAULT_CONNECT_TIMEOUT);
    config.setRequestTimeout(ConfigurationProperties.DEFAULT_REQUEST_TIMEOUT);

    client = new OkHttpAetherClient(config);
  }

  @Override
  public boolean resourceExists(String resourceName) throws TransferFailedException, AuthorizationException {

    try {
      String url = buildUrl(resourceName);
      Response response = client.head(url);
      int statusCode = response.getStatusCode();

      switch (statusCode) {
      case HttpURLConnection.HTTP_OK:
        return true;

      case HttpURLConnection.HTTP_FORBIDDEN:
        throw new AuthorizationException("Access denied to: " + url);

      case HttpURLConnection.HTTP_NOT_FOUND:
        return false;

      case HttpURLConnection.HTTP_UNAUTHORIZED:
        throw new AuthorizationException("Access denied to: " + url);

      default:
        throw new TransferFailedException("Failed to look for file: " + buildUrl(resourceName) + ". Return code is: " + statusCode);
      }
    } catch (IOException e) {
      throw new TransferFailedException("Error transferring file: " + e.getMessage(), e);
    }
  }

  private String buildUrl(String path) {
    String repoUrl = getRepository().getUrl();
    path = path.replace(' ', '+');
    if (repoUrl.charAt(repoUrl.length() - 1) != '/') {
      return repoUrl + '/' + path;
    }
    return repoUrl + path;
  }

  void setSystemProperty(String key, String value) {
    if (value != null) {
      System.setProperty(key, value);
    } else {
      System.getProperties().remove(key);
    }
  }

  public void setHttpHeaders(Properties httpHeaders) {
    Map<String,String> map = new HashMap<String, String>();
    for (Map.Entry<Object,Object> header : httpHeaders.entrySet()) {
      if (header.getKey() instanceof String && header.getValue() instanceof String) {
        map.put((String) header.getKey(), (String) header.getValue());
      }
    }
    this.httpHeaders = Collections.unmodifiableMap(map);
  }

}
