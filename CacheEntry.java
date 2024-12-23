package proxy;

public class CacheEntry {
    byte[] data;
    long expirationTime;

    CacheEntry(byte[] data, long ttlSeconds) {
        this.data = data;
        this.expirationTime = System.currentTimeMillis() + (ttlSeconds * 1000);
    }

    boolean isExpired() {
        return System.currentTimeMillis() > expirationTime;
    }
}
