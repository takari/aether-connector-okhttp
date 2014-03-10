/**
 * Copyright (c) 2012 to original author or authors
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.tesla.aether.connector;

//TODO remove custom exceptions
//TODO only allow auth over SSL
//TODO make it easy to use SSL with Nexus, make Nexus a key issuing authority
//TODO protocol with repo managers
//TODO make the layout pluggable
//TODO Signature validation
//TODO TEST NTLM
//TODO Certificate base auth

// Encoding credentials for basic auth
// http://tools.ietf.org/id/draft-reschke-basicauth-enc-00.html

// Encoding
// http://www.joelonsoftware.com/articles/Unicode.html

// Resumable downloads
// http://zoompf.com/2010/03/performance-tip-for-http-downloads
// http://stackoverflow.com/questions/6237079/resume-http-file-download-in-java

import io.tesla.aether.client.AetherClient;
import io.tesla.aether.client.AetherClientAuthentication;
import io.tesla.aether.client.AetherClientConfig;
import io.tesla.aether.client.AetherClientProxy;
import io.tesla.aether.client.Response;
import io.tesla.aether.okhttp.OkHttpAetherClient;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.SSLSocketFactory;

import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.AuthenticationContext;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.spi.connector.ArtifactDownload;
import org.eclipse.aether.spi.connector.ArtifactTransfer;
import org.eclipse.aether.spi.connector.ArtifactUpload;
import org.eclipse.aether.spi.connector.MetadataDownload;
import org.eclipse.aether.spi.connector.MetadataTransfer;
import org.eclipse.aether.spi.connector.MetadataUpload;
import org.eclipse.aether.spi.connector.RepositoryConnector;
import org.eclipse.aether.spi.connector.Transfer;
import org.eclipse.aether.spi.io.FileProcessor;
//import org.eclipse.aether.spi.log.Logger;
import org.eclipse.aether.transfer.ArtifactNotFoundException;
import org.eclipse.aether.transfer.ArtifactTransferException;
import org.eclipse.aether.transfer.ChecksumFailureException;
import org.eclipse.aether.transfer.MetadataNotFoundException;
import org.eclipse.aether.transfer.MetadataTransferException;
import org.eclipse.aether.transfer.NoRepositoryConnectorException;
import org.eclipse.aether.transfer.TransferEvent;
import org.eclipse.aether.transfer.TransferEvent.EventType;
import org.eclipse.aether.transfer.TransferEvent.RequestType;
import org.eclipse.aether.transfer.TransferListener;
import org.eclipse.aether.transfer.TransferResource;
import org.eclipse.aether.util.ChecksumUtils;
import org.eclipse.aether.util.ConfigUtils;
import org.eclipse.aether.util.repository.layout.MavenDefaultLayout;
import org.eclipse.aether.util.repository.layout.RepositoryLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.ByteStreams;
import com.google.common.io.Closer;

class AetherRepositoryConnector implements RepositoryConnector {

  private final Logger logger = LoggerFactory.getLogger(AetherRepositoryConnector.class);
  
  private final RepositoryLayout layout = new MavenDefaultLayout();
  private final TransferListener listener;
  private final RepositorySystemSession session;
  private final AuthenticationContext repoAuthenticationContext;
  private final AuthenticationContext proxyAuthenticationContext;
  private final FileProcessor fileProcessor;
  private final RemoteRepository repository;

  private final Map<String, String> checksumAlgos;
  private final AtomicBoolean closed = new AtomicBoolean(false);
  private boolean useCache = true;
  private Map<String, String> commonHeaders;

  private AetherClient aetherClient;

  public AetherRepositoryConnector(RemoteRepository repository, RepositorySystemSession session, FileProcessor fileProcessor) throws NoRepositoryConnectorException {
    this(repository, session, fileProcessor, null);
  }

  public AetherRepositoryConnector(RemoteRepository repository, RepositorySystemSession session, FileProcessor fileProcessor, SSLSocketFactory sslSocketFactory) throws NoRepositoryConnectorException {
    //
    // Right now this only support a Maven layout which is what we mean by type
    //
    if (!"default".equals(repository.getContentType())) {
      throw new NoRepositoryConnectorException(repository);
    }

    if (!repository.getProtocol().regionMatches(true, 0, "http", 0, "http".length())) {
      throw new NoRepositoryConnectorException(repository);
    }

    //this.logger = logger;
    this.repository = repository;
    this.listener = session.getTransferListener();
    this.fileProcessor = fileProcessor;
    this.session = session;

    AetherClientConfig config = new AetherClientConfig();

    repoAuthenticationContext = AuthenticationContext.forRepository(session, repository);
    proxyAuthenticationContext = AuthenticationContext.forProxy(session, repository);

    commonHeaders = new HashMap<String, String>();
    Map<String, String> headers = (Map<String, String>) ConfigUtils.getMap(session, null, ConfigurationProperties.HTTP_HEADERS + "." + repository.getId(), ConfigurationProperties.HTTP_HEADERS);
    if (headers != null) {
      this.commonHeaders.putAll(headers);
    }

    PlexusConfiguration wagonConfig = (PlexusConfiguration) ConfigUtils.getObject(session, null, "aether.connector.wagon.config" + "." + repository.getId());
    //
    //  <configuration>
    //    <httpHeaders>
    //      <property>
    //        <name>User-Agent</name>                                                                                                               
    //        <value>Maven Fu</value>                                                                                                                                                            
    //      </property>                                                                                                                                                                        
    //      <property>
    //        <name>Custom-Header</name>                                                                                                                                               
    //        <value>My wonderful header</value>                                                                                                                                                 
    //      </property>                                                                                                                                                                        
    //    </httpHeaders>                                                                                                                                                                     
    //  </configuration>
    //
    if (wagonConfig != null) {
      PlexusConfiguration httpHeaders = wagonConfig.getChild("httpHeaders");
      if (httpHeaders != null) {
        PlexusConfiguration[] properties = httpHeaders.getChildren("property");
        if (properties != null) {
          for (PlexusConfiguration property : properties) {
            commonHeaders.put(property.getChild("name").getValue(), property.getChild("value").getValue());
          }
        }
      }
    }

    config.setHeaders(commonHeaders);
    config.setUserAgent(ConfigUtils.getString(session, ConfigurationProperties.DEFAULT_USER_AGENT, ConfigurationProperties.USER_AGENT));

    checksumAlgos = new LinkedHashMap<String, String>();
    checksumAlgos.put("SHA-1", ".sha1");
    checksumAlgos.put("MD5", ".md5");

    if (repository.getProxy() != null) {
      AetherClientProxy proxy = new AetherClientProxy();
      proxy.setHost(repository.getProxy().getHost());
      proxy.setPort(repository.getProxy().getPort());
      //
      // Proxy authorization
      //
      if (proxyAuthenticationContext != null) {
        String username = proxyAuthenticationContext.get(AuthenticationContext.USERNAME);
        String password = proxyAuthenticationContext.get(AuthenticationContext.PASSWORD);
        proxy.setAuthentication(new AetherClientAuthentication(username, password));
      }
      config.setProxy(proxy);
    }

    //
    // Authorization
    //
    if (repoAuthenticationContext != null) {
      String username = repoAuthenticationContext.get(AuthenticationContext.USERNAME);
      String password = repoAuthenticationContext.get(AuthenticationContext.PASSWORD);
      AetherClientAuthentication authentication = new AetherClientAuthentication(username, password);
      config.setAuthentication(authentication);
    }

    int connectTimeout = ConfigUtils.getInteger(session, ConfigurationProperties.DEFAULT_CONNECT_TIMEOUT, ConfigurationProperties.CONNECT_TIMEOUT);
    int readTimeout = ConfigUtils.getInteger(session, ConfigurationProperties.DEFAULT_REQUEST_TIMEOUT, ConfigurationProperties.REQUEST_TIMEOUT);

    config.setConnectionTimeout(connectTimeout);
    config.setRequestTimeout(readTimeout);
    config.setSslSocketFactory(sslSocketFactory);

    aetherClient = new OkHttpAetherClient(config);

  }

  /**
   * Download artifacts and metadata.
   *
   * @param artifactDownloads The artifact downloads to perform, may be {@code null} or empty.
   * @param metadataDownloads The metadata downloads to perform, may be {@code null} or empty.
   */
  public void get(Collection<? extends ArtifactDownload> artifactDownloads, Collection<? extends MetadataDownload> metadataDownloads) {

    if (closed.get()) {
      throw new IllegalStateException("connector closed");
    }

    artifactDownloads = safe(artifactDownloads);
    metadataDownloads = safe(metadataDownloads);

    CountDownLatch latch = new CountDownLatch(artifactDownloads.size() + metadataDownloads.size());

    Collection<GetTask<?>> tasks = new ArrayList<GetTask<?>>();

    for (MetadataDownload download : metadataDownloads) {
      String resource = layout.getPath(download.getMetadata()).getPath();
      GetTask<?> task = new GetTask<MetadataTransfer>(resource, download.getFile(), download.getChecksumPolicy(), latch, download, METADATA);
      tasks.add(task);
      task.run();
    }

    for (ArtifactDownload download : artifactDownloads) {
      String resource = layout.getPath(download.getArtifact()).getPath();
      GetTask<?> task = new GetTask<ArtifactTransfer>(resource, download.isExistenceCheck() ? null : download.getFile(), download.getChecksumPolicy(), latch, download, ARTIFACT);
      tasks.add(task);
      task.run();
    }

    await(latch);

    for (GetTask<?> task : tasks) {
      task.flush();
    }
  }

  /**
   * Use the async http client library to upload artifacts and metadata.
   *
   * @param artifactUploads The artifact uploads to perform, may be {@code null} or empty.
   * @param metadataUploads The metadata uploads to perform, may be {@code null} or empty.
   */
  public void put(Collection<? extends ArtifactUpload> artifactUploads, Collection<? extends MetadataUpload> metadataUploads) {

    if (closed.get()) {
      throw new IllegalStateException("connector closed");
    }

    artifactUploads = safe(artifactUploads);
    metadataUploads = safe(metadataUploads);

    CountDownLatch latch = new CountDownLatch(artifactUploads.size() + metadataUploads.size());

    Collection<PutTask<?>> tasks = new ArrayList<PutTask<?>>();

    for (ArtifactUpload upload : artifactUploads) {
      String path = layout.getPath(upload.getArtifact()).getPath();
      PutTask<?> task = new PutTask<ArtifactTransfer>(path, upload.getFile(), latch, upload, ARTIFACT);
      tasks.add(task);
      task.run();
    }

    for (MetadataUpload upload : metadataUploads) {
      String path = layout.getPath(upload.getMetadata()).getPath();
      PutTask<?> task = new PutTask<MetadataTransfer>(path, upload.getFile(), latch, upload, METADATA);
      tasks.add(task);
      task.run();
    }

    await(latch);

    for (PutTask<?> task : tasks) {
      task.flush();
    }
  }

  private void await(CountDownLatch latch) {
    boolean interrupted = false;
    while (latch.getCount() > 0) {
      try {
        latch.await();
      } catch (InterruptedException e) {
        interrupted = true;
      }
    }
    if (interrupted) {
      Thread.currentThread().interrupt();
    }
  }

  private void handleResponseCode(String url, int responseCode, String responseMsg) throws AuthorizationException, ResourceDoesNotExistException, TransferException {
    if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
      throw new ResourceDoesNotExistException(String.format("Unable to locate resource %s. Error code %s", url, responseCode));
    }

    if (responseCode == HttpURLConnection.HTTP_FORBIDDEN || responseCode == HttpURLConnection.HTTP_UNAUTHORIZED || responseCode == HttpURLConnection.HTTP_PROXY_AUTH) {
      throw new AuthorizationException(String.format("Access denied to %s. Error code %s, %s", url, responseCode, responseMsg));
    }

    if (responseCode >= HttpURLConnection.HTTP_MULT_CHOICE) {
      throw new TransferException(String.format("Failed to transfer %s. Error code %s, %s", url, responseCode, responseMsg));
    }
  }

  private TransferEvent.Builder newEvent(TransferResource resource, TransferEvent.RequestType requestType, TransferEvent.EventType eventType) {
    return newEvent(resource, null, requestType, eventType);
  }

  private TransferEvent.Builder newEvent(TransferResource resource, Exception e, TransferEvent.RequestType requestType, TransferEvent.EventType eventType) {
    TransferEvent.Builder event = new TransferEvent.Builder(session, resource);
    event.setType(eventType);
    event.setRequestType(requestType);
    event.setException(e);
    return event;
  }

  class GetTask<T extends Transfer> implements Runnable {

    private final T download;
    private final String path;
    private final File fileInLocalRepository;
    private final String checksumPolicy;
    private final LatchGuard latch;
    private volatile Exception exception;
    private final ExceptionWrapper<T> wrapper;

    public GetTask(String path, File fileInLocalRepository, String checksumPolicy, CountDownLatch latch, T download, ExceptionWrapper<T> wrapper) {
      this.path = path;
      this.fileInLocalRepository = fileInLocalRepository;
      this.checksumPolicy = checksumPolicy;
      this.latch = new LatchGuard(latch);
      this.download = download;
      this.wrapper = wrapper;
    }

    public T getDownload() {
      return download;
    }

    public Exception getException() {
      return exception;
    }

    public void run() {

      download.setState(Transfer.State.ACTIVE);
      String uri = buildUrl(path);
      TransferResource transferResource = new TransferResource(repository.getUrl(), path, fileInLocalRepository, download.getTrace());

      try {

        if (listener != null) {
          listener.transferInitiated(newEvent(transferResource, RequestType.GET, EventType.INITIATED).build());
        }

        // Aether sends a request to the connector for the content to retrieve from a given URL and the file on the local file system
        // to populate with the downloaded content. It is up to the connector to prevent collisions of multiple processes downloading
        // the same file: a simple strategy for mitigating this is for the connector to create a randomly named temporary file that
        // sits along side the file that will be.
        //
        // Also note that if the concurrent safe local repository implementation is used then all operations, even across JVMs, will
        // block while a particular artifact is being downloaded. So if five builds jobs are retrieving the same very large file it
        // will only be downloaded once and all operations will use that file.
        //
        // A file download is only considered successful if it successfully downloads and all validation operations succeed. When we
        // a successful operation the temporary file is atomically renamed to the actual file.
        //
        // If the final download path is:
        //
        // ${HOME}/.m2/repository/io/tesla/tesla/4/pom.xml
        //
        // Then the temporary file that corresponds to that file looks like this:
        //
        // ${HOME}/.m2/repository/io/tesla/tesla/4/aether-737f90e4cfa047e3-pom.xml-in-progress

        if (fileInLocalRepository != null) {
          fileProcessor.mkdirs(fileInLocalRepository.getParentFile());
        } else {
          if (!resourceExist(uri)) {
            throw new ResourceDoesNotExistException("Could not find " + uri + " in " + repository.getUrl());
          }
          latch.countDown();
          return;
        }

        FileTransfer temporaryFileInLocalRepository = resumableGet(uri, fileInLocalRepository, transferResource, RequestType.GET, listener);

        //
        // The file has now been successfully downloaded so let's perform any validations required
        // like checksum validation and signature validation. We will only move the temporary file over
        // to the realFile if all the validations are successful.
        //
        validateChecksums(temporaryFileInLocalRepository.file, fileInLocalRepository, uri, transferResource);

        //
        // Only if the checksum handling succeeds will the temporary file be moved to the real file. The contents of the file are not
        // available if there is a checksum handling failure.
        //
        rename(temporaryFileInLocalRepository.file, fileInLocalRepository);

        if (listener != null) {
          listener.transferSucceeded(newEvent(transferResource, RequestType.GET, EventType.SUCCEEDED).setTransferredBytes(temporaryFileInLocalRepository.bytesTransferred).build());
        }
      } catch (Throwable t) {
        if (Exception.class.isAssignableFrom(t.getClass())) {
          exception = Exception.class.cast(t);
        } else {
          exception = new Exception(t);
        }
        if (listener != null) {
          listener.transferFailed(newEvent(transferResource, exception, RequestType.GET, EventType.FAILED).build());
        }
      } finally {
        latch.countDown();
      }
    }

    class FileTransfer {
      FileTransfer(File file, long bytesTransferred) {
        this.file = file;
        this.bytesTransferred = bytesTransferred;
      }

      File file;
      long bytesTransferred;
    }

    private boolean resourceExist(String uri) throws IOException {
      if (aetherClient.head(uri).getStatusCode() == 200) {
        return true;
      }
      return false;
    }

    //
    // Checksum handling
    //
    private void validateChecksums(File temporaryFileInLocalRepository, File fileInLocalRepository, String uri, TransferResource transferResource) throws Exception {

      boolean failOnInvalidOrMissingCheckums = RepositoryPolicy.CHECKSUM_POLICY_FAIL.equals(checksumPolicy);

      try {
        Map<String, Object> checksums = ChecksumUtils.calc(temporaryFileInLocalRepository, checksumAlgos.keySet());
        //
        // SHA1
        //
        if (!verifyChecksum(temporaryFileInLocalRepository, fileInLocalRepository, uri, (String) checksums.get("SHA-1"), ".sha1")) {
          throw new ChecksumFailureException("Checksum validation failed" + ", no checksums available from the repository");
        }
        //
        // MD5
        //
        if (!verifyChecksum(temporaryFileInLocalRepository, fileInLocalRepository, uri, (String) checksums.get("MD5"), ".md5")) {
          throw new ChecksumFailureException("Checksum validation failed" + ", no checksums available from the repository");
        }
      } catch (Exception e) {
        if (listener != null) {
          listener.transferCorrupted(newEvent(transferResource, e, RequestType.GET, EventType.CORRUPTED).build());
        }
        if (failOnInvalidOrMissingCheckums) {
          if (listener != null) {
            listener.transferFailed(newEvent(transferResource, e, RequestType.GET, EventType.FAILED).build());
          }
          throw e;
        }
      }
    }

    /**
     * 
     * @param temporaryFileInLocalRepository The in-progress name of the resource being downloaded e.g. ${localRepo}/io/tesla/maven/maven-core/3.1.2/aether-90e2b299-3604-4504-b13b-dc147f001c1e-maven-core-3.1.2.jar-in-progress
     * @param fileInLocalRepository The name of the completed name of the resource being downloaded e.g. ${localRepo}/io/tesla/maven/maven-core/3.1.2/maven-core-3.1.2.jar
     * @param uri The URI of the resource in the remote repository e.g. http://repo1.maven.org/maven2/io/tesla/maven/maven-core/3.1.2/maven-core-3.1.2.jar
     * @param actual The calculated checksum of the file e.g. 724036fb069c47ccc1e27b370f99f6f10069e34a
     * @param ext The type of the checksum like .sha1 or .md5
     * @return Whether the checksum file remotely matches the locally calculated checksum
     * @throws ChecksumFailureException
     */
    private boolean verifyChecksum(File temporaryFileInLocalRepository, File fileInLocalRepository, String uri, String actual, String ext) throws ChecksumFailureException {

      String checksumUri = uri + ext;
      // ${localRepo}/io/tesla/maven/maven-core/3.1.2/maven-core-3.1.2.jar + ".sha1"
      File checksumFileInLocalRepository = new File(temporaryFileInLocalRepository.getParentFile(), fileInLocalRepository.getName() + ext);
      TransferResource transferResource = new TransferResource(repository.getUrl(), checksumUri, checksumFileInLocalRepository, download.getTrace());

      try {

        FileTransfer temporaryChecksumFile = resumableGet(checksumUri, checksumFileInLocalRepository, transferResource, RequestType.GET, null);
        String expected = ChecksumUtils.read(temporaryChecksumFile.file);
        if (!expected.equalsIgnoreCase(actual)) {
          throw new ChecksumFailureException(expected, actual);
        }

        rename(temporaryChecksumFile.file, checksumFileInLocalRepository);

      } catch (Exception e) {
        throw new ChecksumFailureException(e);
      }

      return true;
    }

    private FileTransfer resumableGet(String uri, File fileInLocalRepository, TransferResource transferResource, RequestType requestType, TransferListener listener) throws Exception {

      long bytesTransferred = 0;

      boolean downloadSuccessful = false;
      boolean resumeDownloadInProgress = false;
      File temporaryFileInLocalRepository = null;

      //
      // Need to distinguish between client side failure and server side failure
      //      
      for (int retries = 0; retries < 10; retries++) {
        File[] files = fileInLocalRepository.getParentFile().listFiles();
        for (File inProgress : files) {
          //
          // ${HOME}/.m2/repository/io/tesla/tesla/4/aether-737f90e4cfa047e3-pom.xml-in-progress
          //
          if (inProgress.getName().startsWith("aether") && inProgress.getName().endsWith(fileInLocalRepository.getName() + "-in-progress")) {
            temporaryFileInLocalRepository = inProgress;
            resumeDownloadInProgress = true;
            break;
          }
        }

        if (temporaryFileInLocalRepository == null) {
          temporaryFileInLocalRepository = getTmpFile(fileInLocalRepository.getPath());
        }

        //JVZ: this all needs to be moved up to the client

        Response response;

        if (resumeDownloadInProgress) {
          Map<String, String> requestHeaders = new HashMap<String, String>();
          requestHeaders.put("Range", "bytes=" + temporaryFileInLocalRepository.length() + "-");
          requestHeaders.put("Accept-Encoding", "identity");
          response = aetherClient.get(uri, requestHeaders);
        } else {
          response = aetherClient.get(uri);
        }

        handleResponseCode(uri, response.getStatusCode(), response.getStatusMessage());
        //
        // We need to check to see if the server supports the Range header. We should see a response
        // that looks like the following:
        //
        // 206 Partial Content
        // Content-Type: video/mp4
        // Content-Length: 64656927
        // Accept-Ranges: bytes
        // Content-Range: bytes 100-64656926/64656927
        //
        //
        // If we are going to resume a download, the server needs to respond with a 206 and say it accepts ranges
        //                
        if (resumeDownloadInProgress && response.getHeader("Accept-Ranges") == null && response.getStatusCode() == HttpURLConnection.HTTP_OK) {
          //
          // The server does not support ranges so delete the temporary file and start over.
          //
          temporaryFileInLocalRepository.delete();
        }

        if (listener != null) {
          String contentLength = response.getHeader("Content-Length");
          if (contentLength != null) {
            long length = Long.parseLong(contentLength);
            transferResource.setContentLength(length);
            listener.transferStarted(newEvent(transferResource, null, requestType, EventType.STARTED).setTransferredBytes(bytesTransferred).build());
          }
        }

        Closer closer = Closer.create();
        final byte[] buffer = new byte[1024 * 1024];
        int n = 0;
        try {
          InputStream is = closer.register(response.getInputStream());
          OutputStream os = closer.register(new BufferedOutputStream(new FileOutputStream(temporaryFileInLocalRepository, resumeDownloadInProgress)));
          while (-1 != (n = is.read(buffer))) {
            os.write(buffer, 0, n);
            if (listener != null) {
              listener.transferProgressed(newEvent(transferResource, null, requestType, EventType.PROGRESSED).setTransferredBytes(n).setDataBuffer(buffer, 0, n).build());
            }
            bytesTransferred = bytesTransferred + n;
          }
          //
          // No interruptions in the download so we have transferred all the bytes
          //
          downloadSuccessful = true;

        } catch (IOException e) {
          exception = e;
        } finally {
          closer.close();
          if (downloadSuccessful) {
            exception = null;
            break;
          }
        }
      }

      //
      // After all our retry attempts if we are still in an exception state then throw the exception
      //
      if (exception != null) {
        throw exception;
      }

      return new FileTransfer(temporaryFileInLocalRepository, bytesTransferred);
    }

    public void flush() {
      wrapper.wrap(download, exception, repository);
      download.setState(Transfer.State.DONE);
    }

    private void rename(File from, File to) throws IOException {
      fileProcessor.move(from, to);
    }
  }

  class PutTask<T extends Transfer> implements Runnable {

    private final T upload;
    private final ExceptionWrapper<T> wrapper;
    private final String path;
    private final File file;
    private volatile Exception exception;
    private final LatchGuard latch;

    public PutTask(String path, File file, CountDownLatch latch, T upload, ExceptionWrapper<T> wrapper) {
      this.path = path;
      this.file = file;
      this.upload = upload;
      this.wrapper = wrapper;
      this.latch = new LatchGuard(latch);
    }

    public Exception getException() {
      return exception;
    }

    public void run() {

      upload.setState(Transfer.State.ACTIVE);
      final TransferResource transferResource = new TransferResource(repository.getUrl(), path, file, upload.getTrace());

      long bytesTransferred = 0;

      try {
        String uri = buildUrl(path);

        if (listener != null) {
          listener.transferInitiated(newEvent(transferResource, exception, RequestType.PUT, EventType.INITIATED).build());
        }

        Response response = aetherClient.put(uri);

        if (listener != null) {
          transferResource.setContentLength(file.length());
          listener.transferStarted(newEvent(transferResource, null, RequestType.PUT, EventType.STARTED).setTransferredBytes(bytesTransferred).build());
        }

        Closer closer = Closer.create();
        try {
          InputStream is = closer.register(new FileInputStream(file));
          OutputStream os = closer.register(new BufferedOutputStream(response.getOutputStream()));
          int n = 0;
          final byte[] buffer = new byte[4 * 1024];
          while (-1 != (n = is.read(buffer))) {
            os.write(buffer, 0, n);
            if (listener != null) {
              listener.transferProgressed(newEvent(transferResource, null, RequestType.PUT, EventType.PROGRESSED).setTransferredBytes(bytesTransferred).setDataBuffer(buffer, 0, n).build());
            }
            bytesTransferred = bytesTransferred + n;
          }
        } catch (IOException e) {
          exception = e;
        } finally {
          closer.close();
        }

        int statusCode = response.getStatusCode();
        if (statusCode >= HttpURLConnection.HTTP_BAD_REQUEST) {
          throw new TransferException(String.format("Checksum failed for %s with status code %s", uri, "RESPONSE" == null ? HttpURLConnection.HTTP_INTERNAL_ERROR : statusCode));
        }

        if (listener != null) {
          listener.transferSucceeded(newEvent(transferResource, null, RequestType.PUT, EventType.SUCCEEDED).setTransferredBytes(bytesTransferred).build());
        }

        //
        // Send up the checksums
        //
        uploadChecksums(file, uri);

      } catch (Exception e) {
        try {
          exception = e;
        } finally {
          if (listener != null) {
            listener.transferFailed(newEvent(transferResource, exception, RequestType.PUT, EventType.FAILED).build());
          }
        }
      } finally {
        latch.countDown();
      }
    }

    public void flush() {
      wrapper.wrap(upload, exception, repository);
      upload.setState(Transfer.State.DONE);
    }

    private void uploadChecksums(File file, String uri) {
      try {
        Map<String, Object> checksums = ChecksumUtils.calc(file, checksumAlgos.keySet());
        for (Map.Entry<String, Object> entry : checksums.entrySet()) {
          uploadChecksum(file, uri, entry.getKey(), entry.getValue());
        }
      } catch (IOException e) {
        logger.debug("Failed to upload checksums for " + file + ": " + e.getMessage(), e);
      }
    }
  }

  private void uploadChecksum(File file, String uri, String algo, Object checksum) throws IOException {

    Closer closer = Closer.create();
    try {
      if (checksum instanceof Exception) {
        throw (Exception) checksum;
      }

      String ext = checksumAlgos.get(algo);
      Response response = aetherClient.put(uri + ext);

      InputStream is = new ByteArrayInputStream(String.valueOf(checksum).getBytes("UTF-8"));
      OutputStream os = response.getOutputStream();
      ByteStreams.copy(is, os);

      int statusCode = response.getStatusCode();
      if (statusCode >= HttpURLConnection.HTTP_BAD_REQUEST) {
        throw new TransferException(String.format("Checksum failed for %s with status code %s", uri + ext, "RESPONSE" == null ? HttpURLConnection.HTTP_INTERNAL_ERROR : statusCode));
      }
    } catch (Exception e) {
      String msg = "Failed to upload " + algo + " checksum for " + file + ": " + e.getMessage();
      if (logger.isDebugEnabled()) {
        logger.warn(msg, e);
      } else {
        logger.warn(msg);
      }
    } finally {
      closer.close();
    }
  }

  /**
   * Builds a complete URL string from the repository URL and the relative path passed.
   *
   * @param path the relative path
   * @return the complete URL
   */
  private String buildUrl(String path) {
    final String repoUrl = repository.getUrl();
    path = path.replace(' ', '+');

    if (repoUrl.charAt(repoUrl.length() - 1) != '/') {
      return repoUrl + '/' + path;
    }
    return repoUrl + path;
  }

  static interface ExceptionWrapper<T> {
    void wrap(T transfer, Exception e, RemoteRepository repository);
  }

  public void close() {
    closed.set(true);
    AuthenticationContext.close(repoAuthenticationContext);
    AuthenticationContext.close(proxyAuthenticationContext);
  }

  private <T> Collection<T> safe(Collection<T> items) {
    return (items != null) ? items : Collections.<T> emptyList();
  }

  private File getTmpFile(String path) {

    File f = new File(path);

    File file;
    do {
      //
      // We want an easy string to identify as something Aether created so we use the prefex "aether", we then have a unique portion which is randomly
      // generated, then we have the name of the file, then we have a unique suffix so that we avoid weird cases where artifacts have odd names like "a1"
      // and we're trying to download "foo.sha1".
      //
      // ${HOME}/.m2/repository/io/tesla/tesla/4/aether-737f90e4cfa047e3-pom.xml-in-progress
      //      
      file = new File(f.getParentFile(), "aether-" + UUID.randomUUID() + "-" + f.getName() + "-in-progress");
    } while (file.exists());
    return file;
  }

  private static final ExceptionWrapper<MetadataTransfer> METADATA = new ExceptionWrapper<MetadataTransfer>() {
    public void wrap(MetadataTransfer transfer, Exception e, RemoteRepository repository) {
      MetadataTransferException ex = null;
      if (e instanceof ResourceDoesNotExistException) {
        ex = new MetadataNotFoundException(transfer.getMetadata(), repository);
      } else if (e != null) {
        ex = new MetadataTransferException(transfer.getMetadata(), repository, e);
      }
      transfer.setException(ex);
    }
  };

  private static final ExceptionWrapper<ArtifactTransfer> ARTIFACT = new ExceptionWrapper<ArtifactTransfer>() {
    public void wrap(ArtifactTransfer transfer, Exception e, RemoteRepository repository) {
      ArtifactTransferException ex = null;
      if (e instanceof ResourceDoesNotExistException) {
        ex = new ArtifactNotFoundException(transfer.getArtifact(), repository);
      } else if (e != null) {
        ex = new ArtifactTransferException(transfer.getArtifact(), repository, e);
      }
      transfer.setException(ex);
    }
  };

  private class LatchGuard {

    private final CountDownLatch latch;
    private final AtomicBoolean done = new AtomicBoolean(false);

    public LatchGuard(CountDownLatch latch) {
      this.latch = latch;
    }

    public void countDown() {
      if (!done.getAndSet(true)) {
        latch.countDown();
      }
    }
  }
}
