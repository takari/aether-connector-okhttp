package io.takari.aether.connector;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.aether.spi.connector.transport.AbstractTransporter;
import org.eclipse.aether.spi.connector.transport.GetTask;
import org.eclipse.aether.spi.connector.transport.PeekTask;
import org.eclipse.aether.spi.connector.transport.PutTask;
import org.eclipse.aether.transfer.TransferCancelledException;

import com.squareup.okhttp.HttpUrl;

import io.takari.aether.client.Response;
import io.takari.aether.client.RetryableSource;
import io.takari.aether.okhttp.OkHttpAetherClient;

class AetherTransporter extends AbstractTransporter {

  private final HttpUrl baseurl;
  private final OkHttpAetherClient aetherClient;

  public AetherTransporter(String baseurl, OkHttpAetherClient aetherClient) {
    this.aetherClient = aetherClient;

    HttpUrl url = HttpUrl.parse(baseurl);
    if (!url.encodedPath().endsWith("/")) {
      url = url.newBuilder().addPathSegment("").build();
    }
    this.baseurl = url;
  }

  @Override
  public int classify(Throwable error) {
    if (error instanceof ResourceDoesNotExistException) {
      return ERROR_NOT_FOUND;
    }
    return ERROR_OTHER;
  }

  @Override
  protected void implPeek(PeekTask task) throws Exception {
    String url = buildUrl(task.getLocation());
    Response response = aetherClient.head(url);
    handleResponseCode(url, response);
  }

  @Override
  protected void implGet(GetTask task) throws Exception {
    String url = buildUrl(task.getLocation());

    boolean resume = task.getResumeOffset() > 0;

    Map<String, String> headers = new HashMap<>();
    if (resume) {
      headers.put("Range", "bytes=" + task.getResumeOffset() + "-");
      headers.put("Accept-Encoding", "identity");
    }

    Response response = aetherClient.get(url, headers);

    handleResponseCode(url, response);

    try (InputStream in = response.getInputStream()) {
      long length = getContentLength(response);
      utilGet(task, in, false, length, resume);
    }
  }

  private long getContentLength(Response response) {
    String length = response.getHeader("Content-Length");
    return length != null ? Long.parseLong(length) : -1;
  }

  private String buildUrl(URI ref) {
    return baseurl.resolve(ref.toString()).toString();
  }

  @Override
  protected void implPut(final PutTask task) throws Exception {
    String url = buildUrl(task.getLocation());

    class WrappedTransderException extends RuntimeException {
      private static final long serialVersionUID = 1L;

      public WrappedTransderException(TransferCancelledException cause) {
        super(cause);
      }
    };

    try {
      Response response = aetherClient.put(url, new RetryableSource() {
        @Override
        public long length() {
          return task.getDataLength();
        }

        @Override
        public void copyTo(OutputStream os) throws IOException {
          try {
            utilPut(task, os, false);
          } catch (TransferCancelledException e) {
            throw new WrappedTransderException(e);
          }
        }
      });
      handleResponseCode(url, response);
    } catch (WrappedTransderException e) {
      throw (TransferCancelledException) e.getCause();
    }
  }

  @Override
  protected void implClose() {
    aetherClient.close(); // does not do anything for okhttp
  }

  private void handleResponseCode(String url, Response response)
      throws AuthorizationException, ResourceDoesNotExistException, TransferException, IOException {
    int responseCode = response.getStatusCode();
    String responseMsg = response.getStatusMessage();

    if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
      throw new ResourceDoesNotExistException(
          String.format("Unable to locate resource %s. Error code %s", url, responseCode));
    }

    if (responseCode == HttpURLConnection.HTTP_FORBIDDEN
        || responseCode == HttpURLConnection.HTTP_UNAUTHORIZED
        || responseCode == HttpURLConnection.HTTP_PROXY_AUTH) {
      throw new AuthorizationException(
          String.format("Access denied to %s. Error code %s, %s", url, responseCode, responseMsg));
    }

    if (responseCode >= HttpURLConnection.HTTP_MULT_CHOICE) {
      throw new TransferException(String.format("Failed to transfer %s. Error code %s, %s", url,
          responseCode, responseMsg));
    }
  }
}
