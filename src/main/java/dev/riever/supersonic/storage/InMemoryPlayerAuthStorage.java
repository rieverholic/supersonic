package dev.riever.supersonic.storage;

import com.velocitypowered.api.proxy.Player;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryPlayerAuthStorage implements PlayerAuthStorage {
    record AuthRequest(Player player, Instant expiresAt) {}

    private final ConcurrentMap<String, AuthRequest> authRequests = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, String> playerOtp = new ConcurrentHashMap<>();

    public void savePlayerAuth(Player player, String otp, Instant expiresAt) {
        UUID uuid = player.getUniqueId();
        String oldOtp = this.playerOtp.put(uuid, otp);
        if (oldOtp != null) {
            this.authRequests.remove(oldOtp);
        }
        this.authRequests.put(otp, new AuthRequest(player, expiresAt));
    }

    public Player authenticate(String otp) {
        AuthRequest authRequest = this.authRequests.remove(otp);
        if (authRequest == null) {
            return null;
        }
        Player player = authRequest.player();
        UUID uuid = player.getUniqueId();
        playerOtp.remove(uuid);
        if (authRequest.expiresAt().isBefore(Instant.now())) {
            return null;
        }
        return player;
    }

    public void clearExpiredAuthRequests() {
        Instant now = Instant.now();
        this.authRequests.values().removeIf(authRequest -> authRequest.expiresAt().isBefore(now));
        this.playerOtp.values().removeIf(otp -> !this.authRequests.containsKey(otp));
    }

    public void clearAllAuthRequests() {
        this.authRequests.clear();
        this.playerOtp.clear();
    }
}
