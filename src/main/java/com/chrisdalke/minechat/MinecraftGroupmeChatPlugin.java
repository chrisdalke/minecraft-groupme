package com.chrisdalke.minechat;

import github.io.wreed12345.Bot;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import java.net.ServerSocket;
import java.util.Objects;
import java.util.logging.Logger;

public class MinecraftGroupmeChatPlugin extends JavaPlugin implements Listener,CommandExecutor {

    // Constant strings used to pull from configuration
    public static final String CONFIG_BOT_ID = "groupme_bot_id";
    public static final String CONFIG_SERVER_PORT = "server_port";
    public static final String CONFIG_MESSAGES_JOIN_LEAVE = "messages_join_leave";
    public static final String CONFIG_MESSAGES_DEATH = "messages_death";
    public static final String CONFIG_MESSAGES_CHAT = "messages_chat";

    // Instance member variables
    @Getter
    private Logger logger;
    @Getter
    private FileConfiguration configFile = getConfig();
    private Bot groupmeBot;
    private WebhookServerRunnable webhookServerInstance;
    private BukkitTask webhookServerTask;


    /**
     * Triggered when the plugin is enabled.
     */
    @Override
    public void onEnable(){
        logger = getLogger();
        logger.info("Initializing Minecraft-GroupMe Chat plugin...");

        // Create default configuration
        configFile.addDefault(CONFIG_BOT_ID,"none");
        configFile.addDefault(CONFIG_SERVER_PORT,0);
        configFile.addDefault(CONFIG_MESSAGES_JOIN_LEAVE,false);
        configFile.addDefault(CONFIG_MESSAGES_DEATH,false);
        configFile.addDefault(CONFIG_MESSAGES_CHAT,false);
        configFile.options().copyDefaults(true);
        saveConfig();

        // Validate config
        if (Objects.equals(configFile.getString(CONFIG_BOT_ID), "none")){
            logger.warning("Minecraft-GroupMe Plugin: Warning - No GroupMe bot ID is specified! The plugin will not post to GroupMe.");
        }
        if (configFile.getInt(CONFIG_SERVER_PORT) == 0){
            logger.warning("Minecraft-GroupMe Plugin: Warning - No Server port is specified! The plugin will not receive posts from GroupMe.");
        }

        // Create GroupMe Bot
        groupmeBot = new Bot(configFile.getString(CONFIG_BOT_ID));

        // Start instance of server which listens for GroupMe webhook messages
        startWebhookServer();

        // Register this class as an event handler for player events
        getServer().getPluginManager().registerEvents(this, this);
    }

    /**
     * Triggered when the plugin is disabled.
     */
    @Override
    public void onDisable(){
        //Fired when the server stops and disables all plugins
        getLogger().info("Shutting down Minecraft-GroupMe Chat plugin...");
        stopWebhookServer();
    }

    /**
     * Start a webhook server, which listens for messages from GroupMe.
     * This handles incoming messages from the group, which are posted in the Minecraft chat.
     */
    public void startWebhookServer(){
        final JavaPlugin plugin = this;

        // Start listening on the port in a separate thread
        webhookServerInstance = new WebhookServerRunnable(this);
        webhookServerTask = Bukkit.getScheduler().runTaskAsynchronously(this, webhookServerInstance);
    }

    /**
     * Stops the webhook server task.
     */
    public void stopWebhookServer(){
        Bukkit.getScheduler().cancelTask(webhookServerTask.getTaskId());
        webhookServerInstance.cleanup();
        webhookServerInstance = null;
    }

    /**
     * Restarts the webhook server task.
     */
    public void restartWebhookServer(){
        getLogger().info("Restarting GroupMe webhook server...");
        stopWebhookServer();
        startWebhookServer();
    }

    /**
     * Send message to GroupMe when a player chats.
     * @param event An event holding the chat information.
     */
    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (configFile.getBoolean(CONFIG_MESSAGES_CHAT)){
            String formattedChatMessage = String.format("%s: %s",
                    event.getPlayer().getPlayerListName(),
                    event.getMessage());

            sendGroupmeMessage(formattedChatMessage);
        }
    }

    /**
     * Send message to GroupMe when a player joins the server.
     * @param event An event holding the details of the player joining.
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (configFile.getBoolean(CONFIG_MESSAGES_JOIN_LEAVE)){
            Player player = event.getPlayer();
            sendGroupmeMessage(""+player.getPlayerListName()+" has joined the server.");
        }
    }

    /**
     * Send message to GroupMe when a player quits the server.
     * @param event An event holding the details of the player quitting.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event){
        if (configFile.getBoolean(CONFIG_MESSAGES_JOIN_LEAVE)){
            Player player = event.getPlayer();
            sendGroupmeMessage(""+player.getPlayerListName()+" has left the server.");
        }
    }

    /**
     * Send message to GroupMe when a player dies.
     * @param event An event holding information about the death.
     */
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event){
        if (configFile.getBoolean(CONFIG_MESSAGES_DEATH)){
            sendGroupmeMessage(event.getDeathMessage());
        }
    }

    /**
     * Send a message to the GroupMe chat.
     * @param message The raw message to send.
     */
    private void sendGroupmeMessage(String message){
        final String chatMessage = message;
        Bukkit.getScheduler().runTaskAsynchronously(this, new Runnable() {
            @Override
            public void run() {
                groupmeBot.sendTextMessage(chatMessage);
            }
        });
    }

    /**
     * Send a message to the Minecraft chat.
     * @param username The username of the player to display.
     * @param message The raw message string to display. Will be processed to remove invalid characters.
     */
    void sendMinecraftChatMessage(String username, String message){
        String joinedMessage = String.format(
                "<%s%s%s> %s",
                ChatColor.AQUA,
                username,
                ChatColor.WHITE,
                message);
        getServer().broadcastMessage(joinedMessage);
    }
}
