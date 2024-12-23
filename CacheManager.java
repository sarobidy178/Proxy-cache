package proxy;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class CacheManager {
    private ProxyCache proxyCache;
    private int managementPort;

    public CacheManager(ProxyCache proxyCache, int managementPort) {
        this.proxyCache = proxyCache;
        this.managementPort = managementPort;
    }

    public void startManagementServer() {
        try {
            ServerSocket managementSocket = new ServerSocket(managementPort);

            new Thread(() -> {
                while (true) {
                    try {
                        Socket clientSocket = managementSocket.accept();
                        handleManagementRequest(clientSocket);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }).start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // private void handleManagementRequest(Socket clientSocket) {
    //     try {
    //         BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
    //         PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);

    //         String inputLine = in.readLine();
    //         if (inputLine != null) {
    //             if (inputLine.trim().equalsIgnoreCase("clear_cache")) {
    //                 proxyCache.clearCache();
    //                 out.println("Cache cleared successfully.");
    //                 System.out.println("Cache cleared via management interface.");
    //             } else {
    //                 out.println("Unknown command. Use 'clear_cache'.");
    //             }
    //         }

    //         in.close();
    //         out.close();
    //         clientSocket.close();
    //     } catch (Exception e) {
    //         e.printStackTrace();
    //     }
    // }

    private void handleManagementRequest(Socket clientSocket) {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
    
            String inputLine = in.readLine();
            if (inputLine != null) {
                String[] parts = inputLine.split(" ");
                String command = parts[0].trim().toLowerCase();
    
                switch (command) {
                    case "clear_cache":
                        proxyCache.clearCache();
                        out.println("Cache cleared successfully.");
                        break;
    
                    case "list_cache":
                        proxyCache.getCache().listCache();
                        out.println("Cache entries listed.");
                        break;
    
                    case "remove_cache":
                        if (parts.length < 2) {
                            out.println("Usage: remove_cache <key>");
                        } else {
                            String key = parts[1];
                            proxyCache.getCache().removeCacheEntry(key);
                            out.println("Cache entry " + key + " removed.");
                        }
                        break;
    
                    default:
                        out.println("Unknown command. Use 'list_cache', 'remove_cache <key>', or 'clear_cache'.");
                }
            }
    
            in.close();
            out.close();
            clientSocket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }     
}