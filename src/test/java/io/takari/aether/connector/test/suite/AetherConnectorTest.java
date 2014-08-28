/**
 * Copyright (c) 2012 to original author or authors
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.aether.connector.test.suite;

import static org.eclipse.aether.transfer.TransferEvent.EventType.INITIATED;
import static org.eclipse.aether.transfer.TransferEvent.EventType.PROGRESSED;
import static org.eclipse.aether.transfer.TransferEvent.EventType.SUCCEEDED;
import static org.junit.Assert.assertArrayEquals;
import io.takari.aether.connector.test.suite.server.ResourceServer;
import io.tesla.webserver.WebServer;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.internal.test.util.TestFileUtils;
import org.eclipse.aether.metadata.DefaultMetadata;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.spi.connector.ArtifactDownload;
import org.eclipse.aether.spi.connector.ArtifactUpload;
import org.eclipse.aether.spi.connector.MetadataDownload;
import org.eclipse.aether.spi.connector.MetadataUpload;
import org.eclipse.aether.spi.connector.RepositoryConnector;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.Transfer;
import org.eclipse.aether.transfer.AbstractTransferListener;
import org.eclipse.aether.transfer.NoRepositoryConnectorException;
import org.eclipse.aether.transfer.TransferCancelledException;
import org.eclipse.aether.transfer.TransferEvent;
import org.eclipse.aether.transfer.TransferEvent.EventType;
import org.eclipse.aether.transfer.TransferListener;
import org.eclipse.aether.transfer.TransferResource;
import org.junit.Assert;
import org.junit.Test;

/**
 * The ConnectorTestSuite bundles standard tests for {@link RepositoryConnector}s. The only thing the client code
 * must provide is a {@link RepositoryConnectorFactory}.
 * <p>
 * To use these tests, provide a (Junit4-)class extending this class, and provide a default constructor calling
 * {@link AetherConnectorTest#ConnectorTestSuite(ConnectorTestSetup)} with a self-implemented {@link ConnectorTestSetup}.
 */
public class AetherConnectorTest extends AetherTestCase {

  protected void configureServer(WebServer server) {
    addBehaviour("/*", new ResourceServer());
  }

  /**
   * Test successful event order.
   * 
   * @see TransferEventTester#testSuccessfulTransferEvents(RepositoryConnectorFactory, TestRepositorySystemSession,
   *      RemoteRepository)
   */
  public void testSuccessfulEvents() throws NoRepositoryConnectorException, IOException {
    testSuccessfulTransferEvents(repositoryConnectorFactory, session(), remoteRepository());
  }

  public void testFailedEvents() throws NoRepositoryConnectorException, IOException {
    testFailedTransferEvents(repositoryConnectorFactory, session(), remoteRepository());
  }

  public void testFileHandleLeakage() throws IOException, NoRepositoryConnectorException {

    Artifact artifact = new DefaultArtifact("testGroup", "testArtifact", "", "jar", "1-test");
    Metadata metadata = new DefaultMetadata("testGroup", "testArtifact", "1-test", "maven-metadata.xml", Metadata.Nature.RELEASE_OR_SNAPSHOT);

    RepositoryConnector connector = connector();

    File tmpFile = TestFileUtils.createTempFile("testFileHandleLeakage");
    ArtifactUpload artUp = new ArtifactUpload(artifact, tmpFile);
    connector.put(Arrays.asList(artUp), null);
    assertTrue("Leaking file handle in artifact upload", tmpFile.delete());

    tmpFile = TestFileUtils.createTempFile("testFileHandleLeakage");
    MetadataUpload metaUp = new MetadataUpload(metadata, tmpFile);
    connector.put(null, Arrays.asList(metaUp));
    assertTrue("Leaking file handle in metadata upload", tmpFile.delete());

    tmpFile = TestFileUtils.createTempFile("testFileHandleLeakage");
    ArtifactDownload artDown = new ArtifactDownload(artifact, null, tmpFile, null);
    connector.get(Arrays.asList(artDown), null);
    new File(tmpFile.getAbsolutePath() + ".sha1").deleteOnExit();
    assertTrue("Leaking file handle in artifact download", tmpFile.delete());

    tmpFile = TestFileUtils.createTempFile("testFileHandleLeakage");
    MetadataDownload metaDown = new MetadataDownload(metadata, null, tmpFile, null);
    connector.get(null, Arrays.asList(metaDown));
    new File(tmpFile.getAbsolutePath() + ".sha1").deleteOnExit();
    assertTrue("Leaking file handle in metadata download", tmpFile.delete());

    connector.close();
  }

