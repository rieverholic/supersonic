package dev.riever.supersonic.config;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Comment;
import org.spongepowered.configurate.objectmapping.meta.Setting;

@ConfigSerializable
public class SupersonicConfig {
    @ConfigSerializable
    public static class Discord {
        @Setting(value = "bot-token")
        private String botToken = "[Required]";
        @Setting(value = "channel-id")
        private String channelId = "[Required]";
        @Setting(value = "role-id")
        private String roleId = "[Required]";

        public String getBotToken() { return this.botToken; }
        public String getChannelId() { return this.channelId; }
        public String getRoleId() { return this.roleId; }
    }

    @ConfigSerializable
    public static class Auth {
        @Comment("Type of storage to store auth requests. Currently only supports in-memory.")
        @Setting(value = "storage-type")
        private String storageType = "in-memory";
        @Comment("The amount of time (in minutes) between each scheduled cleanup of expired auth requests.")
        @Setting(value = "cleaner-period")
        private int cleanerPeriod = 5;
        @Comment("The maximum amount of time (in minutes) to keep auth requests in memory before they are automatically removed.")
        @Setting(value = "max-request-age")
        private int maxRequestAge = 5;

        public String getStorageType() { return this.storageType; }
        public int getCleanerPeriod() { return this.cleanerPeriod; }
        public int getMaxRequestAge() { return this.maxRequestAge; }
    }

    @Comment("Discord API settings.")
    @Setting(value = "discord")
    private Discord discord = new Discord();

    @Comment("Authentication settings.")
    @Setting(value = "storage")
    private Auth auth = new Auth();

    @Comment("Random seed for the random number generator. Leave empty to use the system time as seed.")
    @Setting(value = "seed")
    private String seed = "";

    public Auth getAuth() { return this.auth; }
    public Discord getDiscord() { return this.discord; }
    public String getSeed() { return this.seed; }
}
