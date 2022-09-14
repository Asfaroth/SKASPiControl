package de.asfaroth.skasPiControl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/*
 * Main class.
 */
public class App {
  /**
   * Main method that first parses the settings and then opens a ServerSocket to
   * handle requests.
   * 
   * @param args command line inputs are not used
   */
  public static void main(String[] args) {
    try {
      // First applying settings. If the settings file is not present the application
      // will not start.

      JSONObject settings = new JSONObject(String.join("",
          Files.readAllLines(Paths.get("settings.json"), StandardCharsets.UTF_8)));

      Client[] clients = new Client[settings.getJSONArray("clients").length()];
      for (int i = 0; i < settings.getJSONArray("clients").length(); i++) {
        JSONObject clientSetting = settings.getJSONArray("clients").getJSONObject(i);
        clients[i] = new Client(clientSetting.getString("ip"),
            clientSetting.getInt("port"),
            clientSetting.getString("user"),
            clientSetting.getString("pass"));
      }
      System.out.println("Found " + clients.length + " clients.");

      try {
        ServerSocket serverSocket = new ServerSocket(settings.getInt("server_port"));

        while (true) {
          Socket clientSocket = serverSocket.accept();

          if (checkIP(settings.getJSONArray("whitelist_ips"),
              clientSocket.getInetAddress().getHostAddress())) {
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            String calledPath = "/";
            while (true) {
              String data = in.readLine();

              if (data == null || data.trim().isEmpty())
                break;

              if (data.startsWith("GET")) {
                String[] splitString = data.split(" ");
                calledPath = splitString[1];
              }
            }

            if (calledPath.equals(settings.getJSONObject("paths").getString("toggle"))) {
              // toggle toggles the client/shelly state
              PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);

              JSONObject shellyProperties = settings.getJSONObject("shelly_properties");
              try {
                handleRequest(clients,
                    shellyProperties.getString("ip"),
                    shellyProperties.getInt("shutdown_time"),
                    settings.getInt("request_timeout"));

                out.print("HTTP/1.1 200 OK\r\n");
                out.print("Content-Type: text/plain;");
                out.print("\r\n\r\n");

                out.println("OK.");
              } catch (JSONException ex) {
                // hijacked the JSONException which can only occur if the shelly is not properly
                // online
                out.print("HTTP/1.1 502 Bad Gateway\r\n");
                out.print("Content-Type: text/plain;");
                out.print("\r\n\r\n");

                out.println("Shelly not online.");
              }
            } else if (calledPath.equals(settings.getJSONObject("paths").getString("stop"))) {
              // stop stops the application
              PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
              out.print("HTTP/1.1 200 OK\r\n");
              out.print("Content-Type: text/plain;");
              out.print("\r\n\r\n");

              out.println("Server stopped.");
              break;
            } else {
              PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
              out.print("HTTP/1.1 404 NOT FOUND\r\n");
              out.print("Content-Type: text/plain;");
              out.print("\r\n\r\n");

              out.println("URI not found.");
            }
          } else {
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
            out.print("HTTP/1.1 401 Unauthorized\r\n");
            out.print("Content-Type: text/plain;");
            out.print("\r\n\r\n");

            out.println("Nope.");
          }

          clientSocket.close();
        }
        serverSocket.close();
      } catch (IOException ex) {
        ex.printStackTrace();
      }
    } catch (IOException ex) {
      ex.printStackTrace();
    }
  }

  /**
   * Checks if the calling IP is in the whitelist.
   * 
   * @param whitelist the whitelist in which the ip should be present
   * @param ip        the ip to look for
   * @return true if he ip is present, false otherwise
   */
  private static boolean checkIP(JSONArray whitelist, String ip) {
    for (int i = 0; i < whitelist.length(); i++) {
      if (whitelist.getString(i).equals(ip))
        return true;
    }
    return false;
  }

  /**
   * Handles a succesfull request. First it fetches the required information from
   * the Shelly and then executes actions accordingly.
   * 
   * @param clients      the client terminals' objects
   * @param shellyIP     the shelly's ip address
   * @param shutdownTime the time between the shutdown commands and the shelly
   *                     power cut
   * @param timeout      the time after which connection attempts should be
   *                     canceled
   * @throws JSONException is thrown if the shelly is not online
   */
  private static void handleRequest(Client[] clients, String shellyIP, int shutdownTime, int timeout)
      throws JSONException {
    String shellyInfo = getRequest("http://" + shellyIP + "/relay/0", timeout);
    JSONObject parsedInfo = new JSONObject(shellyInfo);

    if (!parsedInfo.getBoolean("has_timer")) {
      if (parsedInfo.getBoolean("ison")) {
        System.out.println("Now starting the shutdown routine.");

        for (Client client : clients) {
          shutdown(client);
        }

        System.out.println("Now turning off Shelly...");
        getRequest("http://" + shellyIP + "/relay/0?turn=on&timer=" + shutdownTime, timeout);
      } else if (!parsedInfo.getBoolean("has_timer")) {
        System.out.println("Now turning on Shelly...");
        getRequest("http://" + shellyIP + "/relay/0?turn=on", timeout);
      }
    } else {
      System.out.println("Timer already running, aborting.");
    }
  }

  /**
   * Shuts down the given Client via SSH.
   * 
   * @param client the client to shutdown
   */
  private static void shutdown(Client client) {
    try {
      java.util.Properties config = new java.util.Properties();
      config.put("StrictHostKeyChecking", "no");
      JSch jsch = new JSch();
      Session session = jsch.getSession(client.user, client.ip, client.port);
      session.setPassword(client.password);
      session.setConfig(config);
      session.connect();
      System.out.println("Connected");

      Channel channel = session.openChannel("exec");
      ((ChannelExec) channel).setCommand("sudo shutdown now");
      channel.connect();

      channel.disconnect();
      session.disconnect();
      System.out.println("Shutdown of " + client.ip + " executed.");
    } catch (JSchException ex) {
      ex.printStackTrace();
    }
  }

  /**
   * Executes a single GET request and returns its contents as String.
   * 
   * @param address the address to which the request should be executed
   * @param timeout the timeout after which the attempts should be canceled
   * @return the returned data or an empty String if the request fails
   */
  private static String getRequest(String address, int timeout) {
    try {
      StringBuilder result = new StringBuilder();
      URL url = new URL(address);
      HttpURLConnection conn = (HttpURLConnection) url.openConnection();
      conn.setRequestMethod("GET");
      conn.setConnectTimeout(timeout);
      BufferedReader reader = new BufferedReader(
          new InputStreamReader(conn.getInputStream()));
      for (String line; (line = reader.readLine()) != null;) {
        result.append(line);
      }
      return result.toString();
    } catch (MalformedURLException ex) {
      ex.printStackTrace();
    } catch (IOException ex) {
      ex.printStackTrace();
    }
    return "";
  }
}
