package io.tesla.aether.connector.test.suite;

import io.tesla.webserver.WebServer;

import org.eclipse.jetty.util.security.Constraint;

public class GetAuthSslCertTest extends GetAuthTest {

  @Override
  protected void configureTest() throws Exception {
    enableSsl();
  }
  
  @Override
  protected void configureServer(WebServer server) {
    addBehaviour("/repo", generate, expect, provide);    
    server.addAuthentication("/*", Constraint.__CERT_AUTH2);
    server.addUser("user", "password");    
  }
}
