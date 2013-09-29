package io.tesla.aether.connector.test.suite;

public class GetSslTest extends GetTest {

  @Override
  protected void configureTest() throws Exception {
    enableSsl();    
  }
}
