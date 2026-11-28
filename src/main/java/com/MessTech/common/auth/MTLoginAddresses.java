package com.MessTech.common.auth;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.TimeUnit;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

/**
 * The remote address of the login that is currently being validated, for {@code Config.profilecached.VERIFY_LAST_IP}.
 * <p>
 * 1.7.10's {@code MinecraftSessionService#hasJoinedServer(GameProfile, String)} has no address parameter (1.20.1
 * added one, which is what the ported mod compares against), and the check runs on an {@code User Authenticator #n}
 * thread that does not see the login handler. {@code MixinNetHandlerLoginServer_ProfileCached} therefore records the
 * address at the head of {@code NetHandlerLoginServer#processEncryptionResponse} - before vanilla starts that thread -
 * and {@link MTSessionService} reads it while deciding whether a cached entry may be reused.
 * <p>
 * The map is a small expiring cache rather than a plain map: an attempt that never reaches the session service (a
 * disconnect, a failed nonce) must not leak its entry, and the address is only ever consumed within seconds.
 */
public final class MTLoginAddresses {

    private static final Cache<String, String> LOGIN_ADDRESSES = CacheBuilder.newBuilder()
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .build();

    private MTLoginAddresses() {}

    /** Remembers {@code address} as the login address of {@code username}. */
    public static void record(String username, SocketAddress address) {
        final String ip = ipOf(address);
        if (ip != null) {
            LOGIN_ADDRESSES.put(username, ip);
        }
    }

    /** The recorded login address of {@code username}, or {@code null} when unknown. */
    public static String get(String username) {
        return username == null ? null : LOGIN_ADDRESSES.getIfPresent(username);
    }

    private static String ipOf(SocketAddress address) {
        if (address instanceof InetSocketAddress) {
            final InetAddress inetAddress = ((InetSocketAddress) address).getAddress();
            if (inetAddress != null) {
                return inetAddress.getHostAddress();
            }
        }
        return null;
    }
}