  private static class CountingTransferListener extends AbstractTransferListener {
    public final AtomicInteger successCount = new AtomicInteger();
    
    @Override
    public void transferSucceeded(TransferEvent event) {
      successCount.incrementAndGet();
    }
  }

  @Test
  public void testBlocking() throws NoRepositoryConnectorException, IOException {

    RepositoryConnector connector = connector();

    int count = 10;

    byte[] pattern = "tmpFile".getBytes("UTF-8");
    File tmpFile = TestFileUtils.createTempFile(pattern, 100000);

    CountingTransferListener artUpsCounter = new CountingTransferListener();
    CountingTransferListener metaUpsCounter = new CountingTransferListener();
    CountingTransferListener artDownsCounter = new CountingTransferListener();
    CountingTransferListener metaDownsCounter = new CountingTransferListener();

    List<ArtifactUpload> artUps =
        createTransfers(ArtifactUpload.class, count, tmpFile, artUpsCounter);
    List<MetadataUpload> metaUps =
        createTransfers(MetadataUpload.class, count, tmpFile, metaUpsCounter);
    List<ArtifactDownload> artDowns =
        createTransfers(ArtifactDownload.class, count, null, artDownsCounter);
    List<MetadataDownload> metaDowns =
        createTransfers(MetadataDownload.class, count, null, metaDownsCounter);

    // this should block until all transfers are done - racing condition, better way to test this?
    connector.put(artUps, metaUps);
    connector.get(artDowns, metaDowns);

    Assert.assertEquals(count, artUpsCounter.successCount.intValue());
    Assert.assertEquals(count, metaUpsCounter.successCount.intValue());
    Assert.assertEquals(count, artDownsCounter.successCount.intValue());
    Assert.assertEquals(count, metaDownsCounter.successCount.intValue());

    connector.close();
  }

  public void testMkdirConcurrencyBug() throws IOException, NoRepositoryConnectorException {
    
    RepositoryConnector connector = connector();
    File artifactFile = TestFileUtils.createTempFile("mkdirsBug0");
    File metadataFile = TestFileUtils.createTempFile("mkdirsBug1");

    int numTransfers = 2;

    ArtifactUpload[] artUps = new ArtifactUpload[numTransfers];
    MetadataUpload[] metaUps = new MetadataUpload[numTransfers];

    for (int i = 0; i < numTransfers; i++) {
      Artifact art = new DefaultArtifact("testGroup", "testArtifact", "", "jar", i + "-test");
      Metadata meta = new DefaultMetadata("testGroup", "testArtifact", i + "-test", "maven-metadata.xml", Metadata.Nature.RELEASE_OR_SNAPSHOT);

      ArtifactUpload artUp = new ArtifactUpload(art, artifactFile);
      MetadataUpload metaUp = new MetadataUpload(meta, metadataFile);

      artUps[i] = artUp;
      metaUps[i] = metaUp;
    }

    connector.put(Arrays.asList(artUps), null);
    connector.put(null, Arrays.asList(metaUps));

    File localRepo = session().getLocalRepository().getBasedir();

    StringBuilder localPath = new StringBuilder(localRepo.getAbsolutePath());

    for (int i = 0; i < 50; i++) {
      localPath.append("/d");
    }

    
    ArtifactDownload[] artDowns = new ArtifactDownload[numTransfers];
    MetadataDownload[] metaDowns = new MetadataDownload[numTransfers];

    for (int m = 0; m < 20; m++) {
      CountingTransferListener artDownsCounter = new CountingTransferListener();
      CountingTransferListener metaDownsCounter = new CountingTransferListener();

      for (int i = 0; i < numTransfers; i++) {
        File artFile = new File(localPath.toString() + "/a" + i);
        File metaFile = new File(localPath.toString() + "/m" + i);

        Artifact art = new DefaultArtifact("testGroup", "testArtifact", "", "jar", i + "-test");
        Metadata meta = new DefaultMetadata("testGroup", "testArtifact", i + "-test", "maven-metadata.xml", Metadata.Nature.RELEASE_OR_SNAPSHOT);

        ArtifactDownload artDown = new ArtifactDownload(art, null, artFile, RepositoryPolicy.CHECKSUM_POLICY_FAIL);
        artDown.setListener(artDownsCounter);
        MetadataDownload metaDown = new MetadataDownload(meta, null, metaFile, RepositoryPolicy.CHECKSUM_POLICY_FAIL);
        metaDown.setListener(metaDownsCounter);

        artDowns[i] = artDown;
        metaDowns[i] = metaDown;
      }

      connector.get(Arrays.asList(artDowns), Arrays.asList(metaDowns));

      Assert.assertEquals(numTransfers, artDownsCounter.successCount.intValue());
      Assert.assertEquals(numTransfers, metaDownsCounter.successCount.intValue());

      TestFileUtils.deleteFile(localRepo);
    }

    connector.close();
  }

