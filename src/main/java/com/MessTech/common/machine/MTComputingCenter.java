package com.MessTech.common.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import org.jetbrains.annotations.NotNull;

import com.MessTech.common.gui.MTComputingCenterGui;
import com.MessTech.common.machine.Base.CalculateMultiMachineBase;
import com.MessTech.common.machine.hatch.MTHatchRack;
import com.MessTech.common.recipe.MTRecipeMaps;
import com.MessTech.common.util.ExtraShutdownReason;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IItemSource;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureUtility;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.casing.Casings;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.recipe.check.SimpleCheckRecipeResult;
import gregtech.api.util.AssemblyLineUtils;
import gregtech.api.util.GTUtility;
import gregtech.api.util.HatchElementBuilder;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.api.util.shutdown.ShutDownReason;
import gregtech.api.util.shutdown.ShutDownReasonRegistry;
import gregtech.api.util.shutdown.SimpleShutDownReason;
import gregtech.common.WirelessComputationPacket;
import gtPlusPlus.xmod.thermalfoundation.fluid.TFFluids;
import lombok.Getter;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import tectech.mechanics.dataTransport.QuantumDataPacket;
import tectech.recipe.TecTechRecipeMaps;
import tectech.thing.metaTileEntity.hatch.MTEHatchDataInput;
import tectech.thing.metaTileEntity.hatch.MTEHatchDataOutput;
import tectech.thing.metaTileEntity.hatch.MTEHatchObjectHolder;
import tectech.thing.metaTileEntity.multi.base.render.TTRenderedExtendedFacingTexture;

/**
 * MessTech Computing Center (skeleton).
 * <p>
 * Structure/registration only for now:
 * <ul>
 * <li>A = Energy / Data hatches</li>
 * <li>D = {@link com.MessTech.common.machine.hatch.MTHatchRack}</li>
 * <li>No maintenance hatch</li>
 * </ul>
 * Mode 1 uses the Quantum Computer fake recipe pool; Mode 2 uses the Research Station fake recipe pool.
 * Actual processing logic is intentionally not implemented yet.
 */
public class MTComputingCenter extends CalculateMultiMachineBase<MTComputingCenter> implements ISurvivalConstructable {

    private static IIconContainer ScreenOFF;
    private static IIconContainer ScreenON;

    // region Research / computation state
    private ItemStack holderStackToConsume;
    @Getter
    private long computationRemaining = 0;
    @Getter
    private long computationRequired = 0;
    private TecTechRecipeMaps.TTResearchStationALRecipe currentResearchRecipe;
    private boolean wirelessMode = false;
    @Getter
    private boolean overclockEnabled = false;
    @Getter
    private int overclockRatio = 1;
    @Getter
    private int overvoltageRatio = 1;
    private long machineHeat = 0;
    public static final long MAX_HEAT = 10_000_000L;

    public static final int PACKET_LOSS_GRACE_WINDOW = 20;
    public static final int PACKET_LOSS_DECAY_WINDOW = 80;
    public static final int PACKET_LOSS_FULL_WINDOW = PACKET_LOSS_GRACE_WINDOW + PACKET_LOSS_DECAY_WINDOW;
    private int ticksUntilPacketLossFail = PACKET_LOSS_FULL_WINDOW;
    private long packetLossDecayFrom = 0;
    // endregion

    // region Structure piece offsets
    private static final int HORIZONTAL_OFFSET = 8;
    private static final int VERTICAL_OFFSET = 8;
    private static final int DEPTH_OFFSET = 8;
    // endregion

    // region Structure definition
    protected static final String STRUCTURE_PIECE_MAIN = "main";

