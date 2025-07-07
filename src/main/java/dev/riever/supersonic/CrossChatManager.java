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
        StringBuilder result = new StringBuilder();
        Matcher matcher = this.mentionPattern.matcher(message);
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
                    result.append("§b@").append(this.discordBot.getDisplayName(member)).append("§f");
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
        String messageToSend = result.toString();
        destServer.sendMessage(
                Component.text("§6[Discord] §a" + this.discordBot.getDisplayName(sender) + "§f: " + messageToSend)
        );
        String serverName = destServer.getServerInfo().getName();
        return "Message sent to `" + serverName + "`: " + message;
    }

    public String minecraftToDiscord(Player sender, String message) {
        DiscordMentionProcessor.MessagePair messagePair = this.discordMentionProcessor.process(message);
        String minecraftMessage = messagePair.minecraftMessage();
        String discordMessage = messagePair.discordMessage();
        String username = sender.getUsername();
        RegisteredServer server = sender.getCurrentServer()
                .map(ServerConnection::getServer)
                .orElse(null);
        String sourceForDiscord = "**" + username + "**" + (server != null ? " from `" + server.getServerInfo().getName() + "`" : "");
        this.discordBot.sendMessage(sourceForDiscord + ": " + discordMessage);
        return "§a" + username + "§f to §6Discord§f: " + minecraftMessage;
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
    record MessagePair(String minecraftMessage, String discordMessage) {}
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
