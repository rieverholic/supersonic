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
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;
import dev.riever.velocity.utils.Trie;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static net.dv8tion.jda.api.interactions.commands.OptionType.STRING;

@Plugin(id = "supersonic", name = "Supersonic", version = "0.2.0-SNAPSHOT",
        url = "https://riever.dev", description = "A velocity plugin for Joon's Dreamyard", authors = {"Riever"})
public class Supersonic {

    private final ProxyServer server;
    private final Logger logger;

    private final JDA jda;
    private final TextChannel channel;
    private final DiscordMentionProcessor processor;

    @Inject
    public Supersonic(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;
        this.jda = JDABuilder.createLight(System.getenv("DISCORD_JOONBOT_TOKEN"))
                .enableIntents(GatewayIntent.GUILD_MEMBERS)
                .addEventListeners(new SlashCommandListener(server, logger))
                .build();
        try {
            this.jda.awaitReady();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        this.channel = this.jda.getTextChannelById(System.getenv("DISCORD_JOONBOT_CHANNEL_ID"));
        if (this.channel == null) {
            throw new RuntimeException("Could not find channel with ID " + System.getenv("DISCORD_JOONBOT_CHANNEL_ID"));
        }
        Role role = this.jda.getRoleById(System.getenv("DISCORD_CRAFTORIA_ROLE_ID"));
        List<Member> memberList = this.channel.getGuild().findMembersWithRoles(role).get();
        this.processor = new DiscordMentionProcessor(memberList, this.logger);
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        CommandListUpdateAction commands = this.jda.updateCommands();
        commands.addCommands(
                Commands.slash("say", "Send a system message to a Minecraft server")
                        .addOption(STRING, "server", "Name of the server to send a message to", true)
                        .addOption(STRING, "content", "Message to send", true)
        );
        commands.queue();

        CommandManager commandManager = this.server.getCommandManager();
        CommandMeta commandMeta = commandManager.metaBuilder("dsay")
                .plugin(this)
                .build();

        BrigadierCommand discordCommand = DiscordChatCommand.create(this.server, this.channel, this.processor);
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
    private final ProxyServer server;
    private final Logger logger;

    public SlashCommandListener(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;
    }

    private String discordToMinecraft(String message, Guild guild) {
        StringBuilder result = new StringBuilder();
        int lastIndex = 0;
        boolean isRole = false;
        while (true) {
            int mentionIndex = message.indexOf("<@", lastIndex);
            if (mentionIndex == -1) {
                result.append(message.substring(lastIndex));
                break;
            }
            result.append(message, lastIndex, mentionIndex);
            int endIndex = message.indexOf(">", mentionIndex);
            if (endIndex == -1) {
                result.append(message.substring(mentionIndex));
                break;
            }
            if (message.charAt(mentionIndex + 2) == '&') {
                String roleId = message.substring(mentionIndex + 3, endIndex);
                Role role = guild.getRoleById(roleId);
                if (role != null) {
                    String roleName = role.getName();
                    result.append("§d@").append(roleName).append("§f");
                } else {
                    result.append("<@&").append(roleId).append(">");
                }
            } else {
                String userId = message.substring(mentionIndex + 2, endIndex);
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
            }
            lastIndex = endIndex + 1;
        }
        return result.toString();
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getName().equals("say")) {
            String serverName = event.getOption("server", OptionMapping::getAsString);
            String content = event.getOption("content", OptionMapping::getAsString);
            String username;
            if (event.getMember() != null && event.getMember().getNickname() != null) {
                // Use the guild-specific nickname if available
                username = event.getMember().getNickname();
            } else {
                // Fall back to the global display name, or username if the global name is not set
                username = event.getUser().getEffectiveName();
            }

            RegisteredServer server = this.server.getServer(serverName).orElse(null);
            if (server != null) {
                if (content != null) {
                    String message = this.discordToMinecraft(content, event.getGuild());
                    server.sendMessage(Component.text("§6[Discord] §a" + username + "§f: " + message));
                    event.reply("Message sent to `" + serverName + "`: " + content).queue();
                }
            } else {
                event.reply("Server not found.").setEphemeral(true).queue();
            }
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

    public DiscordMentionProcessor(List<Member> memberList, Logger logger) {
        this.memberTrie = new Trie<>();
        this.logger = logger;
        for (Member member : memberList) {
            String nickname = member.getNickname();
            if (nickname != null) {
                this.memberTrie.insert(member.getNickname(), new Mention(member, true));
            }
            this.memberTrie.insert(member.getEffectiveName(), new Mention(member, false));
        }
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
