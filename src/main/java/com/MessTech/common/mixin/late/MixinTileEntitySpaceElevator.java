package com.MessTech.common.mixin.late;

/*
 * Temporarily disabled: the MessTech space modules now extend TileEntityModuleBase and are recognised by the vanilla
 * elevator natively. If the data-sharing patch is needed again, restore the commented-out class below.
 * What it did: shadowed {@code mProjectModuleHatches}, injected at the RETURN of
 * {@code getAvailableDataForModules} and averaged the elevator's data over the active module hatches, counting original
 * miners once each plus the {@code SpaceModuleMinerInfinity} ones vanilla does not count.
 * @Mixin(value = TileEntitySpaceElevator.class, remap = false)
 * public abstract class MixinTileEntitySpaceElevator {
 * @Shadow
 * public ArrayList<TileEntityModuleBase> mProjectModuleHatches;
 * @Inject(method = "getAvailableDataForModules", at = @At("RETURN"), cancellable = true)
 * private void MessTech$shareDataWithNativeInfinityMiners(CallbackInfoReturnable<Long> cir) {
 * ...
 * }
 * }
 */
