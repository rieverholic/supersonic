package dev.riever.supersonic.config;

import org.slf4j.Logger;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public class SupersonicConfigManager {
    private final YamlConfigurationLoader loader;
    private final Path configFile;
    private final Logger logger;
    private CommentedConfigurationNode root;
    private SupersonicConfig config;

    public SupersonicConfigManager(Path configFile, Logger logger) {
        this.configFile = configFile;
        this.loader = YamlConfigurationLoader.builder()
                .path(configFile)
                .nodeStyle(NodeStyle.BLOCK)
                .build();
        this.logger = logger;
    }

    public SupersonicConfig initialize() {
        try {
            if (!Files.exists(this.configFile)) {
                this.root = this.loader.createNode();
                this.root.set(SupersonicConfig.class, new SupersonicConfig());
                this.loader.save(this.root);
                throw new RuntimeException("Open the config file and fill in the required fields. (See the config.yml file for more information.)");
            }
            return this.load();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public SupersonicConfig load() {
        try {
            this.root = this.loader.load();
            this.config = this.root.get(SupersonicConfig.class);
            return this.config;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public SupersonicConfig getConfig() {
        return this.config;
    }
}
