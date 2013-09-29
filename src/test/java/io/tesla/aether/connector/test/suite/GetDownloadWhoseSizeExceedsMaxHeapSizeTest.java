/*******************************************************************************
 * Copyright (c) 2010, 2011 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package io.tesla.aether.connector.test.suite;

import java.io.File;
import java.util.Arrays;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.internal.test.util.TestFileUtils;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.spi.connector.ArtifactDownload;

public class GetDownloadWhoseSizeExceedsMaxHeapSizeTest extends AetherTestCase {

  public void testDownloadArtifactWhoseSizeExceedsMaxHeapSize() throws Exception {
    long bytes = Runtime.getRuntime().maxMemory() * 5 / 4;
    generate.addContent("gid/aid/version/aid-version-classifier.extension", bytes);

    File f = TestFileUtils.createTempFile("");
    Artifact a = artifact();

    ArtifactDownload down = new ArtifactDownload(a, null, f, RepositoryPolicy.CHECKSUM_POLICY_IGNORE);
    connector().get(Arrays.asList(down), null);
    connector().close();

    assertEquals(bytes, f.length());
  }
}