    // spotless:off
    @SuppressWarnings("SpellCheckingInspection")
    private static final String[][] structure_string = new String[][]{
        {"                ","                ","                ","                ","                ","                ","                ","                ","                ","                ","                ","                ","                ","                ","                ","                "},
        {"                ","                ","                ","                ","                ","                ","                ","                ","                ","                ","                ","                ","                ","                ","                ","                "},
        {"                ","                ","                ","                ","                ","                ","                ","                ","                ","                ","                ","                ","                ","                ","                ","                "},
        {"                ","                ","                ","                ","                ","                ","                ","                ","                ","                ","                ","                ","                ","                ","                ","                "},
        {"                ","                ","                ","      BBBBB     ","     BEEEEEB    ","    BEEEEEEEB   ","   BEEEEEEEEEB  ","   BEEEEEEEEEB  ","   BAEEEEEEEAB  ","   BEEEEEEEEEB  ","   BEEEEEEEEEB  ","    BEEEEEEEB   ","     BEEEEEB    ","      BBBBB     ","                ","                "},
        {"                ","                ","                ","      B   B     ","     BD   DB    ","    BD     DB   ","   BD       DB  ","   BD       DB  ","   BA       AB  ","   BD       DB  ","   BD       DB  ","    BD     DB   ","     BD   DB    ","      B   B     ","                ","                "},
        {"                ","                ","                ","      B   B     ","     BD   DB    ","    BD     DB   ","   BD       DB  ","   BD       DB  ","   BA       AB  ","   BD       DB  ","   BD       DB  ","    BD     DB   ","     BD   DB    ","      B   B     ","                ","                "},
        {"                ","                ","                ","      B   B     ","     BD   DB    ","    BD     DB   ","   BD       DB  ","   BD       DB  ","   BA   F   AB  ","   BD       DB  ","   BD       DB  ","    BD     DB   ","     BD   DB    ","      B   B     ","                ","                "},
        {"                ","                ","                ","      B   B     ","     BD   DB    ","    BD     DB   ","   BD       DB  ","   BD       DB  ","   BA   ~   AB  ","   BD       DB  ","   BD       DB  ","    BD     DB   ","     BD   DB    ","      B   B     ","                ","                "},
        {"                ","                ","                ","      B   B     ","     BD   DB    ","    BD     DB   ","   BD       DB  ","   BD  DDD  DB  ","   BA  DAD  AB  ","   BD  DDD  DB  ","   BD       DB  ","    BD     DB   ","     BD   DB    ","      B   B     ","                ","                "},
        {"                ","                ","                ","      BBBBB     ","     BEEEEEB    ","    BEEEEEEEB   ","   BEEEEEEEEEB  ","   BEEEEEEEEEB  ","   BAEEEEEEEAB  ","   BEEEEEEEEEB  ","   BEEEEEEEEEB  ","    BEEEEEEEB   ","     BEEEEEB    ","      BBBBB     ","                ","                "},
        {"                ","                ","      AAAAA     ","     AAAAAAA    ","    AACCCCCAA   ","   AACCCCCCCAA  ","  AACCCCCCCCCAA ","  AACCCCCCCCCAA ","  AACCCCCCCCCAA ","  AACCCCCCCCCAA ","  AACCCCCCCCCAA ","   AACCCCCCCAA  ","    AACCCCCAA   ","     AAAAAAA    ","      AAAAA     ","                "},
        {"                ","                ","                ","                ","                ","                ","                ","                ","                ","                ","                ","                ","                ","                ","                ","                "},
        {"                ","                ","                ","                ","                ","                ","                ","                ","                ","                ","                ","                ","                ","                ","                ","                "},
        {"                ","                ","                ","                ","                ","                ","                ","                ","                ","                ","                ","                ","                ","                ","                ","                "},
        {"                ","                ","                ","                ","                ","                ","                ","                ","                ","                ","                ","                ","                ","                ","                ","                "}
    };
    // spotless:on

    private static final IStructureDefinition<MTComputingCenter> STRUCTURE_DEFINITION = StructureDefinition
        .<MTComputingCenter>builder()
        .addShape(STRUCTURE_PIECE_MAIN, transpose(structure_string))
        // A: normal hatches + Uncertainty / Data hatches
        .addElement(
            'A',
            HatchElementBuilder.<MTComputingCenter>builder()
                .anyOf(
                    HatchElement.InputBus,
                    HatchElement.InputHatch,
                    HatchElement.OutputBus,
                    HatchElement.Energy.or(HatchElement.ExoticEnergy),
                    HatchElement.InputData,
                    HatchElement.OutputData,
                    HatchElement.WirelessComputationOutput)
                .casingIndex(Casings.NanochipMeshInterfaceCasing.textureId)
                .hint(1)
                .buildAndChain(ofBlock(Casings.NanochipMeshInterfaceCasing.getBlock(), 1)))
        // B: reinforcement casing
        .addElement('B', ofBlock(Casings.NanochipReinforcementCasing.getBlock(), 2))
        // C: computational matrix casing
        .addElement('C', ofBlock(Casings.NanochipComputationalMatrixCasing.getBlock(), 3))
        // D: MTHatchRack, using NanochipFirewallProjectionCasing texture
        .addElement(
            'D',
            HatchElementBuilder.<MTComputingCenter>builder()
                .anyOf(CalculateMultiMachineBase.HatchElement.Rack)
                .casingIndex(Casings.NanochipFirewallProjectionCasing.textureId)
                .hint(2)
                .buildAndChain(ofBlock(Casings.NanochipFirewallProjectionCasing.getBlock(), 4)))
        // E: nanochip complex glass
        .addElement('E', Casings.NanochipComplexGlass.asElement())
        // F: research holder or air
        .addElement(
            'F',
            HatchElementBuilder.<MTComputingCenter>builder()
                .anyOf(CalculateMultiMachineBase.HatchElement.Holder)
                .casingIndex(Casings.NanochipMeshInterfaceCasing.textureId)
                .hint(3)
                .buildAndChain(StructureUtility.isAir()))
        .build();
    // endregion

