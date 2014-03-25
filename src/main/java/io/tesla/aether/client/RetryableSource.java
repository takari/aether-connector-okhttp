package io.tesla.aether.client;

import java.io.IOException;
import java.io.OutputStream;

public interface RetryableSource {

  public void copyTo(OutputStream os) throws IOException;

  public long length();

}
