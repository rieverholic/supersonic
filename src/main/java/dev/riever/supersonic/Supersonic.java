package dev.riever.supersonic;

import com.google.inject.Inject;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.CommandSource;
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
import org.slf4j.Logger;

import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

@Plugin(id = "supersonic", name = "Supersonic", version = "0.3.0-SNAPSHOT",
        url = "https://github.com/rieverholic/supersonic", description = "A velocity plugin for Joon's Dreamyard", authors = {"Riever"})
public class Supersonic {

    private final ProxyServer proxyServer;
    private final Logger logger;
    private final SupersonicConfigManager configManager;
    private final Random random;
    private final PlayerAuthManager playerAuthManager;

    private DiscordBot discordBot;

    @Inject
    public Supersonic(ProxyServer proxyServer, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxyServer = proxyServer;
        this.logger = logger;
        Path configFile = dataDirectory.resolve("config.yml");
        Path whitelistFile = dataDirectory.resolve("whitelist.yml");
        this.configManager = new SupersonicConfigManager(configFile, logger);
        this.random = new Random();
        this.playerAuthManager = new PlayerAuthManager(whitelistFile, this.random);
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        SupersonicConfig config = this.configManager.initialize();
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

        // Initialize random
        String entropy = config.getSeed();
        long seed;
        if (entropy != null && !entropy.isEmpty()) {
            // If entropy can be casted to long, use it as a seed
            try {
                seed = Long.parseLong(entropy);
            } catch (NumberFormatException e) {
                seed = entropy.hashCode();
            }
            seed ^= Instant.now().getEpochSecond();
            this.random.setSeed(seed);
        }

        // Initialize Minecraft command
        CommandManager commandManager = this.proxyServer.getCommandManager();
        CommandMeta commandMeta = commandManager.metaBuilder("dsay")
                .plugin(this)
                .build();

        BrigadierCommand discordCommand = ChatCommand.create(this.discordBot.getCrossChatManager());
        commandManager.register(commandMeta, discordCommand);
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
            Component reply = Component.text("Welcome to Joon's Dreamyard! Please submit this code in Discord with §a/auth §b<code>§f command:")
                    .append(Component.newline())
                    .append(Component.newline())
                    .append(Component.text("§l" + otp + "§r"))
                    .append(Component.newline())
                    .append(Component.newline())
                    .append(Component.text("This code will expire in 5 minutes."));
            event.setResult(LoginEvent.ComponentResult.denied(reply));
            this.logger.info("Authentication request from {} ({})", player.getUsername(), player.getUniqueId());
        }
    }
}

final class ChatCommand {
    public static BrigadierCommand create(CrossChatManager crossChatManager) {
        LiteralCommandNode<CommandSource> discordNode = BrigadierCommand.literalArgumentBuilder("dsay")
                .then(BrigadierCommand.requiredArgumentBuilder("message", StringArgumentType.greedyString())
                        .suggests((ctx, builder) -> {
                            builder.suggest("hello");
                            return builder.buildFuture();
                        })
                        .executes(context -> {
                            String content = context.getArgument("message", String.class);
                            CommandSource source = context.getSource();
                            if (source instanceof Player player) {
                                String replyMessage = crossChatManager.minecraftToDiscord(player, content);
                                Component text = Component.text(replyMessage);
                                RegisteredServer server = player.getCurrentServer()
                                        .map(ServerConnection::getServer)
                                        .orElse(null);
                                Objects.requireNonNullElse(server, player).sendMessage(text);
                                return Command.SINGLE_SUCCESS;
                            } else {
                                return BrigadierCommand.FORWARD;
                            }
                        })
                )
                .build();
        return new BrigadierCommand(discordNode);
    }
}
