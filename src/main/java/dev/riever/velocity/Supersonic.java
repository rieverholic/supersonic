package dev.riever.velocity;

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
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleRemoveEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;
import dev.riever.velocity.utils.Trie;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.dv8tion.jda.api.interactions.commands.OptionType.STRING;
import static net.dv8tion.jda.api.interactions.commands.OptionType.BOOLEAN;

@Plugin(id = "supersonic", name = "Supersonic", version = "0.2.0-SNAPSHOT",
        url = "https://riever.dev", description = "A velocity plugin for Joon's Dreamyard", authors = {"Riever"})
public class Supersonic {

    private final ProxyServer proxyServer;
    private final Logger logger;

    private final JDA jda;
    private final TextChannel channel;
    private final DiscordMentionProcessor processor;
    private final Role role;

    private static final String DISCORD_CHANNEL_KEY = "DISCORD_JOONBOT_CHANNEL_ID";
    private static final String DISCORD_BOT_TOKEN_KEY = "DISCORD_JOONBOT_TOKEN";
    private static final String DISCORD_ROLE_KEY = "DISCORD_CRAFTORIA_ROLE_ID";

    @Inject
    public Supersonic(ProxyServer proxyServer, Logger logger) {
        this.proxyServer = proxyServer;
        this.logger = logger;
        this.processor = new DiscordMentionProcessor(this.logger);
        String discordChannelId = System.getenv(DISCORD_CHANNEL_KEY);
        String discordBotToken = System.getenv(DISCORD_BOT_TOKEN_KEY);
        String discordRoleId = System.getenv(DISCORD_ROLE_KEY);

        this.jda = JDABuilder.createLight(discordBotToken)
                .enableIntents(GatewayIntent.GUILD_MEMBERS)
                .addEventListeners(
                        new SlashCommandListener(proxyServer, discordRoleId, discordChannelId, logger),
                        new MemberRoleListener(processor, discordRoleId, logger)
                )
                .build();
        try {
            this.jda.awaitReady();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        this.channel = this.jda.getTextChannelById(discordChannelId);
        if (this.channel == null) {
            throw new RuntimeException("Could not find channel with ID " + discordChannelId);
        }
        this.role = this.jda.getRoleById(discordRoleId);
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        // Initialize Discord mention processor
        List<Member> memberList = this.channel.getGuild().findMembersWithRoles(this.role).get();
        this.processor.addMembers(memberList);

        // Initialize Discord command
        this.jda.updateCommands().addCommands(
                Commands.slash("servers", "Display all current active servers")
                        .addOption(BOOLEAN, "ephemeral", "Message only shown to you")
        ).queue();
        this.channel.getGuild().updateCommands().addCommands(
                Commands.slash("say", "Send a system message to a Minecraft server")
                        .addOption(STRING, "server", "Name of the server to send a message to", true)
                        .addOption(STRING, "content", "Message to send", true)
        ).queue();

        // Initialize Minecraft command
        CommandManager commandManager = this.proxyServer.getCommandManager();
        CommandMeta commandMeta = commandManager.metaBuilder("dsay")
                .plugin(this)
                .build();

        BrigadierCommand discordCommand = DiscordChatCommand.create(this.proxyServer, this.channel, this.processor);
        commandManager.register(commandMeta, discordCommand);
    }

    @Subscribe
    public void onJoin(ServerPostConnectEvent event) {
        Player player = event.getPlayer();
        String username = player.getUsername();
        Optional<ServerConnection> conn = player.getCurrentServer();
        if (conn.isPresent()) {
            ServerInfo serverInfo = conn.get().getServerInfo();
            this.channel.sendMessage("**" + username + "** joined `" + serverInfo.getName() + "`.").queue();
        }
    }

    @Subscribe
    public void onLeave(DisconnectEvent event) {
        Player player = event.getPlayer();
        String username = player.getUsername();
        Optional<ServerConnection> conn = player.getCurrentServer();
        if (conn.isPresent()) {
            ServerInfo serverInfo = conn.get().getServerInfo();
            this.channel.sendMessage("**" + username + "** left `" + serverInfo.getName() + "`.").queue();
        }
    }
}

class SlashCommandListener extends ListenerAdapter {
    private final ProxyServer proxyServer;
    private final String discordRoleId;
    private final String discordChannelId;
    private final Logger logger;

