package com.almworks.http;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

public class NtlmDemo {
  public static void main(String[] args) throws IOException {
    final URL addre = new URL("http://iistest/icons/box.gif");
    HttpURLConnection c = (HttpURLConnection) addre.openConnection();
    c.setAllowUserInteraction(true);
    c.setInstanceFollowRedirects(true);
    c.connect();
    InputStream is = c.getInputStream();
    BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
    while (true) {
      String s = reader.readLine();
      if (s == null)
        break;
      System.out.println(s);
    }
    is.close();
    c.disconnect();
  }
}