  /**
   * See https://issues.sonatype.org/browse/AETHER-8
   */
  public void testTransferZeroBytesFile() throws IOException, NoRepositoryConnectorException {
    File emptyFile = TestFileUtils.createTempFile("");

    Artifact artifact = new DefaultArtifact("gid:aid:ext:ver");
    ArtifactUpload upA = new ArtifactUpload(artifact, emptyFile);
    File dir = TestFileUtils.createTempDir("con-test");
    File downAFile = new File(dir, "downA.file");
    downAFile.deleteOnExit();
    ArtifactDownload downA = new ArtifactDownload(artifact, "", downAFile, RepositoryPolicy.CHECKSUM_POLICY_FAIL);

    Metadata metadata = new DefaultMetadata("gid", "aid", "ver", "maven-metadata.xml", Metadata.Nature.RELEASE_OR_SNAPSHOT);
    MetadataUpload upM = new MetadataUpload(metadata, emptyFile);
    File downMFile = new File(dir, "downM.file");
    downMFile.deleteOnExit();
    MetadataDownload downM = new MetadataDownload(metadata, "", downMFile, RepositoryPolicy.CHECKSUM_POLICY_FAIL);

    RepositoryConnector connector = connector();
    connector.put(Arrays.asList(upA), Arrays.asList(upM));
    connector.get(Arrays.asList(downA), Arrays.asList(downM));

    assertNull(String.valueOf(upA.getException()), upA.getException());
    assertNull(String.valueOf(upM.getException()), upM.getException());
    assertNull(String.valueOf(downA.getException()), downA.getException());
    assertNull(String.valueOf(downM.getException()), downM.getException());

    assertEquals(0, downAFile.length());
    assertEquals(0, downMFile.length());

    connector.close();
  }

  public void testProgressEventsDataBuffer() throws UnsupportedEncodingException, IOException, NoSuchAlgorithmException, NoRepositoryConnectorException {
    byte[] bytes = "These are the test contents.\n".getBytes("UTF-8");
    int count = 120000;
    MessageDigest digest = MessageDigest.getInstance("SHA-1");
    for (int i = 0; i < count; i++) {
      digest.update(bytes);
    }
    byte[] hash = digest.digest();

    File file = TestFileUtils.createTempFile(bytes, count);

    Artifact artifact = new DefaultArtifact("gid:aid:ext:ver");
    ArtifactUpload upA = new ArtifactUpload(artifact, file);

    File dir = TestFileUtils.createTempDir("con-test");
    File downAFile = new File(dir, "downA.file");
    downAFile.deleteOnExit();
    ArtifactDownload downA = new ArtifactDownload(artifact, "", downAFile, RepositoryPolicy.CHECKSUM_POLICY_FAIL);

    Metadata metadata = new DefaultMetadata("gid", "aid", "ver", "maven-metadata.xml", Metadata.Nature.RELEASE_OR_SNAPSHOT);
    MetadataUpload upM = new MetadataUpload(metadata, file);
    File downMFile = new File(dir, "downM.file");
    downMFile.deleteOnExit();
    MetadataDownload downM = new MetadataDownload(metadata, "", downMFile, RepositoryPolicy.CHECKSUM_POLICY_FAIL);

    DigestingTransferListener listener = new DigestingTransferListener();
    session().setTransferListener(listener);

    RepositoryConnector connector = connector();
    connector.put(Arrays.asList(upA), null);
    assertArrayEquals(hash, listener.getHash());
    listener.rewind();
    connector.put(null, Arrays.asList(upM));
    assertArrayEquals(hash, listener.getHash());
    listener.rewind();
    connector.get(Arrays.asList(downA), null);
    assertArrayEquals(hash, listener.getHash());
    listener.rewind();
    connector.get(null, Arrays.asList(downM));
    assertArrayEquals(hash, listener.getHash());
    listener.rewind();

    connector.close();
  }

