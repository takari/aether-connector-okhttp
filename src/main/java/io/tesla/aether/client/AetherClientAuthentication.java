package io.tesla.aether.client;

public class AetherClientAuthentication {
  
  private String username;
  private String password;
  
  public AetherClientAuthentication(String username, String password) {
    this.username = username;
    this.password = password;
  }

  public String getUsername() {
    return username;
  }

  public String getPassword() {
    return password;
  }
}
