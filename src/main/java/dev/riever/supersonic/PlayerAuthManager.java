package dev.riever.supersonic;

import com.velocitypowered.api.proxy.Player;
import dev.riever.supersonic.config.WhitelistManager;
import dev.riever.supersonic.storage.InMemoryPlayerAuthStorage;
import dev.riever.supersonic.storage.PlayerAuthStorage;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Random;

public class PlayerAuthManager {
    private final WhitelistManager whitelistManager;
    private final PlayerAuthStorage playerAuthStorage;
    private final int maxRequestAge;
    private final Random random;
    private final Logger logger;

    public PlayerAuthManager(
            Path whitelistFile,
            String storageType,
            int maxRequestAge,
            int cleanerPeriod,
            Random random,
            Logger logger
    ) {
        this.whitelistManager = new WhitelistManager(whitelistFile);
        this.playerAuthStorage = new InMemoryPlayerAuthStorage(logger, cleanerPeriod);
        this.maxRequestAge = maxRequestAge;
        this.random = random;
        this.logger = logger;
    }

    public void initialize() {
        this.whitelistManager.load();
        this.playerAuthStorage.initialize();
        this.logger.info("Initialized player auth storage");
    }

    private String generateOtp() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            builder.append(this.random.nextInt(10));
        }
        return builder.toString();
    }

    public String request(Player player) {
        while (true) {
            String otp = this.generateOtp();
            if (this.playerAuthStorage.savePlayerAuth(player, otp, Instant.now().plusSeconds(60L * this.maxRequestAge))) {
                return otp;
            }
        }
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
