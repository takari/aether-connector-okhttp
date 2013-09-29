package io.tesla.aether.connector.test.suite;

public class PutSslTest extends PutTest {

  @Override
  protected void configureTest() throws Exception {
    enableSsl();
  }
}
