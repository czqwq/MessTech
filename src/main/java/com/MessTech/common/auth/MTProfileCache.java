package com.MessTech.common.auth;

import java.util.concurrent.TimeUnit;

import com.MessTech.init.Config;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

/**
 * Per-player-name cache of successful online-mode session validations, the core of the ProfileCached port.
 * <p>
 * The entries are the {@link MTCachedProfile}s that {@link MTSessionService#hasJoinedServer} keeps after a real
 * validation. A later login with a cached name is answered from here without asking Mojang, so a player who already
 * played on the server can still join while the session server is unreachable (and the login flow needs fewer
 * requests). Like the original mod the cache is <b>in memory only</b>: a server restart, {@code /pfc cleanall} or
 * {@code /pfc reload} empties it, and every entry expires {@code Config.profilecached.RESULT_TIMEOUT} minutes after it
 * was written.
 * <p>
 * The cache is built lazily and rebuilt when the configured timeout changes, because the session service itself is
 * created in {@code MinecraftServer}'s constructor - which runs <b>before</b> the mods' {@code preInit}, i.e. before
 * GTNHLib has read the config file. Reading {@link Config} is therefore deferred to the first login.
 */
public final class MTProfileCache {

    /**
     * The GTNHLib reload id every config field of this feature carries ({@code @Config.Reloadable}); {@code /pfc
     * reload} hands it to {@code ConfigurationManager#reloadConfig}.
     */
    public static final String RELOAD_ID = "pfc";

    /**
     * Lower bound of {@code RESULT_TIMEOUT}, in minutes: the ported mod clamps its {@code result-timeout} option up to
     * 10 as well.
     */
    private static final int MIN_TIMEOUT_MINUTES = 10;

    /** A timeout change rebuilds (and therefore empties) the cache; the value is the one the cache was built with. */
    private static volatile int cacheTimeoutMinutes = -1;

    private static volatile Cache<String, MTCachedProfile> cache;

    private MTProfileCache() {}

    public static boolean enabled() {
        return Config.profilecached.ENABLED;
    }

    public static boolean debug() {
        return Config.profilecached.DEBUG;
    }

    public static boolean verifyLastIp() {
        return Config.profilecached.VERIFY_LAST_IP;
    }

    /** The configured timeout in minutes, clamped like the ported mod's {@code result-timeout} (at least 10). */
    public static int timeoutMinutes() {
        return Math.max(MIN_TIMEOUT_MINUTES, Config.profilecached.RESULT_TIMEOUT);
    }

    private static Cache<String, MTCachedProfile> cache() {
        final int timeout = timeoutMinutes();
        Cache<String, MTCachedProfile> current = cache;
        if (current != null && cacheTimeoutMinutes == timeout) {
            return current;
        }
        synchronized (MTProfileCache.class) {
            if (cache == null || cacheTimeoutMinutes != timeout) {
                cache = CacheBuilder.newBuilder()
                    .expireAfterWrite(timeout, TimeUnit.MINUTES)
                    .build();
                cacheTimeoutMinutes = timeout;
            }
            return cache;
        }
    }

    /** The cached validation for {@code username}, or {@code null}. */
    public static MTCachedProfile get(String username) {
        return cache().getIfPresent(username);
    }

    public static void put(String username, MTCachedProfile profile) {
        cache().put(username, profile);
    }

    /** Drops the entry for {@code username}; answers whether there was one. */
    public static boolean invalidate(String username) {
        final MTCachedProfile cached = get(username);
        cache().invalidate(username);
        return cached != null;
    }

    /** Empties the cache but keeps the current timeout binding. */
    public static void clear() {
        final Cache<String, MTCachedProfile> current = cache;
        if (current != null) {
            current.invalidateAll();
        }
    }

    /** Number of entries currently held (Guava's estimate, the same value the ported {@code /pfc size} prints). */
    public static long size() {
        return cache().size();
    }
}
