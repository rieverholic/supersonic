package dev.riever.supersonic;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleRemoveEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import java.util.*;

import static net.dv8tion.jda.api.interactions.commands.OptionType.BOOLEAN;
import static net.dv8tion.jda.api.interactions.commands.OptionType.STRING;

public class DiscordBot {
    private final JDA jda;
    private final String channelId;
    private final String roleId;

    private final ProxyServer proxyServer;
    private final CrossChatManager crossChatManager;
    private final PlayerAuthManager playerAuthManager;
    private final Logger logger;

    public DiscordBot(
            String token,
            String channelId,
            String roleId,
            ProxyServer proxyServer,
            PlayerAuthManager playerAuthManager,
            Logger logger
    ) {
        this.jda = JDABuilder.createLight(token)
                .enableIntents(GatewayIntent.GUILD_MEMBERS)
                .build();
        this.channelId = channelId;
        this.roleId = roleId;
        this.logger = logger;
        this.proxyServer = proxyServer;
        this.crossChatManager = new CrossChatManager(proxyServer, this, logger);
        this.playerAuthManager = playerAuthManager;
    }

    public void initialize() {
        try {
            this.jda.awaitReady();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        this.jda.addEventListener(
                new ChatCommandListener(this, this.proxyServer, this.crossChatManager, this.logger),
                new MemberRoleListener(this.crossChatManager, this.roleId, this.logger)
        );
        List<Member> memberList = this.getGuild().findMembersWithRoles(this.getRole()).get();
        this.crossChatManager.addDiscordMembers(memberList);

        // Initialize commands
        this.jda.updateCommands().addCommands(
                Commands.slash("servers", "Display all current active servers")
                        .addOption(BOOLEAN, "ephemeral", "Message only shown to you")
        ).queue();
        this.getGuild().updateCommands().addCommands(
                Commands.slash("say", "Send a system message to a Minecraft server")
                        .addOption(STRING, "server", "Name of the server to send a message to", true)
                        .addOption(STRING, "content", "Message to send", true),
                Commands.slash("auth", "Authenticates a Minecraft account")
                        .addOption(STRING, "code", "The one-time passcode displayed on screen", true)
        ).queue();
    }

    public String getDisplayName(@Nonnull Member member) {
        String displayName = member.getNickname();
        if (displayName == null) {
            displayName = member.getEffectiveName();
        }
        return displayName;
    }

    public void sendMessage(String message) {
        this.getChannel().sendMessage(message).queue();
    }

    public TextChannel getChannel() {
        return this.jda.getTextChannelById(this.channelId);
    }

    public String getChannelId() {
        return this.channelId;
    }

    public String getRoleId() {
        return this.roleId;
    }

    public Role getRole() {
        return this.jda.getRoleById(this.roleId);
    }

    public Guild getGuild() {
        return this.getChannel().getGuild();
    }

    public CrossChatManager getCrossChatManager() {
        return this.crossChatManager;
    }

    public PlayerAuthManager getPlayerAuthManager() {
        return this.playerAuthManager;
    }
}

final class ChatCommandListener extends ListenerAdapter {
    private final ProxyServer proxyServer;
    private final DiscordBot discordBot;
    private final Logger logger;

    private final Map<String, String> hostMap;

    private final CrossChatManager crossChatManager;

    public ChatCommandListener(DiscordBot discordBot, ProxyServer proxyServer, CrossChatManager crossChatManager, Logger logger) {
        this.proxyServer = proxyServer;
        this.discordBot = discordBot;
        this.logger = logger;
        this.crossChatManager = crossChatManager;
        this.hostMap = new HashMap<>();
        this.initializeHostMap();
    }

    public void initializeHostMap() {
        this.proxyServer.getConfiguration().getForcedHosts().forEach((hostname, serverNameList) -> {
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
            String roleId = this.discordBot.getRoleId();
            String channelId = this.discordBot.getChannelId();
            boolean hasRole = member.getRoles().stream().anyMatch(role -> role.getId().equals(roleId));
            if (!hasRole) {
                event.reply("You need the role <@&" + roleId + "> to use this command. Ask your admin!")
                        .setEphemeral(true)
                        .queue();
                return;
            }
            if (!Objects.requireNonNullElse(event.getChannelId(), "").equals(channelId)) {
                event.reply("This command can only be used in <#" + channelId + ">.")
                        .setEphemeral(true)
                        .queue();
                return;
            }
            RegisteredServer server = this.proxyServer.getServer(serverName).orElse(null);
            if (server != null) {
                String replyMessage = this.crossChatManager.discordToMinecraft(member, content, server);
                event.reply(replyMessage).queue();
            } else {
                event.reply("Server not found.").setEphemeral(true).queue();
            }
        } else if (event.getName().equals("servers")) {
            OptionMapping option = event.getOption("ephemeral");
            boolean ephemeral = option != null && option.getAsBoolean();
            List<String> messages = new ArrayList<>();
            for (RegisteredServer server : this.proxyServer.getAllServers()) {
                String serverName = server.getServerInfo().getName();
                String serverHostname = this.hostMap.get(serverName);
                String serverString = "`" + serverName + "`" + (serverHostname != null ? " (" + serverHostname + ")" : "");
                messages.add(serverString + ": **" + server.getPlayersConnected().size() + "** players online");
            }
            String message = String.join("\n", messages);
            event.reply(message).setEphemeral(ephemeral).queue();
        } else if (event.getName().equals("auth")) {
            PlayerAuthManager playerAuthManager = this.discordBot.getPlayerAuthManager();
            String otp = Objects.requireNonNull(event.getOption("code")).getAsString();
            Player player = playerAuthManager.authenticate(otp);
            if (player == null) {
                event.reply("Invalid code. Please retry and get a new code.").setEphemeral(true).queue();
            } else {
                event.reply("Welcome **" + player.getUsername() + "**! You can now connect to the servers.").setEphemeral(true).queue();
            }
        }
    }
}

final class MemberRoleListener extends ListenerAdapter {
    private final CrossChatManager manager;
    private final String roleId;
    private final Logger logger;

    public MemberRoleListener(CrossChatManager manager, String roleId, Logger logger) {
        this.manager = manager;
        this.roleId = roleId;
        this.logger = logger;
    }

    @Override
    public void onGuildMemberRoleAdd(GuildMemberRoleAddEvent event) {
        Member member = event.getMember();
        if (event.getRoles().stream().anyMatch(role -> role.getId().equals(this.roleId))) {
            this.manager.addDiscordMember(member);
        }
    }

    @Override
    public void onGuildMemberRoleRemove(GuildMemberRoleRemoveEvent event) {
        Member member = event.getMember();
        if (event.getRoles().stream().anyMatch(role -> role.getId().equals(this.roleId))) {
            this.manager.removeDiscordMember(member);
        }
    }
}
