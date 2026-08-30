//
// package com.MessTech.common.machine.Base;
//
// import static gregtech.api.enums.GTValues.V;
// import static gregtech.api.metatileentity.BaseTileEntity.TOOLTIP_DELAY;
// import static gregtech.api.util.GTStructureUtility.buildHatchAdder;
// import static gregtech.api.util.GTUtility.validMTEList;
// import static net.minecraft.util.StatCollector.translateToLocalFormatted;
//
// import java.util.ArrayList;
// import java.util.Collections;
// import java.util.List;
// import java.util.Optional;
//
// import javax.annotation.Nonnull;
//
// import net.minecraft.item.ItemStack;
// import net.minecraft.util.StatCollector;
// import net.minecraftforge.common.util.ForgeDirection;
// import net.minecraftforge.fluids.FluidStack;
//
// import org.apache.commons.lang3.ArrayUtils;
// import org.jetbrains.annotations.Nullable;
//
// import com.gtnewhorizons.modularui.api.screen.ModularWindow;
// import com.gtnewhorizons.modularui.api.screen.UIBuildContext;
// import com.gtnewhorizons.modularui.common.widget.DrawableWidget;
// import com.gtnewhorizons.modularui.common.widget.FakeSyncWidget;
//
// import gregtech.api.enums.Materials;
// import gregtech.api.enums.Textures;
// import gregtech.api.enums.TieredVariant;
// import gregtech.api.gui.modularui.CircularGaugeDrawable;
// import gregtech.api.gui.modularui.GTUITextures;
// import gregtech.api.interfaces.IHatchElement;
// import gregtech.api.interfaces.IOutputBus;
// import gregtech.api.interfaces.ITexture;
// import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
// import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
// import gregtech.api.interfaces.tileentity.IOverclockDescriptionProvider;
// import gregtech.api.logic.ProcessingLogic;
// import gregtech.api.metatileentity.implementations.MTEBasicMachine;
// import gregtech.api.metatileentity.implementations.MTEHatch;
// import gregtech.api.metatileentity.implementations.MTEHatchInput;
// import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
// import gregtech.api.metatileentity.implementations.MTEHatchOutputBus;
// import gregtech.api.metatileentity.implementations.MTEHatchVoidBus;
// import gregtech.api.objects.overclockdescriber.OverclockDescriber;
// import gregtech.api.objects.overclockdescriber.SteamOverclockDescriber;
// import gregtech.api.recipe.RecipeMap;
// import gregtech.api.recipe.check.CheckRecipeResult;
// import gregtech.api.recipe.check.CheckRecipeResultRegistry;
// import gregtech.api.structure.error.StructureError;
// import gregtech.api.structure.error.StructureErrors;
// import gregtech.api.util.GTUtility;
// import gregtech.api.util.HatchElementBuilder;
// import gregtech.api.util.IGTHatchAdder;
// import gregtech.api.util.shutdown.ShutDownReasonRegistry;
// import gregtech.common.tileentities.machines.IDualInputHatch;
// import gregtech.common.tileentities.machines.IDualInputInventory;
// import gregtech.common.tileentities.machines.IDualInputInventoryWithPattern;
// import gregtech.common.tileentities.machines.MTEHatchCraftingInputME;
// import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.MTEHatchSteamBusInput;
// import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.MTEHatchSteamBusOutput;
// import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.GTPPMultiBlockBase;
// import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.MTEHatchCustomFluidBase;
//
//
// public abstract class MTSteamMultiBase<T extends MTSteamMultiBase<T>> extends GTPPMultiBlockBase<T>
// implements IOverclockDescriptionProvider {
//
//
// private final OverclockDescriber overclockDescriber;
//
// public ArrayList<MTEHatchSteamBusInput> mSteamInputs = new ArrayList<>();
// // 用 MTEHatchOutputBus 而不是 MTEHatchSteamBusOutput,以便兼容虚空输出仓(MTEHatchVoidBus)
// public ArrayList<MTEHatchOutputBus> mSteamOutputs = new ArrayList<>();
// public ArrayList<MTEHatchCustomFluidBase> mSteamInputFluids = new ArrayList<>();
//
// public MTSteamMultiBase(String aName) {
// super(aName);
// this.overclockDescriber = createOverclockDescriber();
// }
//
// public MTSteamMultiBase(int aID, String aName, String aNameRegional) {
// super(aID, aName, aNameRegional);
// this.overclockDescriber = createOverclockDescriber();
// }
//
// @Override
// public ITexture[] getTexture(final IGregTechTileEntity aBaseMetaTileEntity, final ForgeDirection side,
// final ForgeDirection facing, final int aColorIndex, final boolean aActive, final boolean aRedstone) {
// if (side == facing) {
// return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(getCasingTextureIndex()),
// aActive ? getFrontOverlayActive() : getFrontOverlay() };
// }
// return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(getCasingTextureIndex()) };
// }
//
// protected abstract ITexture getFrontOverlay();
//
// protected abstract ITexture getFrontOverlayActive();
//
// public abstract int getTierRecipes();
//
// private int getCasingTextureIndex() {
// return 10;
// }
//
// @Override
// protected ProcessingLogic createProcessingLogic() {
// return new ProcessingLogic().setMaxParallelSupplier(this::getTrueParallel);
// }
//
// @Override
// protected void setProcessingLogicPower(ProcessingLogic logic) {
// logic.setAvailableVoltage(V[getTierRecipes()]);
// // We need to trick the GT_ParallelHelper we have enough amps for all recipe parallels.
// logic.setAvailableAmperage(getMaxParallelRecipes());
// logic.setAmperageOC(false);
// logic.setMaxTierSkips(0);
// }
//
// public ArrayList<FluidStack> getAllSteamStacks() {
// ArrayList<FluidStack> aFluids = new ArrayList<>();
// FluidStack aSteam = Materials.Steam.getGas(1);
// for (FluidStack aFluid : this.getStoredFluids()) {
// if (aFluid.isFluidEqual(aSteam)) {
// aFluids.add(aFluid);
// }
// }
// return aFluids;
// }
//
// public int getTotalSteamStored() {
// int aSteam = 0;
// for (FluidStack aFluid : getAllSteamStacks()) {
// aSteam += aFluid.amount;
// }
// return aSteam;
// }
//
// public int getTotalSteamCapacity() {
// int aSteam = 0;
// for (MTEHatchCustomFluidBase tHatch : validMTEList(mSteamInputFluids)) {
// aSteam += tHatch.getRealCapacity();
// }
// return aSteam;
// }
//
// public boolean tryConsumeSteam(int aAmount) {
// if (getTotalSteamStored() <= 0) {
// return false;
// } else {
// return this.depleteInput(Materials.Steam.getGas(aAmount));
// }
// }
//
// @Override
// public int getMaxEfficiency(ItemStack arg0) {
// return 0;
// }
//
// @Override
// public void onPostTick(final IGregTechTileEntity aBaseMetaTileEntity, final long aTick) {
// if (aBaseMetaTileEntity.isServerSide()) {
// if (this.mUpdate == 1 || this.mStartUpCheck == 1) {
// // 统一走 clearHatches(),避免手动清列表漏掉 mInputBusses/mOutputBusses 等
// clearHatches();
// }
// }
// super.onPostTick(aBaseMetaTileEntity, aTick);
// }
//
// /**
// * Called every tick the Machine runs
// */
// @Override
// public boolean onRunningTick(ItemStack aStack) {
// if (lEUt < 0) {
// long aSteamVal = ((-lEUt * 10000) / Math.max(1000, mEfficiency));
// // Logger.INFO("Trying to drain "+aSteamVal+" steam per tick.");
// if (!tryConsumeSteam((int) aSteamVal)) {
// stopMachine(ShutDownReasonRegistry.POWER_LOSS);
// return false;
// }
// }
// return true;
// }
//
// @Override
// public boolean addToMachineList(final IGregTechTileEntity aTileEntity, final int aBaseCasingIndex) {
// if (aTileEntity == null) return false;
// final IMetaTileEntity aMetaTileEntity = aTileEntity.getMetaTileEntity();
// if (aMetaTileEntity == null) return false;
//
// // 蒸汽仓优先分发:GTPP 基类的 addToMachineList 不认识 MTEHatchCustomFluidBase,
// // 会把蒸汽输入仓丢进虚空(之前 mSteamInputFluids 永远是空的致命 bug)
// if (addSteamInputFluidHatch(aTileEntity, aBaseCasingIndex)) return true;
// if (addSteamBusInput(aTileEntity, aBaseCasingIndex)) return true;
// if (addSteamBusOutput(aTileEntity, aBaseCasingIndex)) return true;
//
// return super.addToMachineList(aTileEntity, aBaseCasingIndex)
// || addExoticEnergyInputToMachineList(aTileEntity, aBaseCasingIndex);
// }
//
// /**
// * 通用仓添加:更新纹理/合成图标/配方表,并去重。
// */
// public <E> boolean addToMachineListInternal(ArrayList<E> aList, final E aTileEntity, final int aBaseCasingIndex) {
// if (aTileEntity == null) return false;
//
// if (aTileEntity instanceof MTEHatch mteHatch) {
// mteHatch.updateTexture(aBaseCasingIndex);
// mteHatch.updateCraftingIcon(this.getMachineCraftingIcon());
// }
//
// // Set recipe map for input hatches.
// if (aTileEntity instanceof MTEHatchInput hatch) hatch.mRecipeMap = getRecipeMap();
// if (aTileEntity instanceof MTEHatchInputBus hatch) hatch.mRecipeMap = getRecipeMap();
//
// if (aList.contains(aTileEntity)) return false;
//
// return aList.add(aTileEntity);
// }
//
// /**
// * 蒸汽输入总线(MTEHatchSteamBusInput)。
// */
// public boolean addSteamBusInput(final IGregTechTileEntity aTileEntity, final int aBaseCasingIndex) {
// if (aTileEntity == null) return false;
// final IMetaTileEntity aMetaTileEntity = aTileEntity.getMetaTileEntity();
// if (aMetaTileEntity == null) return false;
//
// if (aMetaTileEntity instanceof MTEHatchSteamBusInput steamBus) {
// this.resetRecipeMapForHatch(aTileEntity, getRecipeMap());
// return addToMachineListInternal(mSteamInputs, steamBus, aBaseCasingIndex);
// }
// return false;
// }
//
// /**
// * 蒸汽输出总线(MTEHatchSteamBusOutput),兼容虚空输出仓(MTEHatchVoidBus)。
// */
// public boolean addSteamBusOutput(final IGregTechTileEntity aTileEntity, final int aBaseCasingIndex) {
// if (aTileEntity == null) return false;
// final IMetaTileEntity aMetaTileEntity = aTileEntity.getMetaTileEntity();
// if (aMetaTileEntity == null) return false;
//
// if (aMetaTileEntity instanceof MTEHatchSteamBusOutput || aMetaTileEntity instanceof MTEHatchVoidBus) {
// return addToMachineListInternal(mSteamOutputs, (MTEHatchOutputBus) aMetaTileEntity, aBaseCasingIndex);
// }
// return false;
// }
//
// /**
// * 蒸汽输入仓(MTEHatchCustomFluidBase,锁定为蒸汽)。只接受一个。
// */
// public boolean addSteamInputFluidHatch(final IGregTechTileEntity aTileEntity, final int aBaseCasingIndex) {
// if (aTileEntity == null) return false;
// final IMetaTileEntity aMetaTileEntity = aTileEntity.getMetaTileEntity();
// if (aMetaTileEntity == null) return false;
//
// if (aMetaTileEntity instanceof MTEHatchCustomFluidBase fluidHatch
// && fluidHatch.mLockedFluid.equals(Materials.Steam.mGas)
// && mSteamInputFluids.isEmpty()) {
// return addToMachineListInternal(mSteamInputFluids, fluidHatch, aBaseCasingIndex);
// }
// return false;
// }
//
// public boolean resetRecipeMapForHatch(IGregTechTileEntity aTileEntity, RecipeMap<?> aMap) {
// if (aTileEntity == null) return false;
// IMetaTileEntity meta = aTileEntity.getMetaTileEntity();
// if (meta instanceof MTEHatch hatch) return resetRecipeMapForHatch(hatch, aMap);
// return false;
// }
//
// public boolean resetRecipeMapForHatch(MTEHatch aTileEntity, RecipeMap<?> aMap) {
// if (aTileEntity == null) return false;
// if (aTileEntity instanceof MTEHatchInput hatch) {
// hatch.mRecipeMap = aMap;
// return true;
// }
// if (aTileEntity instanceof MTEHatchInputBus hatch) {
// hatch.mRecipeMap = aMap;
// return true;
// }
// return false;
// }
//
// /*
// * Handle I/O with custom hatches
// */
//
// @Override
// public boolean depleteInput(FluidStack aLiquid) {
// if (aLiquid == null) return false;
// for (MTEHatchCustomFluidBase tHatch : validMTEList(mSteamInputFluids)) {
// FluidStack tLiquid = tHatch.getFluid();
// if (tLiquid != null && tLiquid.isFluidEqual(aLiquid)) {
// tLiquid = tHatch.drain(aLiquid.amount, false);
// if (tLiquid != null && tLiquid.amount >= aLiquid.amount) {
// tLiquid = tHatch.drain(aLiquid.amount, true);
// return tLiquid != null && tLiquid.amount >= aLiquid.amount;
// }
// }
// }
// return false;
// }
//
// @Override
// public boolean depleteInput(ItemStack aStack) {
// if (GTUtility.isStackInvalid(aStack)) return false;
// FluidStack aLiquid = GTUtility.getFluidForFilledItem(aStack, true);
// if (aLiquid != null) return depleteInput(aLiquid);
// // 蒸汽输入仓是单格流体仓,固定检查第 0 格
// for (MTEHatchCustomFluidBase tHatch : validMTEList(mSteamInputFluids)) {
// ItemStack slot0 = tHatch.getBaseMetaTileEntity()
// .getStackInSlot(0);
// if (slot0 != null && GTUtility.areStacksEqual(aStack, slot0)) {
// if (slot0.stackSize >= aStack.stackSize) {
// tHatch.getBaseMetaTileEntity()
// .decrStackSize(0, aStack.stackSize);
// return true;
// }
// }
// }
// for (MTEHatchSteamBusInput tHatch : validMTEList(mSteamInputs)) {
// tHatch.mRecipeMap = getRecipeMap();
// IGregTechTileEntity tile = tHatch.getBaseMetaTileEntity();
// for (int i = tile.getSizeInventory() - 1; i >= 0; i--) {
// ItemStack stored = tile.getStackInSlot(i);
// // 修复:之前误用 getStackInSlot(0) 比较/扣除,会扣错槽位
// if (stored != null && GTUtility.areStacksEqual(aStack, stored)) {
// if (stored.stackSize >= aStack.stackSize) {
// tile.decrStackSize(i, aStack.stackSize);
// return true;
// }
// }
// }
// }
// return false;
// }
//
// @Override
// public ArrayList<FluidStack> getStoredFluidsForColor(Optional<Byte> color) {
// ArrayList<FluidStack> rList = new ArrayList<>();
// for (MTEHatchCustomFluidBase tHatch : validMTEList(mSteamInputFluids)) {
// byte hatchColor = tHatch.getBaseMetaTileEntity()
// .getColorization();
// if (color.isPresent() && hatchColor != -1 && hatchColor != color.get()) continue;
// if (tHatch.getFillableStack() != null) {
// rList.add(tHatch.getFillableStack());
// }
// }
// for (MTEHatchInput hatch : this.mInputHatches) if (hatch.getFillableStack() != null) {
// byte hatchColor = hatch.getBaseMetaTileEntity()
// .getColorization();
// if (color.isPresent() && hatchColor != -1 && hatchColor != color.get()) continue;
// rList.add(hatch.getFillableStack());
// }
// return rList;
// }
//
// @Override
// public ArrayList<ItemStack> getStoredInputsForColor(Optional<Byte> color) {
// ArrayList<ItemStack> rList = new ArrayList<>();
// for (MTEHatchSteamBusInput tHatch : validMTEList(mSteamInputs)) {
// byte hatchColor = tHatch.getBaseMetaTileEntity()
// .getColorization();
// if (color.isPresent() && hatchColor != -1 && hatchColor != color.get()) continue;
// tHatch.mRecipeMap = getRecipeMap();
// for (int i = tHatch.getBaseMetaTileEntity()
// .getSizeInventory() - 1; i >= 0; i--) {
// if (tHatch.getBaseMetaTileEntity()
// .getStackInSlot(i) != null) {
// rList.add(
// tHatch.getBaseMetaTileEntity()
// .getStackInSlot(i));
// }
// }
// }
// return rList;
// }
//
// @Override
// public void updateSlots() {
// for (MTEHatchCustomFluidBase tHatch : validMTEList(mSteamInputFluids)) tHatch.updateSlots();
// for (MTEHatchSteamBusInput tHatch : validMTEList(mSteamInputs)) tHatch.updateSlots();
// for (MTEHatchInputBus tHatch : validMTEList(mInputBusses)) tHatch.updateSlots();
// }
//
// @Override
// public List<IOutputBus> getOutputBusses() {
// // 蒸汽输出仓现在进 mSteamOutputs 而非 mOutputBusses,必须在这里补上,否则产物会丢失
// List<IOutputBus> output = new ArrayList<>(super.getOutputBusses());
// for (MTEHatchOutputBus outputBus : validMTEList(mSteamOutputs)) {
// if (outputBus.isValid()) output.add(outputBus);
// }
// return output;
// }
//
// @Override
// public boolean supportsBatchMode() {
// return false;
// }
//
// @Override
// public void clearHatches() {
// super.clearHatches();
// mInputHatches.clear();
// mSteamInputFluids.clear();
// mSteamInputs.clear();
// mSteamOutputs.clear();
// mOutputHatches.clear();
// }
//
// public static boolean isColorAbsent(short hatchColors, byte color) {
// return (hatchColors & (1 << color)) == 0;
// }
//
// public short getHatchColors() {
// short hatchColors = 0;
//
// for (var bus : mInputBusses) hatchColors |= (short) (1 << bus.getColor());
// for (var hatch : mInputHatches) hatchColors |= (short) (1 << hatch.getColor());
//
// for (var bus : mSteamInputs) hatchColors |= (short) (1 << bus.getColor());
// for (var hatch : mSteamInputFluids) hatchColors |= (short) (1 << hatch.getColor());
//
// return hatchColors;
// }
//
// @Override
// @Nonnull
// public CheckRecipeResult doCheckRecipe() {
// CheckRecipeResult result = CheckRecipeResultRegistry.NO_RECIPE;
//
// // check crafting input hatches first
// for (IDualInputHatch dualInputHatch : mDualInputHatches) {
// ItemStack[] sharedItems = dualInputHatch.getSharedItems();
// for (var it = dualInputHatch.inventories(); it.hasNext();) {
// IDualInputInventory slot = it.next();
//
// if (!slot.isEmpty()) {
// // try to cache the possible recipes from pattern
// if (slot instanceof IDualInputInventoryWithPattern withPattern) {
// if (!processingLogic.tryCachePossibleRecipesFromPattern(withPattern)) {
// // move on to next slots if it returns false, which means there is no possible recipes with
// // given pattern.
// continue;
// }
// }
//
// processingLogic.setInputItems(ArrayUtils.addAll(sharedItems, slot.getItemInputs()));
// processingLogic.setInputFluids(slot.getFluidInputs());
//
// CheckRecipeResult foundResult = processingLogic.process();
// if (foundResult.wasSuccessful()) {
// return foundResult;
// }
// if (foundResult != CheckRecipeResultRegistry.NO_RECIPE) {
// // Recipe failed in interesting way, so remember that and continue searching
// result = foundResult;
// }
// }
// }
// }
//
// result = checkRecipeForCustomHatches(result);
// if (result.wasSuccessful()) {
// return result;
// }
//
// // Use hatch colors if any; fallback to color 1 otherwise.
// short hatchColors = getHatchColors();
// boolean doColorChecking = hatchColors != 0;
// if (!doColorChecking) hatchColors = 0b1;
//
// for (byte color = 0; color < (doColorChecking ? 16 : 1); color++) {
// if (isColorAbsent(hatchColors, color)) continue;
// processingLogic.setInputFluids(getStoredFluidsForColor(Optional.of(color)));
// if (isInputSeparationEnabled()) {
// if (mInputBusses.isEmpty() && mSteamInputs.isEmpty()) {
// CheckRecipeResult foundResult = processingLogic.process();
// if (foundResult.wasSuccessful()) return foundResult;
// // Recipe failed in interesting way, so remember that and continue searching
// if (foundResult != CheckRecipeResultRegistry.NO_RECIPE) result = foundResult;
// } else {
// for (MTEHatchInputBus bus : mInputBusses) {
// if (bus instanceof MTEHatchCraftingInputME) continue;
// byte busColor = bus.getColor();
// if (busColor != -1 && busColor != color) continue;
// List<ItemStack> inputItems = new ArrayList<>();
// for (int i = bus.getSizeInventory() - 1; i >= 0; i--) {
// ItemStack stored = bus.getStackInSlot(i);
// if (stored != null) inputItems.add(stored);
// }
// if (canUseControllerSlotForRecipe() && getControllerSlot() != null) {
// inputItems.add(getControllerSlot());
// }
// processingLogic.setInputItems(inputItems);
// CheckRecipeResult foundResult = processingLogic.process();
// if (foundResult.wasSuccessful()) return foundResult;
// // Recipe failed in interesting way, so remember that and continue searching
// if (foundResult != CheckRecipeResultRegistry.NO_RECIPE) result = foundResult;
// }
// for (MTEHatchSteamBusInput bus : mSteamInputs) {
// byte busColor = bus.getColor();
// if (busColor != -1 && busColor != color) continue;
// List<ItemStack> inputItems = new ArrayList<>();
// for (int i = bus.getSizeInventory() - 1; i >= 0; i--) {
// ItemStack stored = bus.getStackInSlot(i);
// if (stored != null) inputItems.add(stored);
// }
// if (canUseControllerSlotForRecipe() && getControllerSlot() != null) {
// inputItems.add(getControllerSlot());
// }
// processingLogic.setInputItems(inputItems);
// CheckRecipeResult foundResult = processingLogic.process();
// if (foundResult.wasSuccessful()) return foundResult;
// // Recipe failed in interesting way, so remember that and continue searching
// if (foundResult != CheckRecipeResultRegistry.NO_RECIPE) result = foundResult;
// }
// }
// } else {
// List<ItemStack> inputItems = getStoredInputsForColor(Optional.of(color));
// if (canUseControllerSlotForRecipe() && getControllerSlot() != null) {
// inputItems.add(getControllerSlot());
// }
// processingLogic.setInputItems(inputItems);
// CheckRecipeResult foundResult = processingLogic.process();
// if (foundResult.wasSuccessful()) return foundResult;
// // Recipe failed in interesting way, so remember that
// if (foundResult != CheckRecipeResultRegistry.NO_RECIPE) result = foundResult;
// }
// }
// return result;
// }
//
// @Override
// public boolean resetRecipeMapForAllInputHatches(RecipeMap<?> aMap) {
// boolean ret = super.resetRecipeMapForAllInputHatches(aMap);
// for (MTEHatchSteamBusInput hatch : mSteamInputs) {
// if (resetRecipeMapForHatch(hatch, aMap)) {
// ret = true;
// }
// }
// for (MTEHatchInput g : this.mInputHatches) {
// if (resetRecipeMapForHatch(g, aMap)) {
// ret = true;
// }
// }
//
// return ret;
// }
//
// private int uiSteamStored = 0;
// private int uiSteamCapacity = 0;
//
// @Override
// public void addUIWidgets(ModularWindow.Builder builder, UIBuildContext buildContext) {
// super.addUIWidgets(builder, buildContext);
// builder.widget(new FakeSyncWidget.IntegerSyncer(this::getTotalSteamCapacity, val -> uiSteamCapacity = val));
// builder.widget(new FakeSyncWidget.IntegerSyncer(this::getTotalSteamStored, val -> uiSteamStored = val));
//
// builder.widget(
// new DrawableWidget().setDrawable(GTUITextures.STEAM_GAUGE_BG_STEEL)
// .dynamicTooltip(
// () -> Collections.singletonList(
// translateToLocalFormatted(
// MTEBasicMachine.STEAM_AMOUNT_LANGKEY,
// numberFormat.format(uiSteamStored),
// numberFormat.format(uiSteamCapacity))))
// .setTooltipShowUpDelay(TOOLTIP_DELAY)
// .setUpdateTooltipEveryTick(true)
// .setSize(48, 42)
// .setPos(-48, -8));
//
// builder.widget(
// new DrawableWidget().setDrawable(new CircularGaugeDrawable(() -> (float) uiSteamStored / uiSteamCapacity))
// .setPos(-48 + 21, -8 + 21)
// .setSize(18, 4));
// }
//
// protected static <T extends MTSteamMultiBase<T>> HatchElementBuilder<T> buildSteamInput(Class<T> typeToken) {
// return buildHatchAdder(typeToken).adder(MTSteamMultiBase::addToMachineList)
// .hatchIds(31040)
// .shouldReject(t -> !t.mSteamInputFluids.isEmpty());
// }
//
// protected static OverclockDescriber createOverclockDescriber() {
// return new SteamOverclockDescriber(TieredVariant.BRONZE, 1, 2);
// }
//
// @Override
// public @Nullable OverclockDescriber getOverclockDescriber() {
// return overclockDescriber;
// }
//
// public enum SteamHatchElement implements IHatchElement<MTSteamMultiBase<?>> {
//
// InputBus_Steam("hatch.input_bus.tier.steam") {
//
// @Override
// public List<? extends Class<? extends IMetaTileEntity>> mteClasses() {
// return Collections.singletonList(MTEHatchSteamBusInput.class);
// }
//
// @Override
// public long count(MTSteamMultiBase<?> t) {
// return t.mSteamInputs.size();
// }
//
// @Override
// public IGTHatchAdder<? super MTSteamMultiBase<?>> adder() {
// return MTSteamMultiBase::addSteamBusInput;
// }
// },
// OutputBus_Steam("hatch.output_bus.tier.steam") {
//
// @Override
// public List<? extends Class<? extends IMetaTileEntity>> mteClasses() {
// return Collections.singletonList(MTEHatchSteamBusOutput.class);
// }
//
// @Override
// public long count(MTSteamMultiBase<?> t) {
// return t.mSteamOutputs.size();
// }
//
// @Override
// public IGTHatchAdder<? super MTSteamMultiBase<?>> adder() {
// return MTSteamMultiBase::addSteamBusOutput;
// }
// },;
//
// private final String langKey;
//
// SteamHatchElement(String langKey) {
// this.langKey = "gt.blockmachines." + langKey + ".name";
// }
//
// @Override
// public String getDescriptionLangKey() {
// return langKey;
// }
//
// @Override
// public String getDisplayName() {
// return StatCollector.translateToLocal(langKey);
// }
//
// @Override
// public IGTHatchAdder<? super MTSteamMultiBase<?>> adder() {
// return MTSteamMultiBase::addToMachineList;
// }
// }
//
// @Override
// public boolean getDefaultInputSeparationMode() {
// return true;
// }
//
// @Override
// public boolean getDefaultHasMaintenanceChecks() {
// return false;
// }
//
// // ── 结构错误检查(移植自 GT5U 的 MTESteamMultiBlockBase)──
//
// /** 蒸汽输入仓必须存在。 */
// protected final void checkHasSteamInput(List<StructureError> errors) {
// if (mSteamInputFluids.isEmpty()) {
// errors.add(StructureErrors.of("GT5U.gui.text.structure_error.missing_steam_input"));
// }
// }
//
// protected final void checkHasSteamInputBus(List<StructureError> errors) {
// if (mSteamInputs.isEmpty()) {
// errors.add(StructureErrors.of("GT5U.gui.text.structure_error.missing_steam_input_bus"));
// }
// }
//
// protected final void checkHasSteamOutputBus(List<StructureError> errors) {
// if (mSteamOutputs.isEmpty()) {
// errors.add(StructureErrors.of("GT5U.gui.text.structure_error.missing_steam_output_bus"));
// }
// }
//
// @Override
// protected void checkHasAnyInput(List<StructureError> errors) {
// // 蒸汽机器输入 = 蒸汽输入总线 + 普通输入总线 + 输入仓 + 双输入仓
// if (mSteamInputs.isEmpty() && mInputBusses.isEmpty()
// && mInputHatches.isEmpty()
// && mDualInputHatches.isEmpty()) {
// errors.add(StructureErrors.of("GT5U.gui.text.structure_error.no_input"));
// }
// }
//
// @Override
// protected void checkHasAnyOutput(List<StructureError> errors) {
// if (mSteamOutputs.isEmpty() && mOutputBusses.isEmpty() && mOutputHatches.isEmpty()) {
// errors.add(StructureErrors.of("GT5U.gui.text.structure_error.no_output"));
// }
// }
// }
