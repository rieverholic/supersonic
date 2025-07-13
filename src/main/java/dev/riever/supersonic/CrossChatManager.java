package dev.riever.supersonic;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import dev.riever.supersonic.utils.Trie;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CrossChatManager {
    private final ProxyServer proxyServer;
    private final DiscordBot discordBot;
    private final DiscordMentionProcessor discordMentionProcessor;
    private final Logger logger;

    private final String mentionRegex = "<@&(?<role>\\d+)>|<@(?<user>\\d+)>|<#(?<channel>\\d+)>";
    private final Pattern mentionPattern = Pattern.compile(mentionRegex);

    public CrossChatManager(ProxyServer proxyServer, DiscordBot discordBot, Logger logger) {
        this.proxyServer = proxyServer;
        this.discordBot = discordBot;
        this.discordMentionProcessor = new DiscordMentionProcessor(logger);
        this.logger = logger;
    }

    public String discordToMinecraft(Member sender, String message, RegisteredServer destServer) {
        Guild guild = this.discordBot.getGuild();
        TextComponent.Builder result = Component.text()
                .append(Component.text("[Discord]", NamedTextColor.GOLD))
                .appendSpace()
                .append(Component.text(this.discordBot.getDisplayName(sender), NamedTextColor.GREEN))
                .append(Component.text(": "));
        Matcher matcher = this.mentionPattern.matcher(message);
        int lastMentionIndex = 0;
        while (matcher.find()) {
            result.append(Component.text(message.substring(lastMentionIndex, matcher.start())));
            String roleId = matcher.group("role");
            String userId = matcher.group("user");
            String channelId = matcher.group("channel");
            if (roleId != null) {
                Role role = guild.getRoleById(roleId);
                if (role != null) {
                    result.append(Component.text("@" + role.getName(), NamedTextColor.AQUA));
                } else {
                    result.append(Component.text("<@&" + roleId + ">"));
                }
            } else if (userId != null) {
                Member member = guild.retrieveMemberById(userId).complete();
                if (member != null) {
                    result.append(Component.text("@" + this.discordBot.getDisplayName(member), NamedTextColor.DARK_AQUA));
                } else {
                    result.append(Component.text("<@" + userId + ">"));
                }
            } else if (channelId != null) {
                Channel channel = guild.getJDA().getChannelById(Channel.class, channelId);
                if (channel != null) {
                    result.append(Component.text("#" + channel.getName(), NamedTextColor.GRAY));
                } else {
                    result.append(Component.text("<#" + channelId + ">"));
                }
            }
            lastMentionIndex = matcher.end();
        }
        result.append(Component.text(message.substring(lastMentionIndex)));
        destServer.sendMessage(result.build());
        String serverName = destServer.getServerInfo().getName();
        return "Message sent to `" + serverName + "`: " + message;
    }

    public TextComponent minecraftToDiscord(Player sender, String message) {
        DiscordMentionProcessor.MessagePair messagePair = this.discordMentionProcessor.process(message);
        TextComponent minecraftMessage = messagePair.minecraftMessage();
        String discordMessage = messagePair.discordMessage();
        String username = sender.getUsername();
        RegisteredServer server = sender.getCurrentServer()
                .map(ServerConnection::getServer)
                .orElse(null);
        String sourceForDiscord = "**" + username + "**" + (server != null ? " from `" + server.getServerInfo().getName() + "`" : "");
        this.discordBot.sendMessage(sourceForDiscord + ": " + discordMessage);
        return Component.text()
                .append(Component.text(username, NamedTextColor.GREEN))
                .append(Component.text(" to "))
                .append(Component.text("Discord", NamedTextColor.GOLD))
                .append(Component.text(": "))
                .append(minecraftMessage)
                .build();
    }

    public void addDiscordMembers(List<Member> memberList) {
        for (Member member : memberList) {
            this.addDiscordMember(member);
        }
    }

    public void addDiscordMember(Member member) {
        this.discordMentionProcessor.addMember(member);
    }

    public void removeDiscordMember(Member member) {
        this.discordMentionProcessor.removeMember(member);
    }
}

final class DiscordMentionProcessor {
    record MessagePair(TextComponent minecraftMessage, String discordMessage) {}
    record Mention(Member member, boolean isNickname) {}
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
        TextComponent.Builder minecraftBuilder = Component.text();
        int lastIndex = 0;
        while (true) {
            int mentionIndex = message.indexOf("@", lastIndex);
            if (mentionIndex == -1) {
                discordBuilder.append(message.substring(lastIndex));
                minecraftBuilder.append(Component.text(message.substring(lastIndex)));
                break;
            }
            discordBuilder.append(message, lastIndex, mentionIndex);
            minecraftBuilder.append(Component.text(message.substring(lastIndex, mentionIndex)));
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
                String mentionedName = (Objects.requireNonNull(mention.isNickname() ? member.getNickname() : member.getEffectiveName()));
                minecraftBuilder.append(Component.text("@" + mentionedName, NamedTextColor.AQUA));
                int mentionLength = mentionedName.length();
                endIndex = mentionIndex + mentionLength + 1;
            } else {
                discordBuilder.append(message, mentionIndex, endIndex);
                minecraftBuilder.append(Component.text(message.substring(mentionIndex, endIndex)));
            }
            lastIndex = endIndex;
            if (endIndex == message.length()) {
                break;
            }
        }
        return new MessagePair(minecraftBuilder.build(), discordBuilder.toString());
    }
}
