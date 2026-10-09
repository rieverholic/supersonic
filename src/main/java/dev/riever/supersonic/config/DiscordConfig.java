package dev.riever.supersonic.config;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Setting;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@ConfigSerializable
public class DiscordConfig {
    @ConfigSerializable
    public static class Subscription {
        @Setting(value = "role")
        private String roleId;

        @Setting(value = "servers")
        private String serverName;

        public Subscription() { }
        public Subscription(String serverName) {
            this.serverName = serverName;
        }
        public Subscription(String serverName, String roleId) {
            this.serverName = serverName;
            this.roleId = roleId;
        }

        public String getRoleId() { return this.roleId; }

        public String getServerName() { return this.serverName; }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Subscription entry) {
                return this.serverName.equals(entry.serverName);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return this.serverName.hashCode();
        }
    }

    @Setting(value = "entries")
    private Map<String, Set<Subscription>> subscriptions = new HashMap<>();

    public boolean subscribe(String channelId, String serverName) {
        return this.subscriptions.computeIfAbsent(channelId, _ -> new HashSet<>())
                .add(new Subscription(serverName));
    }

    public boolean unsubscribe(String channelId, String serverName) {
        Set<Subscription> subscriptions = this.subscriptions.get(channelId);
        if (subscriptions != null) {
            return subscriptions.remove(new Subscription(serverName));
        }
        return false;
    }

    public void removeChannel(String channelId) {
        this.subscriptions.remove(channelId);
    }

    public boolean isSubscribing(String channelId, String serverName) {
        return this.subscriptions.getOrDefault(channelId, Set.of()).contains(new Subscription(serverName));
    }

    public boolean contains(String channelId) {
        return this.subscriptions.containsKey(channelId);
    }

    public Set<String> getChannels() {
        return this.subscriptions.keySet();
    }
}
