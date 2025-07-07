package dev.riever.supersonic;

import com.velocitypowered.api.proxy.Player;
import dev.riever.supersonic.config.WhitelistManager;
import dev.riever.supersonic.storage.InMemoryPlayerAuthStorage;
import dev.riever.supersonic.storage.PlayerAuthStorage;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Random;

public class PlayerAuthManager {
    private final WhitelistManager whitelistManager;
    private final PlayerAuthStorage playerAuthStorage;
    private final Random random;

    public PlayerAuthManager(Path whitelistFile, Random random) {
        this.whitelistManager = new WhitelistManager(whitelistFile);
        this.playerAuthStorage = new InMemoryPlayerAuthStorage();
        this.random = random;
    }

    public void initialize() {
        this.whitelistManager.load();
    }

    private String generateOtp() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            builder.append(this.random.nextInt(10));
        }
        return builder.toString();
    }

    public String request(Player player) {
        String otp = this.generateOtp();
        this.playerAuthStorage.savePlayerAuth(player, otp, Instant.now().plusSeconds(60 * 5));
        return otp;
    }

    public Player authenticate(String otp) {
        Player player = this.playerAuthStorage.authenticate(otp);
        if (player != null) {
            this.whitelistManager.addPlayer(player);
        }
        return player;
    }

    public boolean isAuthenticated(Player player) {
        return this.whitelistManager.check(player);
    }
}