    private final String mentionRegex = "<@&(?<role>\\d+)>|<@(?<user>\\d+)>|<#(?<channel>\\d+)>";
    private final Pattern mentionPattern = Pattern.compile(mentionRegex);

    private final Map<String, String> hostMap;

    public SlashCommandListener(ProxyServer proxyServer, String discordRoleId, String discordChannelId, Logger logger) {
        this.proxyServer = proxyServer;
        this.discordRoleId = discordRoleId;
        this.discordChannelId = discordChannelId;
        this.logger = logger;
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

    private String discordToMinecraft(String message, Guild guild) {
        StringBuilder result = new StringBuilder();
        Matcher matcher = mentionPattern.matcher(message);
        int lastMentionIndex = 0;
        while (matcher.find()) {
            result.append(message, lastMentionIndex, matcher.start());
            String roleId = matcher.group("role");
            String userId = matcher.group("user");
            String channelId = matcher.group("channel");
            if (roleId != null) {
                Role role = guild.getRoleById(roleId);
                if (role != null) {
                    result.append("§b@").append(role.getName()).append("§f");
                } else {
                    result.append("<@&").append(roleId).append(">");
                }
            } else if (userId != null) {
                Member member = guild.retrieveMemberById(userId).complete();
                if (member != null) {
                    String username = member.getNickname();
                    if (username == null) {
                        username = member.getEffectiveName();
                    }
                    result.append("§b@").append(username).append("§f");
                } else {
                    result.append("<@").append(userId).append(">");
                }
            } else if (channelId != null) {
                Channel channel = guild.getJDA().getChannelById(Channel.class, channelId);
                if (channel != null) {
                    result.append("§7#").append(channel.getName()).append("§f");
                } else {
                    result.append("<#").append(channelId).append(">");
                }
            }
            lastMentionIndex = matcher.end();
        }
        result.append(message, lastMentionIndex, message.length());
        return result.toString();
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getName().equals("say")) {
            String serverName = Objects.requireNonNull(event.getOption("server")).getAsString();
            String content = Objects.requireNonNull(event.getOption("content")).getAsString();
            Member member =  Objects.requireNonNull(event.getMember());
            boolean hasRole = member.getRoles().stream().anyMatch(role -> role.getId().equals(this.discordRoleId));
            if (!hasRole) {
                event.reply("You need the role <@&" + this.discordRoleId + "> to use this command. Ask your admin!")
                        .setEphemeral(true)
                        .queue();
                return;
            }
            if (!Objects.requireNonNullElse(event.getChannelId(), "").equals(this.discordChannelId)) {
                event.reply("This command can only be used in <#" + this.discordChannelId + ">.")
                        .setEphemeral(true)
                        .queue();
                return;
            }

            String username;
            if (member.getNickname() != null) {
                // Use the guild-specific nickname if available
                username = member.getNickname();
            } else {
                // Fall back to the global display name or username if the global name is not set
                username = member.getEffectiveName();
            }

            RegisteredServer server = this.proxyServer.getServer(serverName).orElse(null);
            if (server != null) {
                String message = this.discordToMinecraft(content, event.getGuild());
                server.sendMessage(Component.text("§6[Discord] §a" + username + "§f: " + message));
                event.reply("Message sent to `" + serverName + "`: " + content).queue();
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
        }
    }
}

class MemberRoleListener extends ListenerAdapter {
    private final DiscordMentionProcessor processor;
    private final String discordRoleId;
    private final Logger logger;

    public MemberRoleListener(DiscordMentionProcessor processor, String discordRoleId, Logger logger) {
        this.processor = processor;
        this.discordRoleId = discordRoleId;
        this.logger = logger;
    }

    @Override
    public void onGuildMemberRoleAdd(GuildMemberRoleAddEvent event) {
        Member member = event.getMember();
        if (event.getRoles().stream().anyMatch(role -> role.getId().equals(this.discordRoleId))) {
            this.processor.addMember(member);
        }
    }

