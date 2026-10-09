package dev.riever.supersonic.config;

import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.nio.file.Path;
import java.util.Set;

public class DiscordChannelManager {
    private final YamlConfigurationLoader loader;
    private CommentedConfigurationNode root;
    private DiscordConfig config;

    public DiscordChannelManager(Path configFile) {
        this.loader = YamlConfigurationLoader.builder()
                .path(configFile)
                .nodeStyle(NodeStyle.BLOCK)
                .build();
    }

    public void load() {
        try {
            this.root = this.loader.load();
            this.config = this.root.get(DiscordConfig.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void save() {
        try {
            this.root.set(DiscordConfig.class, this.config);
            this.loader.save(this.root);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public boolean subscribe(String channelId, String serverName) {
        if (this.config.subscribe(channelId, serverName)) {
            this.save();
            return true;
        }
        return false;
    }

    public boolean unsubscribe(String channelId, String serverName) {
        if (this.config.unsubscribe(channelId, serverName)) {
            this.save();
            return true;
        }
        return false;
    }

    public boolean isSubscribing(String channelId, String serverName) {
        return this.config.isSubscribing(channelId, serverName);
    }

    public boolean contains(String channelId) {
        return this.config.contains(channelId);
    }

    public Set<String> getChannels() {
        return this.config.getChannels();
    }
}