  private final class DigestingTransferListener implements TransferListener {

    private MessageDigest digest;

    private synchronized void initDigest() throws NoSuchAlgorithmException {
      digest = MessageDigest.getInstance("SHA-1");
    }

    public DigestingTransferListener() throws NoSuchAlgorithmException {
      initDigest();
    }

    public void rewind() throws NoSuchAlgorithmException {
      initDigest();
    }

    public void transferSucceeded(TransferEvent event) {
    }

    public void transferStarted(TransferEvent event) throws TransferCancelledException {
    }

    public synchronized void transferProgressed(TransferEvent event) throws TransferCancelledException {
      digest.update(event.getDataBuffer());
    }

    public void transferInitiated(TransferEvent event) throws TransferCancelledException {
    }

    public void transferFailed(TransferEvent event) {
    }

    public void transferCorrupted(TransferEvent event) throws TransferCancelledException {
    }

    public synchronized byte[] getHash() {
      return digest.digest();
    }
  }

  //
  // Events testing
  //

  /**
   * Test the order of events and their properties for the successful up- and download of artifact and metadata.
   */
  public static void testSuccessfulTransferEvents(RepositoryConnectorFactory factory, DefaultRepositorySystemSession session, RemoteRepository repository) throws NoRepositoryConnectorException,
      IOException {
    RecordingTransferListener listener = new RecordingTransferListener(session.getTransferListener());
    session.setTransferListener(listener);

    RepositoryConnector connector = factory.newInstance(session, repository);

    byte[] pattern = "tmpFile".getBytes();
    File tmpFile = TestFileUtils.createTempFile(pattern, 10000);
    long expectedBytes = tmpFile.length();

    Collection<ArtifactUpload> artUps = createTransfers(ArtifactUpload.class, 1, tmpFile);
    Collection<ArtifactDownload> artDowns = createTransfers(ArtifactDownload.class, 1, tmpFile);
    Collection<MetadataUpload> metaUps = createTransfers(MetadataUpload.class, 1, tmpFile);
    Collection<MetadataDownload> metaDowns = createTransfers(MetadataDownload.class, 1, tmpFile);

    connector.put(artUps, null);
    LinkedList<TransferEvent> events = new LinkedList<TransferEvent>(listener.getEvents());
    checkEvents(events, expectedBytes);
    listener.clear();

    connector.get(artDowns, null);
    events = new LinkedList<TransferEvent>(listener.getEvents());
    checkEvents(events, expectedBytes);
    listener.clear();

    connector.put(null, metaUps);
    events = new LinkedList<TransferEvent>(listener.getEvents());
    checkEvents(events, expectedBytes);
    listener.clear();

    connector.get(null, metaDowns);
    events = new LinkedList<TransferEvent>(listener.getEvents());
    checkEvents(events, expectedBytes);

    connector.close();
    session.setTransferListener(null);
  }

