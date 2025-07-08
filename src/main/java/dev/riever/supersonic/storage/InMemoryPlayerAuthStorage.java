package dev.riever.supersonic.storage;

import com.velocitypowered.api.proxy.Player;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryPlayerAuthStorage implements PlayerAuthStorage {
    record AuthRequest(Player player, Instant expiresAt) {}

    private final ConcurrentMap<String, AuthRequest> authRequests = new ConcurrentHashMap<>();

    public void savePlayerAuth(Player player, String otp, Instant expiresAt) {
        this.authRequests.put(otp, new AuthRequest(player, expiresAt));
    }

    public Player authenticate(String otp) {
        AuthRequest authRequest = this.authRequests.remove(otp);
        if (authRequest == null || authRequest.expiresAt().isBefore(Instant.now())) {
            return null;
        }
        return authRequest.player();
    }

    public void clearExpiredAuthRequests() {
        Instant now = Instant.now();
        this.authRequests.values().removeIf(authRequest -> authRequest.expiresAt().isBefore(now));
    }

    public void clearAllAuthRequests() {
        this.authRequests.clear();
    }
}
