package dev.riever.supersonic;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import dev.riever.supersonic.config.DiscordChannelManager;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.*;

import static net.dv8tion.jda.api.interactions.commands.OptionType.BOOLEAN;
import static net.dv8tion.jda.api.interactions.commands.OptionType.STRING;

public class DiscordBot {
    private final String token;
    private final DiscordChannelManager channelManager;

    private final ProxyServer proxyServer;
    private final CrossChatManager crossChatManager;
    private final PlayerAuthManager playerAuthManager;
    private final boolean autoAddRole;
    private final Logger logger;

    private JDA jda;

    public DiscordBot(
            String token,
            Path configFile,
            ProxyServer proxyServer,
            PlayerAuthManager playerAuthManager,
            boolean autoAddRole,
            Logger logger
    ) {
        this.token = token;
        this.channelManager = new DiscordChannelManager(configFile);
        this.logger = logger;
        this.proxyServer = proxyServer;
        this.crossChatManager = new CrossChatManager(proxyServer, this, logger);
        this.playerAuthManager = playerAuthManager;
        this.autoAddRole = autoAddRole;
    }

    public void initialize() {
        this.channelManager.load();
        this.jda = JDABuilder.createLight(token)
                .enableIntents(GatewayIntent.GUILD_MEMBERS)
                .build();
        try {
            this.jda.awaitReady();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        this.jda.addEventListener(
                new CommandListener(this, this.logger)
        );

        // Initialize commands
        this.jda.updateCommands().addCommands(
                Commands.slash("servers", "Display all current active servers")
                        .addOption(BOOLEAN, "ephemeral", "Message only shown to you"),
                Commands.slash("say", "Send a system message to a Minecraft server")
                        .addOption(STRING, "server", "Name of the server to send a message to", true)
                        .addOption(STRING, "content", "Message to send", true),
                Commands.slash("auth", "Authenticates a Minecraft account")
                        .addOption(STRING, "code", "The one-time passcode displayed on screen", true),
                Commands.slash("subscribe", "Subscribe to a server and activate cross chat")
                        .addOption(STRING, "server", "Name of the server to subscribe to", true),
                Commands.slash("unsubscribe", "Unsubscribe from a server")
                        .addOption(STRING, "server", "Name of the server to subscribe to", true)
        ).queue();
    }

    public String getDisplayName(Member member) {
        String displayName = member.getNickname();
        if (displayName == null) {
            displayName = member.getEffectiveName();
        }
        return displayName;
    }

    public void broadcast(String sourceServer, String message) {
        this.channelManager.getChannels().forEach(channelId -> {
            if (this.channelManager.isSubscribing(channelId, sourceServer)) {
                this.getChannel(channelId).sendMessage(message).queue();
            }
        });
    }

    public boolean isSubscribed(String channelId) {
        return this.channelManager.contains(channelId);
    }

    public boolean subscribe(String channelId, String serverName) {
        return this.channelManager.subscribe(channelId, serverName);
    }

    public boolean unsubscribe(String channelId, String serverName) {
        return this.channelManager.unsubscribe(channelId, serverName);
    }

    public TextChannel getChannel(String channelId) {
        return this.jda.getTextChannelById(channelId);
    }

    public Guild getGuild(String channelId) {
        return this.getChannel(channelId).getGuild();
    }

    public CrossChatManager getCrossChatManager() {
        return this.crossChatManager;
    }

    public PlayerAuthManager getPlayerAuthManager() {
        return this.playerAuthManager;
    }

    public ProxyServer getProxyServer() {
        return this.proxyServer;
    }

    public boolean isAutoAddRole() {
        return this.autoAddRole;
    }
}

final class CommandListener extends ListenerAdapter {
    private final DiscordBot bot;
    private final Logger logger;

    private final Map<String, String> hostMap;

    public CommandListener(DiscordBot bot, Logger logger) {
        this.bot = bot;
        this.logger = logger;
        this.hostMap = new HashMap<>();
        this.initializeHostMap();
    }

    public void initializeHostMap() {
        this.bot.getProxyServer().getConfiguration().getForcedHosts().forEach((hostname, serverNameList) -> {
            for (String serverName : serverNameList) {
                this.hostMap.put(serverName, hostname);
            }
        });
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getName().equals("say")) {
            String serverName = Objects.requireNonNull(event.getOption("server")).getAsString();
            String content = Objects.requireNonNull(event.getOption("content")).getAsString();
            Member member =  Objects.requireNonNull(event.getMember());
            String channelId = event.getChannelId();
            if (!this.bot.isSubscribed(channelId)) {
                event.reply("This channel is not subscribing to `" + serverName + "`! " +
                                "Use `/subscribe " + serverName + "` to subscribe.")
                        .setEphemeral(true)
                        .queue();
                return;
            }
            RegisteredServer server = this.bot.getProxyServer().getServer(serverName).orElse(null);
            if (server != null) {
                String replyMessage = this.bot.getCrossChatManager().discordToMinecraft(member, content, server);
                event.reply(replyMessage).queue();
            } else {
                event.reply("Server not found.").setEphemeral(true).queue();
            }
        } else if (event.getName().equals("servers")) {
            OptionMapping option = event.getOption("ephemeral");
            boolean ephemeral = option != null && option.getAsBoolean();
            List<String> messages = new ArrayList<>();
            for (RegisteredServer server : this.bot.getProxyServer().getAllServers()) {
                String serverName = server.getServerInfo().getName();
                String serverHostname = this.hostMap.get(serverName);
                String serverString = "`" + serverName + "`" + (serverHostname != null ? " (" + serverHostname + ")" : "");
                messages.add(serverString + ": **" + server.getPlayersConnected().size() + "** players online");
            }
            String message = String.join("\n", messages);
            event.reply(message).setEphemeral(ephemeral).queue();
        } else if (event.getName().equals("auth")) {
            PlayerAuthManager playerAuthManager = this.bot.getPlayerAuthManager();
            String otp = Objects.requireNonNull(event.getOption("code")).getAsString();
            Player player = playerAuthManager.authenticate(otp);
            if (player == null) {
                event.reply("Invalid code. Please retry and get a new code.")
                        .setEphemeral(true)
                        .queue();
            } else {
                event.reply("Welcome **" + player.getUsername() + "**! You can now connect to the servers.")
                        .setEphemeral(true)
                        .queue();
            }
        } else if (event.getName().equals("subscribe")) {
            String serverName = Objects.requireNonNull(event.getOption("server")).getAsString();
            if (this.bot.subscribe(event.getChannelId(), serverName)) {
                event.reply("This channel has subscribed for `" + serverName + "`!")
                        .queue();
            } else {
                event.reply("This channel has already subscribed for `" + serverName + "`.")
                        .setEphemeral(true)
                        .queue();
            }
        } else if (event.getName().equals("unsubscribe")) {
            String serverName = Objects.requireNonNull(event.getOption("server")).getAsString();
            if (this.bot.unsubscribe(event.getChannelId(), serverName)) {
                event.reply("This channel has unsubscribed from `" + serverName + "`.")
                        .queue();
            } else {
                event.reply("This channel has not subscribed for `" + serverName + "`.")
                        .setEphemeral(true)
                        .queue();
            }
        }
    }
}
