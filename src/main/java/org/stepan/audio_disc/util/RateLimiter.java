package org.stepan.audio_disc.util;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiter for controlling upload frequency per player.
 */
public class RateLimiter {
    private final Map<UUID, PlayerRateInfo> playerRates;
    private final int maxUploadsPerMinute;
    private final long cooldownMillis;

    public RateLimiter(int maxUploadsPerMinute, long cooldownSeconds) {
        this.playerRates = new ConcurrentHashMap<>();
        this.maxUploadsPerMinute = maxUploadsPerMinute;
        this.cooldownMillis = cooldownSeconds * 1000;
    }

    /**
     * Checks if a player can upload based on rate limits.
     * 
     * @param playerId The player's UUID
     * @return A RateLimitResult indicating if upload is allowed
     */
    public RateLimitResult checkLimit(UUID playerId) {
        long now = System.currentTimeMillis();
        PlayerRateInfo info = playerRates.computeIfAbsent(playerId, k -> new PlayerRateInfo());

        // Check cooldown
        if (now - info.lastUploadTime < cooldownMillis) {
            long remainingCooldown = (cooldownMillis - (now - info.lastUploadTime)) / 1000;
            return RateLimitResult.denied("Please wait " + remainingCooldown + " seconds before uploading again");
        }

        // Clean up old uploads (older than 1 minute)
        info.uploadTimes.removeIf(time -> now - time > 60000);

        // Check uploads per minute
        if (info.uploadTimes.size() >= maxUploadsPerMinute) {
            return RateLimitResult.denied("Upload limit reached. Maximum " + maxUploadsPerMinute + " uploads per minute");
        }

        return RateLimitResult.allowed();
    }

    /**
     * Records an upload for a player.
     * 
     * @param playerId The player's UUID
     */
    public void recordUpload(UUID playerId) {
        long now = System.currentTimeMillis();
        PlayerRateInfo info = playerRates.computeIfAbsent(playerId, k -> new PlayerRateInfo());
        info.uploadTimes.add(now);
        info.lastUploadTime = now;
    }

    /**
     * Gets the remaining cooldown time for a player.
     * 
     * @param playerId The player's UUID
     * @return The remaining cooldown in seconds, or 0 if no cooldown
     */
    public long getRemainingCooldown(UUID playerId) {
        PlayerRateInfo info = playerRates.get(playerId);
        if (info == null) {
            return 0;
        }

        long now = System.currentTimeMillis();
        long elapsed = now - info.lastUploadTime;
        
        if (elapsed >= cooldownMillis) {
            return 0;
        }

        return (cooldownMillis - elapsed) / 1000;
    }

    /**
     * Clears rate limit data for a player.
     * 
     * @param playerId The player's UUID
     */
    public void clearPlayer(UUID playerId) {
        playerRates.remove(playerId);
    }

    /**
     * Clears all rate limit data.
     */
    public void clearAll() {
        playerRates.clear();
    }

    /**
     * Information about a player's upload rate.
     */
    private static class PlayerRateInfo {
        final java.util.List<Long> uploadTimes = new java.util.ArrayList<>();
        long lastUploadTime = 0;
    }

    /**
     * Result of a rate limit check.
     */
    public static class RateLimitResult {
        private final boolean allowed;
        private final String message;

        private RateLimitResult(boolean allowed, String message) {
            this.allowed = allowed;
            this.message = message;
        }

        public static RateLimitResult allowed() {
            return new RateLimitResult(true, null);
        }

        public static RateLimitResult denied(String message) {
            return new RateLimitResult(false, message);
        }

        public boolean isAllowed() {
            return allowed;
        }

        public String getMessage() {
            return message;
        }
    }
}