    public MTComputingCenter(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
        machineMode = 0;
    }

    public MTComputingCenter(String aName) {
        super(aName);
        machineMode = 0;
    }

    @Override
    public IStructureDefinition<MTComputingCenter> getStructureDefinition() {
        return STRUCTURE_DEFINITION;
    }

    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack,
        java.util.List<gregtech.api.structure.error.StructureError> errors) {
        eUncertainHatches.clear();
        eInputData.clear();
        eOutputData.clear();
        eRacks.clear();
        eHolders.clear();
        eWirelessComputationOutputs.clear();
        if (!checkPiece(STRUCTURE_PIECE_MAIN, HORIZONTAL_OFFSET, VERTICAL_OFFSET, DEPTH_OFFSET, errors)) return;
        if (machineMode == 0) {
            checkHasRack(errors);
            checkHasDataOutput(errors);
        }
        if (machineMode == 1) {
            checkHasOutputBus(errors);
            checkHasHolder(errors);
        }
        checkHasAnyEnergy(errors);
    }

    @Override
    public RecipeMap<?> getRecipeMap() {
        // Mode 0 = Computing Center fake recipes, Mode 1 = Research Station fake recipes.
        return machineMode == 1 ? TecTechRecipeMaps.researchStationFakeRecipes
            : MTRecipeMaps.computingCenterFakeRecipes;
    }

    @Override
    public @NotNull Collection<RecipeMap<?>> getAvailableRecipeMaps() {
        return Arrays.<RecipeMap<?>>asList(
            MTRecipeMaps.computingCenterFakeRecipes,
            TecTechRecipeMaps.researchStationFakeRecipes);
    }

    @Override
    protected @NotNull MTComputingCenterGui getGui() {
        return new MTComputingCenterGui(this);
    }

    @Override
    public int totalMachineMode() {
        return 2;
    }

    @Override
    public String getMachineModeName(int mode) {
        return mode == 1 ? StatCollector.translateToLocal("machine.computingcenter.mode.research")
            : StatCollector.translateToLocal("machine.computingcenter.mode.computing");
    }

    @Override
    public void onScrewdriverRightClick(ForgeDirection side, EntityPlayer aPlayer, float aX, float aY, float aZ,
        ItemStack aTool) {
        if (getBaseMetaTileEntity() != null && getBaseMetaTileEntity().isActive()) {
            if (aPlayer != null) {
                aPlayer.addChatMessage(
                    new ChatComponentText(
                        EnumChatFormatting.RED
                            + StatCollector.translateToLocal("machine.computingcenter.cannot_switch_active")));
            }
            return;
        }
        if (machineHeat > 0) {
            if (aPlayer != null) {
                aPlayer.addChatMessage(
                    new ChatComponentText(
                        EnumChatFormatting.RED
                            + StatCollector.translateToLocal("machine.computingcenter.cannot_switch_heat")));
            }
            return;
        }
        setMachineMode(nextMachineMode());
        if (machineMode != 0) {
            setWirelessModeEnabled(false);
        }
        if (aPlayer != null) {
            aPlayer.addChatMessage(
                new ChatComponentText(EnumChatFormatting.AQUA + "Mode: " + getMachineModeName(machineMode)));
        }
    }

    public long getAvailableData() {
        return eAvailableData;
    }

    public int getCurrentEUt() {
        return Math.abs(mEUt);
    }

    public long getTotalHeat() {
        return machineHeat;
    }

    private void sendToOwner(String langKey) {
        if (getBaseMetaTileEntity() == null) return;
        net.minecraft.tileentity.TileEntity te = (net.minecraft.tileentity.TileEntity) getBaseMetaTileEntity();
        if (te == null || te.getWorldObj() == null) return;
        EntityPlayer p = te.getWorldObj()
            .getPlayerEntityByName(getBaseMetaTileEntity().getOwnerName());
        if (p != null) {
            p.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.RED + StatCollector.translateToLocal(langKey) + EnumChatFormatting.RESET));
        }
    }

    public void setOverclockEnabled(boolean value) {
        if (!value && machineHeat > 0) {
            sendToOwner("machine.computingcenter.heat_present");
            return; // cannot disable overclock while heat is still present
        }
        this.overclockEnabled = value;
    }

    public void setOverclockRatio(int value) {
        this.overclockRatio = Math.max(1, value);
    }

    public void setOvervoltageRatio(int value) {
        this.overvoltageRatio = Math.max(1, value);
    }

    public boolean isWirelessModeEnabled() {
        return wirelessMode;
    }

    public void setWirelessModeEnabled(boolean value) {
        if (machineMode != 0) {
            wirelessMode = false;
            return;
        }
        wirelessMode = value;
        if (getBaseMetaTileEntity() != null) {
            if (value) {
                WirelessComputationPacket.enableWirelessNetWork(getBaseMetaTileEntity());
            } else {
                WirelessComputationPacket.disableWirelessNetWork(getBaseMetaTileEntity());
            }
        }
    }

    @Override
    public boolean onWireCutterRightClick(ForgeDirection side, ForgeDirection wrenchingSide, EntityPlayer aPlayer,
        float aX, float aY, float aZ, ItemStack aTool) {
        if (machineMode == 0) {
            setWirelessModeEnabled(!wirelessMode);
            if (aPlayer != null) {
                aPlayer.addChatMessage(new ChatComponentText(EnumChatFormatting.AQUA + "Wireless: " + wirelessMode));
            }
        } else {
            setWirelessModeEnabled(false);
        }
        return true;
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        aNBT.setInteger("computingMode", machineMode);
        aNBT.setLong("compRemaining", computationRemaining);
        aNBT.setLong("compRequired", computationRequired);
        aNBT.setInteger("packetLossFail", ticksUntilPacketLossFail);
        aNBT.setLong("packetLossDecay", packetLossDecayFrom);
        aNBT.setBoolean("overclockEnabled", overclockEnabled);
        aNBT.setInteger("overclockRatio", overclockRatio);
        aNBT.setInteger("overvoltageRatio", overvoltageRatio);
        aNBT.setLong("machineHeat", machineHeat);
        if (holderStackToConsume != null) {
            aNBT.setTag("holderStackToConsume", holderStackToConsume.writeToNBT(new NBTTagCompound()));
        }
        super.saveNBTData(aNBT);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        if (aNBT.hasKey("computingMode")) {
            machineMode = Math.clamp(aNBT.getInteger("computingMode"), 0, 1);
        } else {
            machineMode = 0;
        }
        if (aNBT.hasKey("compRemaining")) computationRemaining = aNBT.getLong("compRemaining");
        if (aNBT.hasKey("compRequired")) computationRequired = aNBT.getLong("compRequired");
        if (aNBT.hasKey("packetLossFail")) ticksUntilPacketLossFail = aNBT.getInteger("packetLossFail");
        if (aNBT.hasKey("packetLossDecay")) packetLossDecayFrom = aNBT.getLong("packetLossDecay");
        if (aNBT.hasKey("overclockEnabled")) overclockEnabled = aNBT.getBoolean("overclockEnabled");
        if (aNBT.hasKey("overclockRatio")) overclockRatio = Math.max(1, aNBT.getInteger("overclockRatio"));
        if (aNBT.hasKey("overvoltageRatio")) overvoltageRatio = Math.max(1, aNBT.getInteger("overvoltageRatio"));
        if (aNBT.hasKey("machineHeat")) machineHeat = aNBT.getLong("machineHeat");
        if (aNBT.hasKey("holderStackToConsume")) {
            holderStackToConsume = ItemStack.loadItemStackFromNBT(aNBT.getCompoundTag("holderStackToConsume"));
        }
        super.loadNBTData(aNBT);
    }

    @Override
    public int getStackSizeLimit(int slot, ItemStack stack) {
        if (slot == getControllerSlotIndex()) return 1;
        return super.getStackSizeLimit(slot, stack);
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (aBaseMetaTileEntity.isServerSide()) {
            // Re-assert the lock whenever heat is still present (covers chunk reloads and any
            // code path that might otherwise unlock the rack while the controller is idle).
            if (machineHeat > 0) {
                setRacksActive(true);
                if (!aBaseMetaTileEntity.isActive()) {
                    // Machine stopped: dissipate heat.
                    // Passive cooling every second...
                    if (aTick % 20 == 12) {
                        passiveCoolDown();
                    }
                    // ...and active cooling whenever a whole 10,000 heat batch can be removed,
                    // consuming Gelid Cryotheum until no full batch remains (or runs out of fluid).
                    consumeCryotheumForHeat();
                }
            }
            // Manual shutdown (GUI on/off, redstone...) does not call stopMachine(); the last
            // power/output numbers would linger in Waila/GUI. Reset them whenever the machine
            // is not actively running.
            if (!aBaseMetaTileEntity.isActive()) {
                resetStoppedDisplay();
            }
            if (mMachine && machineMode == 1) {
                tryRefillControllerSlot();
            }
        }
    }

    /**
     * Clears any leftover displayed values (power, output computation, research progress) when the
     * machine is stopped by any means — manual shutdown included, which never reaches stopMachine().
     */
    private void resetStoppedDisplay() {
        if (eAvailableData != 0) eAvailableData = 0;
        if (mEUt != 0) mEUt = 0;
        if (machineMode == 1 && computationRequired > 0) {
            computationRequired = computationRemaining = 0;
            eRequiredData = 0;
            currentResearchRecipe = null;
            holderStackToConsume = null;
            mOutputItems = null;
        }
    }

    private void tryRefillControllerSlot() {
        int slot = getControllerSlotIndex();
        ItemStack current = getStackInSlot(slot);
        if (current != null && current.stackSize > 0) return;
        for (MTEHatchInputBus bus : mInputBusses) {
            IGregTechTileEntity te = bus.getBaseMetaTileEntity();
            for (int i = 0; i < te.getSizeInventory(); i++) {
                ItemStack stack = te.getStackInSlot(i);
                if (stack != null && ItemList.Tool_DataStick.isStackEqual(stack, false, true)) {
                    stack.stackSize--;
                    if (stack.stackSize <= 0) {
                        te.setInventorySlotContents(i, null);
                    }
                    setInventorySlotContents(slot, ItemList.Tool_DataStick.get(1));
                    return;
                }
            }
        }
    }

    @Override
    public @NotNull CheckRecipeResult checkProcessing() {
        if (!mMachine) return CheckRecipeResultRegistry.NO_RECIPE;
        if (machineMode == 0) {
            return checkQuantumProcessing();
        }
        return checkResearchProcessing();
    }

    private CheckRecipeResult checkQuantumProcessing() {
        long computation = getRackComputation();
        eAvailableData = computation;
        // Default 1A UEV while running + 1A UEV per full 1,000,000 computation (round up if >0)
        int amps = 1;
        if (computation > 0) {
            amps += (int) Math.ceil(computation / 100_000.0);
        }
        mEUt = -(int) (GTValues.V[10] * amps);
        mMaxProgresstime = 1;
        mEfficiencyIncrease = 10000;
        setRacksActive(true);
        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    private CheckRecipeResult checkResearchProcessing() {
        ItemStack holderStack = getHolderStack();
        if (holderStack == null) return SimpleCheckRecipeResult.ofFailure("no_research_item");
        ItemStack specialStack = getStackInSlot(getControllerSlotIndex());
        if (!ItemList.Tool_DataStick.isStackEqual(specialStack, false, true)) {
            return CheckRecipeResultRegistry.NO_DATA_STICKS;
        }
        for (TecTechRecipeMaps.TTResearchStationALRecipe recipe : TecTechRecipeMaps.researchableALRecipeList) {
            if (GTUtility.areStacksEqual(recipe.mResearchItem, holderStack, true)) {
                currentResearchRecipe = recipe;
                holderStackToConsume = GTUtility.copyAmount(recipe.mResearchItem.stackSize, holderStack);
                computationRequired = computationRemaining = recipe.mComputation * 20L;
                eRequiredData = recipe.mComputationRequiredPerSec;
                ticksUntilPacketLossFail = PACKET_LOSS_FULL_WINDOW;
                packetLossDecayFrom = 0;
                // Do NOT consume the flash or holder item yet; they are consumed only on completion.
                mOutputItems = new ItemStack[] { createResearchOutput(recipe) };
                // Energy: normal research EU/t + total produced computation / 2 (internal racks + external input)
                long produced = getRackComputation() + getExternalInputData();
                mEUt = -(int) (Math.abs(recipe.mEUt) + produced / 2);
                mMaxProgresstime = 20;
                mEfficiencyIncrease = 10000;
                setRacksActive(true);
                setHoldersActive(true);
                return SimpleCheckRecipeResult.ofSuccess("researching");
            }
        }
        return CheckRecipeResultRegistry.NO_RECIPE;
    }

    private ItemStack getHolderStack() {
        if (eHolders.isEmpty()) return null;
        ItemStack stack = eHolders.getFirst().mInventory[0];
        if (stack == null || stack.stackSize <= 0) return null;
        return stack;
    }

    private long getRackComputation() {
        float oc = overclockEnabled ? overclockRatio : 1.0f;
        float ov = overclockEnabled ? overvoltageRatio : 1.0f;
        long sum = 0;
        for (MTHatchRack rack : eRacks) {
            if (!overclockEnabled) {
                rack.setHeat(0);
            }
            sum += rack.tickComponents(oc, ov);
            if (overclockEnabled) {
                machineHeat += rack.getHeat();
                rack.setHeat(0);
            } else {
                rack.setHeat(0);
            }
        }
        return sum;
    }

    /**
     * Passive cooling applied every second while the machine is stopped: mirrors the stock rack
     * decay (remove {@code max(heat/1000, 20)}) so the residual heat slowly dissipates on its own.
     */
    private void passiveCoolDown() {
        if (machineHeat <= 0) return;
        machineHeat -= Math.max(machineHeat / 1000, 100);
        if (machineHeat < 0) machineHeat = 0;
    }

    /**
     * Active cooling: for every whole 10,000 heat batch, consume 100,000L of Gelid Cryotheum.
     * Keeps draining as long as at least one full batch remains and the fluid is available;
     * when no full batch can be deducted any more (or Cryotheum runs out) it stops, leaving
     * the rest to passive cooling.
     */
    private void consumeCryotheumForHeat() {
        if (machineHeat <= 0) return;
        while (machineHeat >= 10000) {
            FluidStack cryotheum = new FluidStack(TFFluids.fluidCryotheum, 100000);
            if (!depleteInput(cryotheum, false)) {
                break; // cannot afford one full batch -> stop; passive cooling takes over
            }
            machineHeat -= 10000;
        }
    }

    private long getExternalInputData() {
        long input = 0;
        for (MTEHatchDataInput di : eInputData) {
            if (di.q != null) {
                input += di.q.getContent();
                di.setContents(null);
            }
        }
        return input;
    }

    private ItemStack createResearchOutput(TecTechRecipeMaps.TTResearchStationALRecipe recipe) {
        ItemStack output = ItemList.Tool_DataStick.get(1);
        output.setTagCompound(new NBTTagCompound());
        output.getTagCompound()
            .setString("author", EnumChatFormatting.BLUE + "MessTech" + EnumChatFormatting.WHITE);
        AssemblyLineUtils.setAssemblyLineRecipeOnDataStick(output, recipe);
        return output;
    }

    private void setRacksActive(boolean active) {
        // While heat is still present the rack components must stay locked, even when the
        // machine itself is stopped (racing heat can't be allowed to be pulled out freely).
        if (!active && machineHeat > 0) return;
        for (MTHatchRack rack : eRacks) {
            rack.getBaseMetaTileEntity()
                .setActive(active);
        }
    }

    private void meltAllComponents() {
        for (MTHatchRack rack : eRacks) {
            // Use the underlying 4 rack slots directly; getSizeInventory() returns 0 while active/locked.
            for (int i = 0; i < 4; i++) {
                rack.getBaseMetaTileEntity()
                    .setInventorySlotContents(i, null);
            }
            rack.setHeat(0);
        }
        machineHeat = 0;
    }

    private void setHoldersActive(boolean active) {
        for (MTEHatchObjectHolder holder : eHolders) {
            holder.getBaseMetaTileEntity()
                .setActive(active);
        }
    }

    private void resetResearchProgress() {
        computationRequired = computationRemaining = 0;
        eRequiredData = 0;
        ticksUntilPacketLossFail = PACKET_LOSS_FULL_WINDOW;
        packetLossDecayFrom = 0;
        holderStackToConsume = null;
        currentResearchRecipe = null;
        mOutputItems = null;
        setRacksActive(false);
        setHoldersActive(false);
    }

    @Override
    public boolean onRunningTick(ItemStack aStack) {
        if (machineMode == 1 && computationRequired > 0) {
            if (eHolders.isEmpty() || eHolders.get(0).mInventory[0] == null) {
                resetResearchProgress();
                stopMachine(ShutDownReasonRegistry.STRUCTURE_INCOMPLETE);
                return false;
            }
            ItemStack flash = getStackInSlot(getControllerSlotIndex());
            if (flash == null || !ItemList.Tool_DataStick.isStackEqual(flash, false, true)) {
                resetResearchProgress();
                stopMachine(ExtraShutdownReason.MISS_DATASTICK);
                return false;
            }
            long produced = getRackComputation() + getExternalInputData();
            eAvailableData = produced;
            if (computationRemaining <= 0) {
                computationRemaining = 0;
                mProgresstime = mMaxProgresstime;
            } else {
                if (eAvailableData >= eRequiredData) {
                    packetLossDecayFrom = computationRemaining;
                    ticksUntilPacketLossFail = PACKET_LOSS_FULL_WINDOW;
                }
                computationRemaining -= eAvailableData;
                if (eAvailableData == 0) {
                    // packet loss mitigation (ported from MTEResearchStation)
                    --ticksUntilPacketLossFail;
                    if (ticksUntilPacketLossFail <= 0) {
                        ticksUntilPacketLossFail = 0;
                        stopMachine(SimpleShutDownReason.ofCritical("computation_loss"));
                        return false;
                    }
                    if (ticksUntilPacketLossFail < PACKET_LOSS_DECAY_WINDOW) {
                        long diff = computationRequired - packetLossDecayFrom;
                        double mult = 1.0d - (double) ticksUntilPacketLossFail / PACKET_LOSS_DECAY_WINDOW;
                        computationRemaining = packetLossDecayFrom + (long) (diff * mult);
                    }
                }
                mProgresstime = 1;
            }
        } else if (machineMode == 0) {
            // Forward incoming computation automatically; it does not count toward power surcharge.
            long input = 0;
            for (MTEHatchDataInput di : eInputData) {
                if (di.q != null) {
                    input += di.q.getContent();
                    di.setContents(null);
                }
            }
            eAvailableData += input;
        }
        // Active cooling runs while the machine is working too; if it overheats, everything melts.
        consumeCryotheumForHeat();
        if (machineHeat > MAX_HEAT) {
            meltAllComponents();
            stopMachine(ExtraShutdownReason.OverHeating);
            return false;
        }
        return super.onRunningTick(aStack);
    }

    @Override
    public void stopMachine(@Nonnull ShutDownReason reason) {
        super.stopMachine(reason);
        eAvailableData = 0;
        mEUt = 0;
        setRacksActive(machineHeat > 0); // parts remain locked until heat is fully dissipated
        setHoldersActive(false);
        if (machineMode == 1) {
            resetResearchProgress();
        }
    }

    @Override
    public void onRemoval() {
        if (machineHeat > 0) {
            meltAllComponents();
        }
        super.onRemoval();
    }

    @Override
    protected void outputAfterRecipe() {
        if (machineMode == 1) {
            // consume holder item
            if (holderStackToConsume != null && !eHolders.isEmpty()) {
                ItemStack holderStack = eHolders.getFirst().mInventory[0];
                if (holderStack != null && GTUtility.areStacksEqual(holderStackToConsume, holderStack, false)
                    && holderStackToConsume.stackSize <= holderStack.stackSize) {
                    holderStack.stackSize -= holderStackToConsume.stackSize;
                    if (holderStack.stackSize <= 0) {
                        eHolders.getFirst().mInventory[0] = null;
                    }
                } else {
                    mOutputItems = null;
                }
            }
            // consume the original flash/data stick from the controller slot only on completion
            setInventorySlotContents(getControllerSlotIndex(), null);
            // if no output bus (should not happen in research mode), keep the result in the controller slot
            if (mOutputItems != null && mOutputBusses.isEmpty()) {
                setInventorySlotContents(getControllerSlotIndex(), mOutputItems[0]);
                mOutputItems = null;
            }
            resetResearchProgress();
        } else {
            // quantum mode: output computation (wireless or data output hatches)
            if (eAvailableData > 0) {
                if (wirelessMode && getBaseMetaTileEntity() != null) {
                    long tick;
                    net.minecraft.tileentity.TileEntity te = (net.minecraft.tileentity.TileEntity) getBaseMetaTileEntity();
                    tick = te.getWorldObj()
                        .getTotalWorldTime();
                    WirelessComputationPacket.uploadData(getBaseMetaTileEntity().getOwnerUuid(), eAvailableData, tick);
                } else if (!eOutputData.isEmpty()) {
                    QuantumDataPacket packet = new QuantumDataPacket(eAvailableData);
                    long packetSize = packet.getContent() / eOutputData.size();
                    for (MTEHatchDataOutput o : eOutputData) {
                        o.providePacket(new QuantumDataPacket(packetSize));
                    }
                }
            }
            setRacksActive(false);
        }
        super.outputAfterRecipe();
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        buildPiece(STRUCTURE_PIECE_MAIN, stackSize, hintsOnly, HORIZONTAL_OFFSET, VERTICAL_OFFSET, DEPTH_OFFSET);
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, IItemSource source, EntityPlayerMP actor) {
        return super.survivalConstruct(stackSize, elementBudget, source, actor);
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        return survivalBuildPiece(
            STRUCTURE_PIECE_MAIN,
            stackSize,
            HORIZONTAL_OFFSET,
            VERTICAL_OFFSET,
            DEPTH_OFFSET,
            elementBudget,
            env,
            false,
            true);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTComputingCenter(mName);
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType("Nano Computing Center")
            .addSeparator()
            .addInfo(EnumChatFormatting.AQUA + StatCollector.translateToLocal("machine.computingcenter.tooltip.0"))
            .addInfo(EnumChatFormatting.RED + StatCollector.translateToLocal("machine.computingcenter.tooltip.1"))
            .addSeparator()
            .addInfo(EnumChatFormatting.GREEN + StatCollector.translateToLocal("machine.computingcenter.tooltip.2"))
            .addInfo(EnumChatFormatting.RED + StatCollector.translateToLocal("machine.computingcenter.tooltip.3"))
            .addInfo(EnumChatFormatting.GOLD + StatCollector.translateToLocal("machine.computingcenter.tooltip.4"))
            .addSeparator()
            .addInfo(EnumChatFormatting.YELLOW + StatCollector.translateToLocal("machine.computingcenter.tooltip.5"))
            .addStructureInfo(
                EnumChatFormatting.GRAY + StatCollector.translateToLocal("machine.computingcenter.tooltip.6"))
            .toolTipFinisher();
        return tt;
    }

    @Override
    public void getWailaNBTData(EntityPlayerMP player, TileEntity tile, NBTTagCompound tag, World world, int x, int y,
        int z) {
        super.getWailaNBTData(player, tile, tag, world, x, y, z);
        tag.setInteger("mode", machineMode);
        tag.setLong("compRemaining", computationRemaining);
        tag.setLong("compRequired", computationRequired);
        tag.setLong("availableData", eAvailableData);
        tag.setInteger("eut", Math.abs(mEUt));
        tag.setBoolean("wireless", wirelessMode);
        tag.setBoolean("overclock", overclockEnabled);
        tag.setLong("heat", getTotalHeat());
    }

    @Override
    public void getWailaBody(ItemStack itemStack, List<String> currentTip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        super.getWailaBody(itemStack, currentTip, accessor, config);
        NBTTagCompound tag = accessor.getNBTData();
        if (!tag.hasKey("mode")) return;
        int mode = tag.getInteger("mode");
        if (mode == 0) {
            currentTip.add(
                EnumChatFormatting.GOLD
                    + StatCollector
                        .translateToLocalFormatted("machine.computingcenter.waila.output", tag.getLong("availableData"))
                    + EnumChatFormatting.RESET);
            currentTip.add(
                EnumChatFormatting.RED
                    + StatCollector
                        .translateToLocalFormatted("machine.computingcenter.waila.eut", tag.getInteger("eut"))
                    + EnumChatFormatting.RESET);
            if (tag.getBoolean("wireless")) {
                currentTip.add(
                    EnumChatFormatting.LIGHT_PURPLE
                        + StatCollector.translateToLocal("machine.computingcenter.waila.wireless")
                        + EnumChatFormatting.RESET);
            }
            if (tag.getBoolean("overclock")) {
                currentTip.add(
                    EnumChatFormatting.DARK_AQUA
                        + StatCollector
                            .translateToLocalFormatted("machine.computingcenter.waila.heat", tag.getLong("heat"))
                        + EnumChatFormatting.RESET);
            }
        } else {
            long done = tag.getLong("compRequired") - tag.getLong("compRemaining");
            currentTip.add(
                EnumChatFormatting.GREEN + StatCollector.translateToLocalFormatted(
                    "machine.computingcenter.waila.progress",
                    done,
                    tag.getLong("compRequired")) + EnumChatFormatting.RESET);
            currentTip.add(
                EnumChatFormatting.RED
                    + StatCollector
                        .translateToLocalFormatted("machine.computingcenter.waila.eut", tag.getInteger("eut"))
                    + EnumChatFormatting.RESET);
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister aBlockIconRegister) {
        ScreenOFF = Textures.BlockIcons.custom("iconsets/EM_COMPUTER");
        ScreenON = Textures.BlockIcons.custom("iconsets/EM_COMPUTER_ACTIVE");
        super.registerIcons(aBlockIconRegister);
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int colorIndex, boolean aActive, boolean aRedstone) {
        ITexture base = Textures.BlockIcons.getCasingTextureForId(Casings.NanochipMeshInterfaceCasing.textureId);
        if (side == facing) {
            return new ITexture[] { base, new TTRenderedExtendedFacingTexture(aActive ? ScreenON : ScreenOFF) };
        }
        return new ITexture[] { base };
    }

    @Override
    protected boolean isEnablePerfectOverclock() {
        return false;
    }

    @Override
    protected float getSpeedBonus() {
        return 1;
    }

    @Override
    public int getMaxParallelRecipes() {
        return 1;
    }
}
