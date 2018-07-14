/**
 * Copyright (c) 2012 to original author or authors
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.aether.connector.test.suite;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.internal.impl.DefaultFileProcessor;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.spi.connector.ArtifactDownload;
import org.eclipse.aether.spi.connector.MetadataDownload;
import org.eclipse.aether.spi.connector.RepositoryConnector;
import org.eclipse.aether.spi.io.FileProcessor;
import org.eclipse.aether.util.ChecksumUtils;

import com.google.inject.Binder;

public class ConcurrentDownloadTest extends AetherTestCase {
  private static final int NUMBER_OF_TRIES_TO_CAUSE_CORRUPTION = 10;
  private static final int NUMBER_OF_THREADS = 2;
  private static final File TMP = new File(System.getProperty("java.io.tmpdir"), "aether-" + UUID.randomUUID().toString().substring(0, 8));

  @Override
  public void configure(Binder binder) {
    binder.bind(FileProcessor.class).to(DefaultFileProcessor.class);
    super.configure(binder);
  }

  /**
   * See https://github.com/takari/aether-connector-okhttp/issues/15
   */
  public void testConcurrentDownloadOfSameFileShouldNotCorruptDownloadedArtifact() throws Exception {
    String testInput = createTestInput();
    String expectedChecksum = sha1(testInput);

    addDelivery("gid/aid/version/aid-version-classifier.extension", testInput.getBytes());
    addDelivery("gid/aid/version/aid-version-classifier.extension.sha1", expectedChecksum.getBytes());

    File file = new File(TMP, "foo-bar-1.0.pom");
    Artifact artifact = artifact();
    ArtifactDownload down = new ArtifactDownload(artifact, null, file, RepositoryPolicy.CHECKSUM_POLICY_WARN);
    Collection<? extends ArtifactDownload> downs = Arrays.asList(down);
    RepositoryConnector connector = connector();

    for (int i = 0; i < NUMBER_OF_TRIES_TO_CAUSE_CORRUPTION; ++i) {
      clearRepo();

      callAetherConnectorWithMultipleThreads(connector, downs);

      String actualChecksum = (String) ChecksumUtils.calc(file, Collections.singleton("SHA-1")).get("SHA-1");
      assertEquals(expectedChecksum, actualChecksum);
    }
  }

  private String createTestInput() {
    // Create a test input close to the size of a real artifact, so that it is several times
    // larger than the chunk size of the server to increase the chance of corruption.
    String result = "";
    for (int i = 0; i < 100; ++i) {
      result += "0123456789";
    }
    return result;
  }

  public void clearRepo() {
    if (TMP.listFiles() != null) {
      for (File file : TMP.listFiles()) {
        file.delete();
      }
    }
  }

  private void callAetherConnectorWithMultipleThreads(final RepositoryConnector connector, final Collection<? extends ArtifactDownload> downs) throws InterruptedException {
    List<Thread> threads = createConnectorCallingThreads(connector, downs);
    startThreads(threads);
    waitForThreadsToFinish(threads);
  }

  private List<Thread> createConnectorCallingThreads(final RepositoryConnector connector, final Collection<? extends ArtifactDownload> downs) {
    List<Thread> threads = new ArrayList<>();
    for (int i = 0; i < NUMBER_OF_THREADS; ++i) {
      threads.add(new Thread(new Runnable() {
        @Override
        public void run() {
          download(connector, downs);
        }
      }));
    }
    return threads;
  }

  private void startThreads(List<Thread> threads) {
    for (Thread thread : threads) {
      thread.start();
    }
  }

  private void waitForThreadsToFinish(List<Thread> threads) throws InterruptedException {
    for (Thread thread : threads) {
      thread.join();
    }
  }

  private void download(RepositoryConnector connector, Collection<? extends ArtifactDownload> downs) {
    connector.get(downs, new ArrayList<MetadataDownload>());
  }

}
