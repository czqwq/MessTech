package com.MessTech.common.wireless;

import java.lang.reflect.Method;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import gregtech.api.enums.Mods;

/**
 * Reflective bridge to ServerUtilities' team API. ServerUtilities is optional and is not a compile dependency, so the
 * only thing this class knows about it is the exact static method
 * {@code serverutils.lib.data.ServerUtilitiesAPI.arePlayersInSameTeam(UUID, UUID)}. When the mod is missing or the call
 * fails, team sharing silently degrades to "owner only", as specified by the wireless ME network contract.
 */
public final class MTWirelessTeamAccess {

    private static final String API_CLASS = "serverutils.lib.data.ServerUtilitiesAPI";
    private static final String SAME_TEAM_METHOD = "arePlayersInSameTeam";
    private static final Logger LOG = LogManager.getLogger("MessTech");

    private static Method sameTeamMethod;
    private static boolean resolved;
    private static boolean available;

    private MTWirelessTeamAccess() {}

    /** @return whether player-to-player team sharing can currently be used. */
    public static boolean isTeamSharingAvailable() {
        resolveIfNeeded();
        return available;
    }

    /**
     * @param first  the first player's UUID
     * @param second the second player's UUID
     * @return whether both players belong to the same ServerUtilities team. Always {@code true} for the same UUID.
     */
    public static boolean arePlayersInSameTeam(UUID first, UUID second) {
        if (first == null || second == null) return false;
        if (first.equals(second)) return true;

        resolveIfNeeded();
        if (!available) return false;

        try {
            Object result = sameTeamMethod.invoke(null, first, second);
            return result instanceof Boolean value && value;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            // The mod is loaded but its API is not usable any more (wrong version, not initialised, ...). Degrade to
            // owner-only access instead of spamming the log every time a hatch is checked.
            available = false;
            sameTeamMethod = null;
            LOG.warn(
                "ServerUtilities team API is unavailable, wireless ME networks degrade to owner-only access",
                error);
            return false;
        }
    }

    private static synchronized void resolveIfNeeded() {
        if (resolved) return;
        resolved = true;
        if (!Mods.ServerUtilities.isModLoaded()) return;

        try {
            Class<?> apiClass = Class.forName(API_CLASS);
            sameTeamMethod = apiClass.getMethod(SAME_TEAM_METHOD, UUID.class, UUID.class);
            available = true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            available = false;
            sameTeamMethod = null;
            LOG.warn(
                "Could not resolve ServerUtilities team API, wireless ME networks degrade to owner-only access",
                error);
        }
    }
}
