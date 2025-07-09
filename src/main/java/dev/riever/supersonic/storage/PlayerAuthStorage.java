package dev.riever.supersonic.storage;

import com.velocitypowered.api.proxy.Player;

import java.time.Instant;

public interface PlayerAuthStorage {
    void savePlayerAuth(Player player, String otp, Instant expiresAt);
    Player authenticate(String otp);
    default void initialize() {}
}
