package io.tesla.aether.connector.test.suite;


public class GetAuthSslTest extends GetAuthTest {

  @Override
  protected void configureTest() throws Exception {
    enableSsl();
    enableConnectingWithBasicAuth();
  }
}