    @Override
    public void onGuildMemberRoleRemove(GuildMemberRoleRemoveEvent event) {
        Member member = event.getMember();
        if (event.getRoles().stream().anyMatch(role -> role.getId().equals(this.discordRoleId))) {
            this.processor.removeMember(member);
        }
    }
}

final class DiscordChatCommand {
    public static BrigadierCommand create(final ProxyServer proxy, final TextChannel discordChannel, final DiscordMentionProcessor processor) {
        LiteralCommandNode<CommandSource> discordNode = BrigadierCommand.literalArgumentBuilder("dsay")
                .then(BrigadierCommand.requiredArgumentBuilder("message", StringArgumentType.greedyString())
                        .suggests((ctx, builder) -> {
                            builder.suggest("hello");
                            return builder.buildFuture();
                        })
                        .executes(context -> {
                            String content = context.getArgument("message", String.class);
                            DiscordMentionProcessor.MessagePair messagePair = processor.process(content);
                            String minecraftMessage = messagePair.minecraftMessage();
                            String discordMessage = messagePair.discordMessage();
                            CommandSource source = context.getSource();
                            if (source instanceof Player player) {
                                String username = player.getUsername();
                                RegisteredServer server = player.getCurrentServer()
                                        .map(ServerConnection::getServer)
                                        .orElse(null);
                                Component text = Component.text("§a" + username + "§f to §6Discord§f: " + minecraftMessage);
                                Objects.requireNonNullElse(server, player).sendMessage(text);
                                String sourceForDiscord = "**" + username + "**" + (server != null ? " from `" + server.getServerInfo().getName() + "`" : "");
                                discordChannel.sendMessage(sourceForDiscord + ": " + discordMessage).queue();
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

final class DiscordMentionProcessor {
    record Mention(Member member, boolean isNickname) {}
    record MessagePair(String minecraftMessage, String discordMessage) {}
    private final Trie<Mention> memberTrie;
    private final Logger logger;

    public DiscordMentionProcessor(Logger logger) {
        this.memberTrie = new Trie<>();
        this.logger = logger;
    }

    public void addMembers(List<Member> memberList) {
        for (Member member : memberList) {
            this.addMember(member);
        }
    }

    public void addMember(Member member) {
        String nickname = member.getNickname();
        if (nickname != null) {
            this.memberTrie.insert(member.getNickname(), new Mention(member, true));
        }
        this.memberTrie.insert(member.getEffectiveName(), new Mention(member, false));
    }

    public void removeMember(Member member) {
        String nickname = member.getNickname();
        if (nickname != null) {
            this.memberTrie.remove(member.getNickname());
        }
        this.memberTrie.remove(member.getEffectiveName());
    }

    public Mention findMention(String message) {
        Mention mention = this.memberTrie.search(message, false);
        if (mention == null) {
            mention = this.memberTrie.search(message, true);
        }
        return mention;
    }

    public MessagePair process(String message) {
        StringBuilder discordBuilder = new StringBuilder();
        StringBuilder minecraftBuilder = new StringBuilder();
        int lastIndex = 0;
        while (true) {
            int mentionIndex = message.indexOf("@", lastIndex);
            if (mentionIndex == -1) {
                discordBuilder.append(message.substring(lastIndex));
                minecraftBuilder.append(message.substring(lastIndex));
                break;
            }
            discordBuilder.append(message, lastIndex, mentionIndex);
            minecraftBuilder.append(message, lastIndex, mentionIndex);
            int endIndex = mentionIndex + 1;
            while (endIndex < message.length()) {
                if (Character.isWhitespace(message.charAt(endIndex))) {
                    break;
                }
                endIndex++;
            }
            String mentionMessage = message.substring(mentionIndex + 1, endIndex);
            Mention mention = this.findMention(mentionMessage);
            if (mention != null) {
                Member member = mention.member;
                discordBuilder.append("<@");
                discordBuilder.append(member.getId());
                discordBuilder.append(">");
                String mentionedName = (Objects.requireNonNull(mention.isNickname ? member.getNickname() : member.getEffectiveName()));
                minecraftBuilder.append("§b@").append(mentionedName).append("§f");
                int mentionLength = mentionedName.length();
                endIndex = mentionIndex + mentionLength + 1;
            } else {
                discordBuilder.append(message, mentionIndex, endIndex);
                minecraftBuilder.append(message, mentionIndex, endIndex);
            }
            lastIndex = endIndex;
            if (endIndex == message.length()) {
                break;
            }
        }
        return new MessagePair(minecraftBuilder.toString(), discordBuilder.toString());
    }
}
