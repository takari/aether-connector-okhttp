package io.tesla.aether.connector.test.suite;

public class GetProxyAuthSslTest extends GetProxyAuthTest {

  @Override
  protected void configureTest() throws Exception {
    enableSsl();
    enableProxyWithAuth();
  }
}
