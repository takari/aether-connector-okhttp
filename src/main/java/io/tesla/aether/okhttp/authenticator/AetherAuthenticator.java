package io.tesla.aether.okhttp.authenticator;

import java.io.IOException;
import java.net.Proxy;
import java.net.URL;
import java.util.List;

import com.squareup.okhttp.OkAuthenticator;

public class AetherAuthenticator implements OkAuthenticator {

  private String username;
  private String password;
  
  public AetherAuthenticator(String username, String password) {
    this.username = username;
    this.password = password;
  }

  @Override
  public Credential authenticate(Proxy proxy, URL url, List<Challenge> challenges) throws IOException {
    return Credential.basic(username, password);
  }

  @Override
  public Credential authenticateProxy(Proxy proxy, URL url, List<Challenge> challenges) throws IOException {
    return Credential.basic(username, password);
  }

}
