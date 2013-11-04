/**
 * Copyright (c) 2012 to original author or authors
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.tesla.aether.connector.test.suite;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

//
// A server handler that dies mid-stream a given number of times before successfully transferring the contents of the whole file
//
class FlakyServerHandlerWithNoRangeSupport extends AbstractHandler {

  private final int requiredRequests;
  private final Map<String, Integer> madeRequests;
  final int totalSize;

  public FlakyServerHandlerWithNoRangeSupport(int requiredRequests) {
    this.requiredRequests = requiredRequests;
    madeRequests = new ConcurrentHashMap<String, Integer>();
    totalSize = 1024 * 128;
  }

  public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException {
    
    Integer attempts = madeRequests.get(target);
    attempts = (attempts == null) ? Integer.valueOf(1) : Integer.valueOf(attempts.intValue() + 1);
    madeRequests.put(target, attempts);

    if (attempts.intValue() > requiredRequests) {
      response.setStatus(HttpURLConnection.HTTP_BAD_REQUEST);
      response.flushBuffer();
      return;
    }

    response.setStatus(HttpURLConnection.HTTP_OK);
    response.setContentType("Content-type: text/plain; charset=UTF-8");
    response.setContentLength(totalSize);
    response.flushBuffer();

    OutputStream out = response.getOutputStream();

    if(attempts.intValue() == requiredRequests) {
      // Write out all the content
      for (int i = 0; i <= totalSize; i++) {
        out.write(GetResumeTest.CONTENT_PATTERN[i % GetResumeTest.CONTENT_PATTERN.length]);        
        out.flush();
      }
    } else {
      // Write out half the content and die
      for (int i = 0; i <= (totalSize/2); i++) {
        out.write(GetResumeTest.CONTENT_PATTERN[i % GetResumeTest.CONTENT_PATTERN.length]);        
      }      
      throw new IOException("oups, we're dead");
    }
    
    out.close();
  }
}