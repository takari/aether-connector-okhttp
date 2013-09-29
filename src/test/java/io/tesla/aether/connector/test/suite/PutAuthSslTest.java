package io.tesla.aether.connector.test.suite;


public class PutAuthSslTest extends PutAuthTest {

  @Override
  protected void configureTest() throws Exception {
    enableSsl();
    enableConnectingWithBasicAuth();
  }
}
