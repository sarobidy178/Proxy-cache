package main;

import proxy.*;
import java.io.*;
import java.net.*;
import java.util.Properties;

public class ProxyServeur {
    public static void main(String[] args) throws Exception {
        try {
            int port = Integer.parseInt(ProxyCache.getConfig("port"));
            
            long ttl = Long.parseLong(ProxyCache.getConfig("TTL"));
            
            int managementPort = Integer.parseInt(ProxyCache.getConfig("managementPort"));
            
            ProxyCache proxyServer = new ProxyCache(port, ttl, managementPort);
            proxyServer.start();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
