package com.chrisdalke.minechat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Logger;

import static com.chrisdalke.minechat.MinecraftGroupmeChatPlugin.CONFIG_SERVER_PORT;

public class WebhookServerRunnable implements Runnable {

    private final MinecraftGroupmeChatPlugin parentPluginInstance;
    private ServerSocket serverSocket;
    private Logger logger;
    private FileConfiguration config;

    public WebhookServerRunnable(MinecraftGroupmeChatPlugin parentPluginInstance) {
        this.parentPluginInstance = parentPluginInstance;
        this.logger = parentPluginInstance.getLogger();
        this.config = parentPluginInstance.getConfigFile();
    }

    public void cleanup(){
        try {
            serverSocket.close();
        } catch (Exception e){
            logger.severe("Failed to stop GroupMe webhook server! Exception: "+e.getMessage());
        }
        logger.info("Stopped GroupMe webhook server.");
    }

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(config.getInt(CONFIG_SERVER_PORT));
            logger.info("Started MineChat server socket on port "+config.getInt(CONFIG_SERVER_PORT));

            //noinspection InfiniteLoopStatement
            while (true) {
                Socket clientSocket = serverSocket.accept();

                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                BufferedWriter out = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));

                in.readLine();
                String line;
                int postDataI = 0;
                boolean hasHeader = false;
                while ((line = in.readLine()) != null && (line.length() != 0)) {
                    line = line.toLowerCase();
                    if (line.contains("content-length:")) {
                        postDataI = Integer.parseInt(line.substring(line.indexOf("content-length:") + 16));
                    }
                    hasHeader = true;
                }

                String postData = "";
                if (postDataI > 0) {
                    char[] charArray = new char[postDataI];
                    in.read(charArray, 0, postDataI);
                    postData = new String(charArray);
                }

                String message = postData;
                // Send OK response
                out.write("HTTP/1.1 200 OK\r\n\r\n");
                out.flush();

                if (hasHeader) {
                    //We got the line, try to parse it into JSON and send message
                    try {
                        ObjectMapper mapper = new ObjectMapper();
                        JsonNode actualObj = mapper.readTree(message);

                        String jsonName = actualObj.get("name").textValue();
                        String jsonMessage = actualObj.get("text").textValue();

                        if (actualObj.get("sender_type").textValue().equals("user")) {
                            parentPluginInstance.sendMinecraftChatMessage(jsonName,jsonMessage);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                in.close();
                out.close();
                clientSocket.close();
            }
        } catch (IOException e) {
            logger.severe("Unable to process client chat message. ");
            e.printStackTrace();

            // Close the server and restart.
            Bukkit.getScheduler().runTaskAsynchronously(parentPluginInstance, parentPluginInstance::restartWebhookServer);
        }
    }
}
