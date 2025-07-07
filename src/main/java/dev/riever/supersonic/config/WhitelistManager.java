package dev.riever.supersonic.config;

import com.velocitypowered.api.proxy.Player;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.nio.file.Path;

public class WhitelistManager {
    private final YamlConfigurationLoader loader;
    private CommentedConfigurationNode root;
    private Whitelist whitelist;

    public WhitelistManager(Path whitelistFile) {
        this.loader = YamlConfigurationLoader.builder()
                .path(whitelistFile)
                .nodeStyle(NodeStyle.BLOCK)
                .build();
    }

    public void load() {
        try {
            this.root = this.loader.load();
            this.whitelist = this.root.get(Whitelist.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void save() {
        try {
            this.root.set(Whitelist.class, this.whitelist);
            this.loader.save(this.root);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void addPlayer(Player player) {
        if (this.whitelist.addPlayer(player)) {
            this.save();
        }
    }

    public void removePlayer(Player player) {
        this.whitelist.removePlayer(player);
    }

    public boolean check(Player player) {
        return this.whitelist.contains(player);
    }
}
