package com.MessTech.common.mixin.early;

import net.minecraft.network.NetworkManager;
import net.minecraft.server.network.NetHandlerLoginServer;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.MessTech.common.auth.MTLoginAddresses;
import com.mojang.authlib.GameProfile;

/**
 * Records the remote address of an online-mode login for the session cache's {@code VERIFY_LAST_IP} option.
 * <p>
 * 1.7.10's {@code hasJoinedServer} has no address argument, so the address has to be captured outside the session
 * service. {@code processEncryptionResponse} is the method that starts vanilla's {@code User Authenticator #n} thread
 * (HEAD runs on the netty thread, before the thread exists), and by then the profile of the {@code Login Start} packet
 * is in {@code field_147337_i}, while {@code field_147333_a} is the connection: both are what the vanilla auth call
 * uses. {@link MTLoginAddresses} keeps it in a short-lived map keyed by name; {@code MTSessionService} reads it when
 * deciding whether a cached entry may be reused and drops the entry on an address change.
 * <p>
 * Server-only, like {@code MixinMinecraftServer_ProfileCached}: the mixin is listed in the early config's {@code
 * "server"} list.
 */
@Mixin(NetHandlerLoginServer.class)
public abstract class MixinNetHandlerLoginServer_ProfileCached {

    /** The profile from the Login Start packet; vanilla replaces it with the validated one after the auth check. */
    @Shadow
    private GameProfile field_147337_i;

    @Shadow
    @Final
    public NetworkManager field_147333_a;

    @Inject(method = "processEncryptionResponse", at = @At("HEAD"))
    private void messtech$recordLoginAddress(CallbackInfo ci) {
        final GameProfile profile = this.field_147337_i;
        if (profile != null && profile.getName() != null) {
            MTLoginAddresses.record(profile.getName(), this.field_147333_a.getSocketAddress());
        }
    }
}
