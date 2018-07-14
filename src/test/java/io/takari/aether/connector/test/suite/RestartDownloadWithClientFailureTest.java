/**
 * Copyright (c) 2012 to original author or authors
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.aether.connector.test.suite;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.spi.connector.ArtifactDownload;
import org.eclipse.aether.spi.connector.RepositoryConnector;
import org.eclipse.aether.transfer.TransferEvent;
import org.eclipse.aether.transfer.TransferEvent.EventType;

import com.google.common.io.Files;

public class RestartDownloadWithClientFailureTest extends AetherTestCase {

  public void testWhenTheClientDiesAndRestartsConnectorShouldRedownloadTheEntireArtifact() throws Exception {

    addDelivery("gid/aid/version/aid-version-classifier.extension", "01234");
    addDelivery("gid/aid/version/aid-version-classifier.extension.sha1", sha1("0123456789"));
    addDelivery("gid/aid/version/aid-version-classifier.extension.md5", md5("0123456789"));

    //
    // We need to specifically name the file so that when the client dies, and new connector
    // is created in order to attempt finishing the download we need have the same name pattern
    // in order to find an existing file. This is how it will work with a real local Maven
    // repository.
    //
    File f = createTempFile("foo-bar-1.0.jar", "");
    Artifact a = artifact("bla");
    ArtifactDownload down = new ArtifactDownload(a, null, f, RepositoryPolicy.CHECKSUM_POLICY_FAIL);
    Collection<? extends ArtifactDownload> downs = Arrays.asList(down);
    RepositoryConnector c = connector();
    c.get(downs, null);

    //
    // We should have a checksum failure because right now only half the file has
    // been transferred so simulate the client giving up, or dying, and having
    // to start again to download the rest.
    //
    assertNotNull(String.valueOf(down.getException()), down.getException());

    //
    // Now add the full content of the expected file, so the client should be able to
    // download the entire artifact from the beginning.
    //
    addDelivery("gid/aid/version/aid-version-classifier.extension", "0123456789");

    //
    // Attempt the download again
    //
    f = createTempFile("foo-bar-1.0.jar", "");
    a = artifact("bla");
    RecordingTransferListener listener = new RecordingTransferListener(session().getTransferListener());
    down = new ArtifactDownload(a, null, f, RepositoryPolicy.CHECKSUM_POLICY_FAIL);
    down.setListener(listener);
    downs = Arrays.asList(down);
    c = connector(true);
    c.get(downs, null);

    //
    // We should have no errors and we should see that the entire file was redownloaded by the client.
    //
    assertNull(String.valueOf(down.getException()), down.getException());
    LinkedList<TransferEvent> events = new LinkedList<TransferEvent>(listener.getEvents());
    checkEvents(events, 10);

  }

  private static void checkEvents(Queue<TransferEvent> events, long expectedBytes) {
    TransferEvent currentEvent;
    boolean foundTransferEvent = false;
    while ((currentEvent = events.poll()) != null) {
      EventType currentType = currentEvent.getType();
      if (TransferEvent.EventType.SUCCEEDED.equals(currentType)) {
        foundTransferEvent = true;
        assertEquals("Should only have received " + expectedBytes + " bytes", expectedBytes, currentEvent.getTransferredBytes());
      }
    }
    assertTrue("No transfer event found", foundTransferEvent);
  }

  private static final File TMP = new File(System.getProperty("java.io.tmpdir"), "aether-" + UUID.randomUUID().toString().substring(0, 8));

  public static File createTempFile(String name, String contents) throws IOException {
    mkdirs(TMP);
    File tmpFile = new File(TMP, name);
    write(contents.getBytes("UTF8"), tmpFile);
    return tmpFile;
  }

  public static boolean mkdirs(File directory) {
    if (directory == null) {
      return false;
    }

    if (directory.exists()) {
      return false;
    }
    if (directory.mkdir()) {
      return true;
    }

    File canonDir = null;
    try {
      canonDir = directory.getCanonicalFile();
    } catch (IOException e) {
      return false;
    }

    File parentDir = canonDir.getParentFile();
    return (parentDir != null && (mkdirs(parentDir) || parentDir.exists()) && canonDir.mkdir());
  }

  public static void write(byte[] pattern, File file) throws IOException {
    file.deleteOnExit();
    file.getParentFile().mkdirs();
    Files.asByteSink(file).write(pattern);
  }
}
