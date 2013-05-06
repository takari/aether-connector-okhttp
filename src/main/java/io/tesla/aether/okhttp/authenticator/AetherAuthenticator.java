package io.tesla.aether.okhttp.authenticator;

import java.io.IOException;
import java.net.Proxy;
import java.net.URL;
import java.util.List;

import com.squareup.okhttp.OkAuthenticator;

public class AetherAuthenticator implements OkAuthenticator {

  private String username;
  private String password;
  private String proxyUsername;
  private String proxyPassword;
  
  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public String getProxyUsername() {
    return proxyUsername;
  }

  public void setProxyUsername(String proxyUsername) {
    this.proxyUsername = proxyUsername;
  }

  public String getProxyPassword() {
    return proxyPassword;
  }

  public void setProxyPassword(String proxyPassword) {
    this.proxyPassword = proxyPassword;
  }

  @Override
  public Credential authenticate(Proxy proxy, URL url, List<Challenge> challenges) throws IOException {
    return Credential.basic(username, password);
  }

  @Override
  public Credential authenticateProxy(Proxy proxy, URL url, List<Challenge> challenges) throws IOException {
    return Credential.basic(proxyUsername, proxyPassword);
  }

}
