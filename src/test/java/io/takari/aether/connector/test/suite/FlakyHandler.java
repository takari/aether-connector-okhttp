/**
 * Copyright (c) 2012 to original author or authors
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.aether.connector.test.suite;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

class FlakyHandler extends AbstractHandler {

  private static final Pattern RANGE = Pattern.compile("bytes=([0-9]+)-");
  private final int requiredRequests;
  private final Map<String, Integer> madeRequests;
  final int totalSize;
  private final int chunkSize;
  private boolean supportRanges;

  public FlakyHandler(int requiredRequests, boolean supportRanges) {
    this.requiredRequests = requiredRequests;
    this.supportRanges = supportRanges;
    madeRequests = new ConcurrentHashMap<String, Integer>();
    totalSize = 1024 * 128;
    chunkSize = (requiredRequests > 1) ? totalSize / (requiredRequests - 1) - 1 : totalSize;
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

    int lb = 0, ub = totalSize - 1;

    if (supportRanges) {
      String range = request.getHeader("Range");
      if (range != null && range.matches(RANGE.pattern())) {
        Matcher m = RANGE.matcher(range);
        m.matches();
        lb = Integer.parseInt(m.group(1));
      }
      if (lb > 0) {
        response.setHeader("Accept-Ranges", "bytes");
        response.setHeader("Content-Range", "bytes " + lb + "-" + ub + "/" + totalSize);
      }      
    }

    response.setStatus((lb > 0) ? HttpURLConnection.HTTP_PARTIAL : HttpURLConnection.HTTP_OK);
    response.setContentType("Content-type: text/plain; charset=UTF-8");
    response.setContentLength(totalSize - lb);
    response.flushBuffer();      
    
    OutputStream out = response.getOutputStream();

    for (int i = lb, j = 0; i <= ub; i++, j++) {
      if (j >= chunkSize && supportRanges) {
        out.flush();
        throw new IOException("oups, we're dead");
      }

      out.write(GetResumeTest.CONTENT_PATTERN[i % GetResumeTest.CONTENT_PATTERN.length]);
    }

    out.close();
  }

}