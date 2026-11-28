package com.MessTech.common.auth;

import com.MessTech.init.MessTech;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.exceptions.AuthenticationUnavailableException;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import com.mojang.authlib.yggdrasil.YggdrasilMinecraftSessionService;

/**
 * The caching {@code MinecraftSessionService}: replaces the service vanilla creates in {@code MinecraftServer}'s
 * constructor (see {@code MixinMinecraftServer_ProfileCached}) with one that answers an already-cached login from
 * {@link MTProfileCache} instead of the Mojang session server. Ported from ProfileCached's
 * {@code PFCSessionService#hasJoinedServer}.
 * <p>
 * Behavior of a validation, in order:
 * <ul>
 * <li>cache disabled ({@code Config.profilecached.ENABLED = false}): straight to the real session server, nothing is
 * cached;</li>
 * <li>cached name and {@code VERIFY_LAST_IP} off (or no address to compare): the cached profile is returned - the
 * Mojang session server is not contacted, which is exactly what lets a cached player in while it is down;</li>
 * <li>cached name but the login comes from another address than the one the entry was validated from: the entry is
 * dropped and the login is revalidated by Mojang, then cached again;</li>
 * <li>no cached name: real validation, and the returned profile (textures included) is cached under its name.</li>
 * </ul>
 * A failed validation ({@code null}) is never cached, and an unreachable session server still propagates
 * {@link AuthenticationUnavailableException} unless the cache answered first.
 * <p>
 * Deliberate tradeoff of the ported mod: within {@code RESULT_TIMEOUT} the server does not re-ask Mojang for a cached
 * name, so a stale entry (e.g. a name that changed owners) is trusted for that long; {@code VERIFY_LAST_IP} narrows
 * this to the last address.
 */
public class MTSessionService extends YggdrasilMinecraftSessionService {

    public MTSessionService(YggdrasilAuthenticationService authenticationService) {
        super(authenticationService);
    }

    @Override
    public GameProfile hasJoinedServer(GameProfile user, String serverId) throws AuthenticationUnavailableException {
        if (!MTProfileCache.enabled()) {
            return super.hasJoinedServer(user, serverId);
        }

        final String username = user.getName();
        final MTCachedProfile cached = MTProfileCache.get(username);
        if (cached != null) {
            final String loginIp = loginIp(username);
            if (cached.lastLoginIp != null && loginIp != null && !cached.lastLoginIp.equals(loginIp)) {
                // The player's address changed while the entry was cached: drop it and let Mojang validate again.
                if (MTProfileCache.debug()) {
                    MessTech.MT_LOG.info(
                        "Profile cache hit but the address changed, revalidating: username={}, serverId={}, address={}, cachedAddress={}",
                        username,
                        serverId,
                        loginIp,
                        cached.lastLoginIp);
                }
                MTProfileCache.invalidate(username);
            } else {
                if (MTProfileCache.debug()) {
                    MessTech.MT_LOG.info(
                        "Profile cache hit: username={}, serverId={}, cachedAddress={}",
                        username,
                        serverId,
                        cached.lastLoginIp);
                }
                return cached.profile;
            }
        }

        final GameProfile profile = super.hasJoinedServer(user, serverId);
        if (profile != null) {
            final String loginIp = loginIp(username);
            MTProfileCache.put(profile.getName(), new MTCachedProfile(profile, loginIp));
            if (MTProfileCache.debug()) {
                MessTech.MT_LOG
                    .info("Profile cache stored: username={}, serverId={}, address={}", username, serverId, loginIp);
            }
        }
        return profile;
    }

    private static String loginIp(String username) {
        return MTProfileCache.verifyLastIp() ? MTLoginAddresses.get(username) : null;
    }
}
