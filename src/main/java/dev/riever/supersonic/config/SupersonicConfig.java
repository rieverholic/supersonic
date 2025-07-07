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

    @Comment("Discord API setting.")
    @Setting(value = "discord")
    private Discord discord = new Discord();

    @Comment("Random seed for the random number generator. Leave empty to use the system time as seed.")
    @Setting(value = "seed")
    private String seed = "";

    public Discord getDiscord() { return this.discord; }
    public String getSeed() { return this.seed; }
}