  private static void checkEvents(Queue<TransferEvent> events, long expectedBytes) {
    TransferEvent currentEvent = events.poll();
    String msg = "initiate event is missing";
    assertNotNull(msg, currentEvent);
    assertEquals(msg, INITIATED, currentEvent.getType());
    checkProperties(currentEvent);

    TransferResource expectedResource = currentEvent.getResource();

    currentEvent = events.poll();
    msg = "start event is missing";
    assertNotNull(msg, currentEvent);
    assertEquals(msg, TransferEvent.EventType.STARTED, currentEvent.getType());
    assertEquals("bad content length", expectedBytes, currentEvent.getResource().getContentLength());
    checkProperties(currentEvent);
    assertResourceEquals(expectedResource, currentEvent.getResource());

    EventType progressed = TransferEvent.EventType.PROGRESSED;
    EventType succeeded = TransferEvent.EventType.SUCCEEDED;

    TransferEvent succeedEvent = null;

    int dataLength = 0;
    long transferredBytes = 0;
    while ((currentEvent = events.poll()) != null) {
      EventType currentType = currentEvent.getType();

      assertResourceEquals(expectedResource, currentEvent.getResource());

      if (succeeded.equals(currentType)) {
        succeedEvent = currentEvent;
        checkProperties(currentEvent);
        break;
      } else {
        assertTrue("event is not 'succeeded' and not 'progressed'", progressed.equals(currentType));
        assertTrue("wrong order of progressed events, transferredSize got smaller, last = " + transferredBytes + ", current = " + currentEvent.getTransferredBytes(),
            currentEvent.getTransferredBytes() >= transferredBytes);
        assertEquals("bad content length", expectedBytes, currentEvent.getResource().getContentLength());
        transferredBytes = currentEvent.getTransferredBytes();
        dataLength += currentEvent.getDataBuffer().remaining();
        checkProperties(currentEvent);
      }
    }

    // all events consumed
    assertEquals("too many events left: " + events.toString(), 0, events.size());

    // test transferred size
    assertEquals("progress events transferred bytes don't match: data length does not add up", expectedBytes, dataLength);
    assertEquals("succeed event transferred bytes don't match", expectedBytes, succeedEvent.getTransferredBytes());
  }

  private static void assertResourceEquals(TransferResource expected, TransferResource actual) {
    assertEquals("TransferResource: content length does not match.", expected.getContentLength(), actual.getContentLength());
    assertEquals("TransferResource: file does not match.", expected.getFile(), actual.getFile());
    assertEquals("TransferResource: repo url does not match.", expected.getRepositoryUrl(), actual.getRepositoryUrl());
    assertEquals("TransferResource: transfer start time does not match.", expected.getTransferStartTime(), actual.getTransferStartTime());
    assertEquals("TransferResource: name does not match.", expected.getResourceName(), actual.getResourceName());
  }

  private static void checkProperties(TransferEvent event) {
    assertNotNull("resource is null for type: " + event.getType(), event.getResource());
    assertNotNull("request type is null for type: " + event.getType(), event.getRequestType());
    assertNotNull("type is null for type: " + event.getType(), event.getType());

    if (PROGRESSED.equals(event.getType())) {
      assertNotNull("data buffer is null for type: " + event.getType(), event.getDataBuffer());
      assertTrue("transferred byte is not set/not positive for type: " + event.getType(), event.getTransferredBytes() > -1);
    } else if (SUCCEEDED.equals(event.getType())) {
      assertTrue("transferred byte is not set/not positive for type: " + event.getType(), event.getTransferredBytes() > -1);
    }
  }

  /**
   * Test the order of events and their properties for the unsuccessful up- and download of artifact and metadata.
   * Failure is triggered by setting the file to transfer to {@code null} for uploads, and asking for a non-existent
   * item for downloads.
   */
  public static void testFailedTransferEvents(RepositoryConnectorFactory factory, DefaultRepositorySystemSession session, RemoteRepository repository) throws NoRepositoryConnectorException, IOException {
    RecordingTransferListener listener = new RecordingTransferListener(session.getTransferListener());
    session.setTransferListener(listener);

    RepositoryConnector connector = factory.newInstance(session, repository);

    byte[] pattern = "tmpFile".getBytes("us-ascii");
    File tmpFile = TestFileUtils.createTempFile(pattern, 10000);

    Collection<ArtifactUpload> artUps = createTransfers(ArtifactUpload.class, 1, null);
    Collection<ArtifactDownload> artDowns = createTransfers(ArtifactDownload.class, 1, tmpFile);
    Collection<MetadataUpload> metaUps = createTransfers(MetadataUpload.class, 1, null);
    Collection<MetadataDownload> metaDowns = createTransfers(MetadataDownload.class, 1, tmpFile);

    connector.put(artUps, null);
    LinkedList<TransferEvent> events = new LinkedList<TransferEvent>(listener.getEvents());
    checkFailedEvents(events, null);
    listener.clear();

    connector.get(artDowns, null);
    events = new LinkedList<TransferEvent>(listener.getEvents());
    checkFailedEvents(events, null);
    listener.clear();

    connector.put(null, metaUps);
    events = new LinkedList<TransferEvent>(listener.getEvents());
    checkFailedEvents(events, null);
    listener.clear();

    connector.get(null, metaDowns);
    events = new LinkedList<TransferEvent>(listener.getEvents());
    checkFailedEvents(events, null);

    connector.close();
    session.setTransferListener(null);
  }

