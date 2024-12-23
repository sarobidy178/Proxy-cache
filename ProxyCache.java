package proxy;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import proxy.ExpiringCache;

import java.net.UnknownHostException;

public class ProxyCache {
    private int port;
    private ExpiringCache cache;
    private long ttl;
    private CacheManager cacheManager;
    private int managementPort;

    public ProxyCache(int port, long ttl, int managementPort) {
        this.port = port;
        this.ttl = ttl;
        this.managementPort = managementPort;
        this.cache = new ExpiringCache(ttl);
        
        this.cacheManager = new CacheManager(this, managementPort);
        this.cacheManager.startManagementServer();
    }

    public void clearCache() {
        System.out.println("Clearing entire cache...");
        this.cache = new ExpiringCache(this.ttl);
    }

    public void start() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(this.port)) {
            System.out.println("Proxy en écoute sur le port " + this.port);
            System.out.println("Cache Management port: " + this.managementPort);
            System.out.println("TTL : " + this.ttl + " seconds ");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connecté: " + clientSocket.getInetAddress());

                new Thread(() -> {
                    try {
                        this.handleRequest(clientSocket);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // private String getHttpHeaders(HttpURLConnection connection) throws IOException {
    //     StringBuilder headers = new StringBuilder();

    //     for (Map.Entry<String, List<String>> header : connection.getHeaderFields().entrySet()) {
    //         if (header.getKey() != null) {
    //             headers.append(header.getKey())
    //                     .append(": ")
    //                     .append(String.join(", ", header.getValue()))
    //                     .append("\r\n");
    //         }
    //     }

    //     headers.append("\r\n");

    //     return headers.toString();
    // }

    public String getContentType(String request) {
        if (request.endsWith(".php")) {
            return "text/html; charset=UTF-8";  
        } else if (request.endsWith(".jpg") || request.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (request.endsWith(".png")) {
            return "image/png";
        } else if (request.endsWith(".gif")) {
            return "image/gif";
        } else if (request.endsWith(".css")) {
            return "text/css";
        } else if (request.endsWith(".js")) {
            return "application/javascript";
        } else {
            return "text/html; charset=UTF-8";
        }
    }

    private String getTheUrl(String lienClient) throws Exception{
        String[] parts = lienClient.split(" ");
        if (parts.length < 2) {
            System.out.println("Probleme avec le lien inséré par client");
        }
        String url = parts[1];

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://" + getConfig("host") + url; 
        }
        
        return url;
    }
    
    private byte[] getFromServer(String lienClient) throws Exception {
        String serverOrigin = "";

        String url = getTheUrl(lienClient);

        byte[] responseBytes = null;

        try {
            URL parsedUrl = new URL(url);
            serverOrigin = parsedUrl.getHost();
            int serverPort = parsedUrl.getPort() == -1 ? parsedUrl.getDefaultPort() : parsedUrl.getPort();

            System.out.println("Serveur d'origine : " + serverOrigin + " sur le port " + serverPort);

            HttpURLConnection connection = (HttpURLConnection) parsedUrl.openConnection();
            connection.setRequestMethod("GET");
            connection.setDoInput(true);

            InputStream inputStream = connection.getInputStream(); 
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] data = new byte[1024];
            int bytesRead;

            while ((bytesRead = inputStream.read(data)) != -1) {
                buffer.write(data, 0, bytesRead);
            }
            responseBytes = buffer.toByteArray();

            connection.disconnect();

        }
        catch(Exception e) {
            if(e instanceof UnknownHostException){
                System.out.println("Unknown host " + serverOrigin);
            } else{
                e.printStackTrace();
                throw e;
            }
        }
        return responseBytes;
    }

    private void insertInCache(String request, byte[] response) {
        System.out.println("Réponse enregistrée dans cache");
        String nom = extractKeyFromRequest(request);
        this.cache.put(nom, response);
    }

    private boolean isInCache(String request) {
        String nom = extractKeyFromRequest(request);
        return this.cache.containsKey(nom);
    }

    private byte[] getFromCache(String request) {
        String nom = extractKeyFromRequest(request);
        byte[] result = this.cache.get(nom);
        System.out.println("Données lues depuis le cache");
        return result;
    }

    private String extractKeyFromRequest(String request) {
        String[] parts = request.split("/");
        String result = parts.length > 1 ? parts[parts.length-1] : request;
        return result;
    }

    void handleRequest(Socket client) {
        try {
            BufferedReader readClient = new BufferedReader(new InputStreamReader(client.getInputStream()));
            OutputStream clientOutput = client.getOutputStream();

            String lienClient = readClient.readLine();

            if (lienClient != null && (lienClient.startsWith("GET") || (lienClient.startsWith("POST") && !lienClient.split(" ")[1].equals("http://o.pki.goog/wr2")))) {
                System.out.println("Requête du client : " + lienClient);
                String request = lienClient.split(" ")[1];
                byte[] reponseVersClient = null;

                if (isInCache(request)) {
                    System.out.println("Récupération depuis le cache");
                    reponseVersClient = getFromCache(request);
                } else {
                    System.out.println("Récupération depuis le serveur");
                    reponseVersClient = getFromServer(lienClient);
                    insertInCache(request, reponseVersClient);
                }

                int contentLength = reponseVersClient.length;
                String responseHeaders = "HTTP/1.1 200 OK\r\n" +
                                        "Content-Type: " + getContentType(getTheUrl(lienClient)) + "\r\n" +
                                        "Content-Length: " + contentLength + "\r\n" +
                                        "Connection: close\r\n\r\n";
                //String responseHeaders = getHttpHeaders(null);

                clientOutput.write(responseHeaders.getBytes("UTF-8"));
                clientOutput.write(reponseVersClient);
            } else {
                String errorResponse = "HTTP/1.1 400 Bad Request\r\n" +
                                    "Content-Type: text/plain; charset=UTF-8\r\n" +
                                    "Content-Length: 0\r\n" +
                                    "Connection: close\r\n\r\n";
                clientOutput.write(errorResponse.getBytes("UTF-8"));
            }

            clientOutput.flush();
            clientOutput.close();
            readClient.close();
            client.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String getConfig(String pptName) throws Exception {
        try {
            Properties config = new Properties();
            FileInputStream configFile = new FileInputStream("config.txt");
            config.load(configFile);
            String result = config.getProperty(pptName);
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    public ExpiringCache getCache() {
        return this.cache;
    }    
}