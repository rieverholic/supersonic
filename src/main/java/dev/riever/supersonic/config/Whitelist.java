package dev.riever.supersonic.config;

import com.velocitypowered.api.proxy.Player;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Setting;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@ConfigSerializable
public class Whitelist {
    @ConfigSerializable
    public static class Entry {
        @Setting(value = "uuid")
        private UUID uuid;
        @Setting(value = "username")
        private String username;

        public Entry() { }
        public Entry(UUID uuid, String username) {
            this.uuid = uuid;
            this.username = username;
        }
        public Entry(Player player) {
            this.uuid = player.getUniqueId();
            this.username = player.getUsername();
        }

        public UUID getUUID() { return this.uuid; }
        public String getUsername() { return this.username; }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Entry entry) {
                return this.uuid.equals(entry.uuid);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return this.uuid.hashCode();
        }
    }

    @Setting(value = "entries")
    private Set<Entry> entries = new HashSet<>();

    public Set<Entry> getEntries() { return this.entries; }

    public boolean addPlayer(Player player) {
        return this.entries.add(new Entry(player));
    }

    public void removePlayer(Player player) {
        this.entries.remove(new Entry(player));
    }

    public boolean contains(Player player) {
        return this.entries.contains(new Entry(player));
    }
}