  private static void checkFailedEvents(Queue<TransferEvent> events, Class<? extends Throwable> expectedError) {
    if (expectedError == null) {
      expectedError = Throwable.class;
    }

    TransferEvent currentEvent = events.poll();
    String msg = "initiate event is missing";
    assertNotNull(msg, currentEvent);
    assertEquals(msg, INITIATED, currentEvent.getType());
    checkProperties(currentEvent);

    currentEvent = events.poll();
    msg = "fail event is missing";
    assertNotNull(msg, currentEvent);
    assertEquals(msg, TransferEvent.EventType.FAILED, currentEvent.getType());
    checkProperties(currentEvent);
    assertNotNull("exception is missing for fail event", currentEvent.getException());
    Exception exception = currentEvent.getException();
    assertTrue("exception is of wrong type, should be instance of " + expectedError + " but was " + exception.getClass(), expectedError.isAssignableFrom(exception.getClass()));

    // all events consumed
    assertEquals("too many events left: " + events.toString(), 0, events.size());
  }

  //
  // Utils
  //
  /**
   * Creates transfer objects according to the given class. If the file parameter is {@code null}, a new temporary
   * file will be created for downloads. Uploads will just use the parameter as it is.
   */
  public static <T extends Transfer> List<T> createTransfers(Class<T> cls, int count, File file, TransferListener listener) {
    ArrayList<T> ret = new ArrayList<T>();

    for (int i = 0; i < count; i++) {
      String context = null;
      String checksumPolicy = RepositoryPolicy.CHECKSUM_POLICY_IGNORE;

      Object obj = null;
      if (cls.isAssignableFrom(ArtifactUpload.class)) {
        Artifact artifact = new DefaultArtifact("testGroup", "testArtifact", "sources", "jar", (i + 1) + "-test");
        ArtifactUpload artifactUpload = new ArtifactUpload(artifact, file);
        artifactUpload.setListener(listener);
        obj = artifactUpload;
      } else if (cls.isAssignableFrom(ArtifactDownload.class)) {
        try {
          Artifact artifact = new DefaultArtifact("testGroup", "testArtifact", "sources", "jar", (i + 1) + "-test");
          ArtifactDownload artifactDownload = new ArtifactDownload(artifact, context, safeFile(file), checksumPolicy);
          artifactDownload.setListener(listener);
          obj = artifactDownload;
        } catch (IOException e) {
          throw new RuntimeException(e.getMessage(), e);
        }
      } else if (cls.isAssignableFrom(MetadataUpload.class)) {
        Metadata metadata = new DefaultMetadata("testGroup", "testArtifact", (i + 1) + "-test", "jar", Metadata.Nature.RELEASE_OR_SNAPSHOT, file);
        MetadataUpload metadataUpload = new MetadataUpload(metadata, file);
        metadataUpload.setListener(listener);
        obj = metadataUpload;
      } else if (cls.isAssignableFrom(MetadataDownload.class)) {
        try {
          Metadata metadata = new DefaultMetadata("testGroup", "testArtifact", (i + 1) + "-test", "jar", Metadata.Nature.RELEASE_OR_SNAPSHOT, file);
          MetadataDownload metadataDownload = new MetadataDownload(metadata, context, safeFile(file), checksumPolicy);
          metadataDownload.setListener(listener);
          obj = metadataDownload;
        } catch (IOException e) {
          throw new RuntimeException(e.getMessage(), e);
        }
      }

      ret.add(cls.cast(obj));
    }

    return ret;
  }
  public static <T extends Transfer> List<T> createTransfers(Class<T> cls, int count, File file) {
    return createTransfers(cls, count, file, null);
  }

  private static File safeFile(File file) throws IOException {
    return file == null ? TestFileUtils.createTempFile("") : file;
  }
}
