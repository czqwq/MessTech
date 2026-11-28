package com.MessTech.common.auth;

import com.mojang.authlib.GameProfile;

/**
 * One cached online-mode login validation, ported from ProfileCached's {@code PlayData}: the full profile the session
 * server returned (UUID plus the texture properties the client needs) and, when
 * {@code Config.profilecached.VERIFY_LAST_IP} is on, the remote address the validation was requested from.
 * <p>
 * The address is {@code null} both when the IP check is off and when the login handler could not report one; the
 * session service only revalidates an entry when both addresses are known.
 */
public final class MTCachedProfile {

    /** The profile returned by {@code MinecraftSessionService#hasJoinedServer}, textures included. */
    public final GameProfile profile;

    /** Remote address of the login that produced {@link #profile}, or {@code null}. */
    public final String lastLoginIp;

    public MTCachedProfile(GameProfile profile, String lastLoginIp) {
        this.profile = profile;
        this.lastLoginIp = lastLoginIp;
    }
}
