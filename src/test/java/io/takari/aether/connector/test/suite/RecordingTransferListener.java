/*******************************************************************************
 * Copyright (c) 2010, 2013 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package io.takari.aether.connector.test.suite;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.aether.transfer.TransferCancelledException;
import org.eclipse.aether.transfer.TransferEvent;
import org.eclipse.aether.transfer.TransferListener;

public class RecordingTransferListener implements TransferListener {
  private List<TransferEvent> events = Collections.synchronizedList(new ArrayList<TransferEvent>());

  private List<TransferEvent> progressEvents = Collections
      .synchronizedList(new ArrayList<TransferEvent>());

  private TransferListener realListener;

  public RecordingTransferListener() {
    this(null);
  }

  public RecordingTransferListener(TransferListener transferListener) {
    this.realListener = transferListener;
  }

  public List<TransferEvent> getEvents() {
    return events;
  }

  public List<TransferEvent> getProgressEvents() {
    return progressEvents;
  }

  @Override
  public void transferSucceeded(TransferEvent event) {
    events.add(event);
    if (realListener != null) {
      realListener.transferSucceeded(event);
    }
  }

  @Override
  public void transferStarted(TransferEvent event) throws TransferCancelledException {
    events.add(event);
    if (realListener != null) {
      realListener.transferStarted(event);
    }
  }

  @Override
  public void transferProgressed(TransferEvent event) throws TransferCancelledException {
    event = deepClone(event);
    events.add(event);
    progressEvents.add(event);
    if (realListener != null) {
      realListener.transferProgressed(event);
    }
  }

  private TransferEvent deepClone(TransferEvent event) {
    TransferEvent.Builder builder =
        new TransferEvent.Builder(event.getSession(), event.getResource());
    builder.setType(event.getType()).setRequestType(event.getRequestType());
    builder.setException(event.getException());
    ByteBuffer buffer = event.getDataBuffer();
    if (buffer != null) {
      builder
          .setDataBuffer((ByteBuffer) ByteBuffer.allocate(buffer.remaining()).put(buffer).flip());
    }
    return builder.build();
  }

  @Override
  public void transferInitiated(TransferEvent event) throws TransferCancelledException {
    events.add(event);
    if (realListener != null) {
      realListener.transferInitiated(event);
    }
  }

  @Override
  public void transferFailed(TransferEvent event) {
    events.add(event);
    if (realListener != null) {
      realListener.transferFailed(event);
    }
  }

  @Override
  public void transferCorrupted(TransferEvent event) throws TransferCancelledException {
    events.add(event);
    if (realListener != null) {
      realListener.transferCorrupted(event);
    }
  }

  public void clear() {
    events.clear();
    progressEvents.clear();
  }

}
