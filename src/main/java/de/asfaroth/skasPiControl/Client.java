package de.asfaroth.skasPiControl;

/*
 * Simple class that only holds connection information for a specific client.
 */
public class Client {
  public String ip;
  public int port;
  public String user;
  public String password;

  public Client(String ip, int port, String user, String password) {
    this.ip = ip;
    this.port = port;
    this.user = user;
    this.password = password;
  }
}
