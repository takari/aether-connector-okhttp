/**
 * Copyright (c) 2012 to original author or authors
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.aether.connector.test.suite;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.spi.connector.ArtifactUpload;
import org.eclipse.aether.spi.connector.MetadataUpload;
import org.eclipse.aether.transfer.ArtifactTransferException;
import org.eclipse.aether.transfer.TransferCancelledException;
import org.eclipse.aether.transfer.TransferEvent;
import org.eclipse.aether.transfer.TransferListener;

public class PutTest extends AetherTestCase {

  public void testArtifactUpload() throws Exception {
    
    addExpectation("gid/aid/version/aid-version-classifier.extension", "artifact");
    addExpectation("gid/aid/version/aid-version-classifier.extension.sha1", sha1("artifact"));
    addExpectation("gid/aid/version/aid-version-classifier.extension.md5", md5("artifact"));

    Artifact artifact = artifact("artifact");
    ArtifactUpload up = new ArtifactUpload(artifact, artifact.getFile());
    final AtomicLong transferredBytes = new AtomicLong();
    up.setListener(new TransferListener() {
      @Override
      public void transferInitiated(final TransferEvent transferEvent) throws TransferCancelledException {
        transferredBytes.set(0);
      }

      @Override
      public void transferStarted(final TransferEvent transferEvent) throws TransferCancelledException {
        transferredBytes.set(0);
      }

      @Override
      public void transferProgressed(final TransferEvent transferEvent) throws TransferCancelledException {

      }

      @Override
      public void transferCorrupted(final TransferEvent transferEvent) throws TransferCancelledException {

      }

      @Override
      public void transferSucceeded(final TransferEvent transferEvent) {
        transferredBytes.addAndGet(transferEvent.getTransferredBytes());
      }

      @Override
      public void transferFailed(final TransferEvent transferEvent) {

      }
    });
    List<ArtifactUpload> uploads = Arrays.asList(up);
    connector().put(uploads, null);

    assertEquals(artifact.getFile().length(), transferredBytes.get());

    ArtifactTransferException ex = up.getException();
    assertNull(ex != null ? ex.getMessage() : "", ex);
    assertExpectations();
  }

  public void testMetadataUpload() throws Exception {
    
    String content = "metadata";
    addExpectation("gid/aid/version/maven-metadata.xml", content);
    addExpectation("gid/aid/version/maven-metadata.xml.sha1", sha1(content));
    addExpectation("gid/aid/version/maven-metadata.xml.md5", md5(content));

    Metadata metadata = metadata(content);

    List<MetadataUpload> uploads = Arrays.asList(new MetadataUpload(metadata, metadata.getFile()));
    connector().put(null, uploads);

    assertExpectations();
  }

  //@Ignore("https://issues.sonatype.org/browse/AHC-5")
  public void IGNOREtestArtifactWithZeroBytesFile() throws Exception {
    String content = "";
    addExpectation("gid/aid/version/aid-version-classifier.extension", content);
    addExpectation("gid/aid/version/aid-version-classifier.extension.sha1", sha1(content));
    addExpectation("gid/aid/version/aid-version-classifier.extension.md5", md5(content));

    Artifact artifact = artifact(content);
    ArtifactUpload up = new ArtifactUpload(artifact, artifact.getFile());
    List<ArtifactUpload> uploads = Arrays.asList(up);
    connector().put(uploads, null);

    ArtifactTransferException ex = up.getException();
    assertNull(ex != null ? ex.getMessage() : "", ex);
    assertExpectations();
  }

  //@Ignore("https://issues.sonatype.org/browse/AHC-5")
  public void IGNOREtestMetadataWithZeroBytesFile() throws Exception {
    String content = "";
    addExpectation("gid/aid/version/maven-metadata.xml", content);
    addExpectation("gid/aid/version/maven-metadata.xml.sha1", sha1(content));
    addExpectation("gid/aid/version/maven-metadata.xml.md5", md5(content));

    Metadata metadata = metadata(content);

    List<MetadataUpload> uploads = Arrays.asList(new MetadataUpload(metadata, metadata.getFile()));
    connector().put(null, uploads);

    assertExpectations();
  }

}
