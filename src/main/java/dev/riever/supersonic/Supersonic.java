package dev.riever.supersonic;

import com.google.inject.Inject;
import com.velocitypowered.api.command.*;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import dev.riever.supersonic.config.SupersonicConfig;
import dev.riever.supersonic.config.SupersonicConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

@Plugin(id = "supersonic", name = "Supersonic", version = "0.3.0-SNAPSHOT",
        url = "https://github.com/rieverholic/supersonic", description = "A velocity plugin for Joon's Dreamyard", authors = {"Riever"})
public class Supersonic {

    private final ProxyServer proxyServer;
    private final Logger logger;
    private final Path dataDirectory;
    private final Random random;

    private SupersonicConfigManager configManager;
    private DiscordBot discordBot;
    private PlayerAuthManager playerAuthManager;

    @Inject
    public Supersonic(ProxyServer proxyServer, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxyServer = proxyServer;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        this.random = new Random();
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        // Initialize config
        Path configFile = this.dataDirectory.resolve("config.yml");
        Path whitelistFile = this.dataDirectory.resolve("whitelist.yml");
        this.configManager = new SupersonicConfigManager(configFile, logger);
        SupersonicConfig config = this.configManager.initialize();

        // Initialize random
        String entropy = config.getSeed();
        long seed;
        if (entropy != null && !entropy.isEmpty()) {
            // If entropy can be cast to long, use it as a seed
            try {
                seed = Long.parseLong(entropy);
            } catch (NumberFormatException e) {
                seed = entropy.hashCode();
            }
            seed ^= Instant.now().getEpochSecond();
            this.random.setSeed(seed);
        }

        // Initialize auth manager
        SupersonicConfig.Auth authConfig = config.getAuth();
        this.playerAuthManager = new PlayerAuthManager(
                whitelistFile,
                authConfig.getStorageType(),
                authConfig.getMaxRequestAge(),
                authConfig.getCleanerPeriod(),
                this.random,
                this.logger
        );
        this.playerAuthManager.initialize();

        // Initialize Discord bot
        SupersonicConfig.Discord discordConfig = config.getDiscord();
        this.discordBot = new DiscordBot(
                discordConfig.getBotToken(),
                discordConfig.getChannelId(),
                discordConfig.getRoleId(),
                this.proxyServer,
                this.playerAuthManager,
                this.logger
        );
        this.discordBot.initialize();

        // Initialize Minecraft command
        CommandManager commandManager = this.proxyServer.getCommandManager();
        CommandMeta commandMeta = commandManager.metaBuilder("dsay")
                .plugin(this)
                .build();

        RawCommand chatCommand = new DiscordChatCommand(this.discordBot.getCrossChatManager());
        commandManager.register(commandMeta, chatCommand);
    }

    @Subscribe
    public void onJoin(ServerPostConnectEvent event) {
        Player player = event.getPlayer();
        String username = player.getUsername();
        Optional<ServerConnection> conn = player.getCurrentServer();
        if (conn.isPresent()) {
            ServerInfo serverInfo = conn.get().getServerInfo();
            this.discordBot.sendMessage("**" + username + "** joined `" + serverInfo.getName() + "`.");
        }
    }

    @Subscribe
    public void onLeave(DisconnectEvent event) {
        Player player = event.getPlayer();
        String username = player.getUsername();
        Optional<ServerConnection> conn = player.getCurrentServer();
        if (conn.isPresent()) {
            ServerInfo serverInfo = conn.get().getServerInfo();
            this.discordBot.sendMessage("**" + username + "** left `" + serverInfo.getName() + "`.");
        }
    }

    @Subscribe
    public void onLogin(LoginEvent event) {
        Player player = event.getPlayer();
        if (!this.playerAuthManager.isAuthenticated(player)) {
            String otp = this.playerAuthManager.request(player);
            int maxRequestAge = this.configManager.getConfig().getAuth().getMaxRequestAge();
            Component reply = Component.text("Welcome to Joon's Dreamyard! Please submit this code on Discord with ")
                    .append(Component.text("/auth", NamedTextColor.GREEN))
                    .appendSpace()
                    .append(Component.text("<code>", NamedTextColor.AQUA))
                    .append(Component.text(" command:"))
                    .appendNewline()
                    .appendNewline()
                    .append(Component.text(otp).decorate(TextDecoration.BOLD))
                    .appendNewline()
                    .appendNewline()
                    .append(Component.text("This code will expire in " + maxRequestAge + " minutes."));
            event.setResult(LoginEvent.ComponentResult.denied(reply));
            this.logger.info("Authentication request from {} ({})", player.getUsername(), player.getUniqueId());
        }
    }
}

final class DiscordChatCommand implements RawCommand {
    private final CrossChatManager crossChatManager;

    public DiscordChatCommand(CrossChatManager crossChatManager) {
        this.crossChatManager = crossChatManager;
    }

    @Override
    public void execute(final Invocation invocation) {
        String content = invocation.arguments();
        CommandSource source = invocation.source();
        if (source instanceof Player player) {
            Component replyMessage = crossChatManager.minecraftToDiscord(player, content);
            RegisteredServer server = player.getCurrentServer()
                    .map(ServerConnection::getServer)
                    .orElse(null);
            Objects.requireNonNullElse(server, player).sendMessage(replyMessage);
        }
    }
}
