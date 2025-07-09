package dev.riever.supersonic.storage;

import com.velocitypowered.api.proxy.Player;
import org.slf4j.Logger;

import java.time.Instant;
import java.util.concurrent.*;

public class InMemoryPlayerAuthStorage implements PlayerAuthStorage {
    record AuthRequest(Player player, Instant expiresAt) {
        public boolean isExpired() {
            return this.expiresAt().isBefore(Instant.now());
        }
    }

    private final ConcurrentMap<String, AuthRequest> authRequests = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor();
    private final int cleanerPeriod;
    private final Logger logger;

    public InMemoryPlayerAuthStorage(Logger logger, int cleanerPeriod) {
        this.logger = logger;
        this.cleanerPeriod = cleanerPeriod;
    }

    public InMemoryPlayerAuthStorage(Logger logger) {
        this(logger, 5);
    }

    public void initialize() {
        this.scheduleCleaner(this.cleanerPeriod);
        this.logger.info("Scheduled auth requests to be cleaned every {} minute{}.", this.cleanerPeriod, this.cleanerPeriod == 1 ? "" : "s");
    }

    public void savePlayerAuth(Player player, String otp, Instant expiresAt) {
        this.authRequests.put(otp, new AuthRequest(player, expiresAt));
    }

    public Player authenticate(String otp) {
        AuthRequest authRequest = this.authRequests.remove(otp);
        if (authRequest == null || authRequest.isExpired()) {
            return null;
        }
        return authRequest.player();
    }

    public void clearExpiredAuthRequests() {
        this.authRequests.values().removeIf(AuthRequest::isExpired);
    }

    private void scheduleCleaner(int minutes) {
        this.cleaner.scheduleAtFixedRate(this::clearExpiredAuthRequests, 0, minutes, TimeUnit.MINUTES);
    }

    public void clearAllAuthRequests() {
        this.authRequests.clear();
    }
}
