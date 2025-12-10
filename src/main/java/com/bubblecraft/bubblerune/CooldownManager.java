package com.bubblecraft.bubblerune;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CooldownManager {
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private volatile long cooldownMillis;

    public CooldownManager(int cooldownSeconds) {
        this.cooldownMillis = cooldownSeconds * 1000L;
    }

    public void setCooldownSeconds(int seconds) {
        this.cooldownMillis = Math.max(0, seconds * 1000L);
    }

    public boolean isOnCooldown(UUID playerId) {
        if (playerId == null) return false;
        
        Long expire = cooldowns.get(playerId);
        if (expire == null) {
            return false;
        }
        
        long now = System.currentTimeMillis();
        if (now >= expire) {
            // Auto-cleanup expired cooldown
            cooldowns.remove(playerId);
            return false;
        }
        return true;
    }

    public long getRemainingCooldown(UUID playerId) {
        if (playerId == null) return 0;
        
        Long expire = cooldowns.get(playerId);
        if (expire == null) {
            return 0;
        }
        
        long now = System.currentTimeMillis();
        long remaining = expire - now;
        
        if (remaining <= 0) {
            cooldowns.remove(playerId);
            return 0;
        }
        
        return remaining / 1000;
    }

    public void setCooldown(UUID playerId) {
        if (playerId == null) return;
        
        long now = System.currentTimeMillis();
        cooldowns.put(playerId, now + cooldownMillis);
    }

    public void removeCooldown(UUID playerId) {
        if (playerId == null) return;
        cooldowns.remove(playerId);
    }

    public void clearAll() {
        cooldowns.clear();
    }
    
    /**
     * Cleanup expired cooldowns to prevent memory leaks
     * Should be called periodically
     */
    public void cleanupExpired() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Long>> iterator = cooldowns.entrySet().iterator();
        int removed = 0;
        
        while (iterator.hasNext()) {
            Map.Entry<UUID, Long> entry = iterator.next();
            if (entry.getValue() <= now) {
                iterator.remove();
                removed++;
            }
        }
        
        if (removed > 0) {
            // Optional: log cleanup for monitoring
            // System.out.println("Cleaned up " + removed + " expired cooldowns");
        }
    }
}
