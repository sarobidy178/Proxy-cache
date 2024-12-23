package proxy;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ExpiringCache {
    private final ConcurrentHashMap<String, CacheEntry> cache;
    private final ScheduledExecutorService cleanupService;
    private final long ttlSeconds;

    public ExpiringCache(long ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
        this.cache = new ConcurrentHashMap<>();
        
        this.cleanupService = Executors.newSingleThreadScheduledExecutor();
        this.cleanupService.scheduleAtFixedRate(this::cleanupExpiredEntries, 
            0, 1, TimeUnit.SECONDS);
    }

    public void listCache()   {
        System.out.println("Les contenus du cache sont : ");
        for(String key : this.cache.keySet())    {
            System.out.println(key);
        }
    }

    public void removeCacheEntry(String key)  {
        if(this.cache.containsKey(key)) {
            cache.remove(key);
            System.out.println("Cache " + key + " supprimee avec succes");
        }
        else    {
            System.out.println("Ce cache n'existe pas");
        }
    }

    public void put(String key, byte[] value) {
        cache.put(key, new CacheEntry(value, ttlSeconds));
    }

    public byte[] get(String key) {
        CacheEntry entry = cache.get(key);
        if (entry != null && !entry.isExpired()) {
            return entry.data;
        }

        if (entry != null) {
            System.out.println("Entrée expirée supprimée du cache : " + key);
            cache.remove(key);
        }
        return null;
    }

    public boolean containsKey(String key) {
        CacheEntry entry = cache.get(key);
        if (entry != null && !entry.isExpired()) {
            return true;
        }

        if (entry != null && entry.isExpired()) {
            System.out.println("Entrée expirée supprimée du cache : " + key);
            cache.remove(key);
        }
        return false;
    }

    private void cleanupExpiredEntries() {
        cache.entrySet().removeIf(entry -> {
            if (entry.getValue().isExpired()) {
                System.out.println("Entrée expirée supprimée du cache lors du nettoyage périodique : " + entry.getKey());
                return true;
            }
            return false;
        });
    }

    public void shutdown() {
        cleanupService.shutdown();
        try {
            if (!cleanupService.awaitTermination(1, TimeUnit.SECONDS)) {
                cleanupService.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupService.shutdownNow();
        }
    }
}