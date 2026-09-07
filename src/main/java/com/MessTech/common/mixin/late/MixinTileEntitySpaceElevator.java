package com.MessTech.common.mixin.late;

/*
 * Temporarily disabled: the MessTech space modules now extend TileEntityModuleBase and are
 * recognised by the vanilla elevator natively. If the data-sharing patch is needed again later,
 * uncomment the class below.
 * @Mixin(value = TileEntitySpaceElevator.class, remap = false)
 * public abstract class MixinTileEntitySpaceElevator {
 * @Shadow
 * public ArrayList<TileEntityModuleBase> mProjectModuleHatches;
 * @Inject(method = "getAvailableDataForModules", at = @At("RETURN"), cancellable = true)
 * private void MessTech$shareDataWithNativeInfinityMiners(CallbackInfoReturnable<Long> cir) {
 * long originalMiners = 0;
 * long messTechMiners = 0;
 * if (mProjectModuleHatches != null) {
 * for (TileEntityModuleBase module : mProjectModuleHatches) {
 * if (module.getBaseMetaTileEntity() == null || !module.getBaseMetaTileEntity()
 * .isActive() || !module.isDataInputListEmpty()) {
 * continue;
 * }
 * boolean isOriginalMiner = module instanceof TileEntityModuleMiner;
 * if (isOriginalMiner) {
 * originalMiners++;
 * }
 * // Only count MessTech miners that the vanilla miner hierarchy does not already count.
 * if (module instanceof SpaceModuleMinerInfinity && !isOriginalMiner) {
 * messTechMiners++;
 * }
 * }
 * }
 * long originalReturn = cir.getReturnValue();
 * long fullData = originalMiners == 0 ? originalReturn : originalReturn * originalMiners;
 * long totalMiners = originalMiners + messTechMiners;
 * if (totalMiners == 0) {
 * cir.setReturnValue(fullData);
 * } else {
 * cir.setReturnValue(fullData / totalMiners);
 * }
 * }
 * }
 */
