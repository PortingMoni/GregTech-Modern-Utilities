package net.neganote.gtutilities.common.machine.multiblock;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.ConditionalSubscriptionHandler;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.mui.MachineUIPanelBuilder;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.part.MultiblockPartMachine;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic;
import com.gregtechceu.gtceu.api.misc.EnergyContainerList;
import com.gregtechceu.gtceu.api.multiblock.MultiPredicate;
import com.gregtechceu.gtceu.api.multiblock.error.PatternStringError;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.data.GTRecipeCapabilities;
import com.gregtechceu.gtceu.common.machine.multiblock.part.EnergyHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.LaserHatchPartMachine;
import com.gregtechceu.gtceu.common.mui.widgets.PopupPanel;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.neganote.gtutilities.common.materials.UtilMaterials;
import net.neganote.gtutilities.config.UtilConfig;
import net.neganote.gtutilities.utils.EnergyUtils;

import brachy.modularui.api.IPanelHandler;
import brachy.modularui.api.drawable.Text;
import brachy.modularui.api.widget.IWidget;
import brachy.modularui.drawable.ItemDrawable;
import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.BooleanSyncValue;
import brachy.modularui.value.sync.IntSyncValue;
import brachy.modularui.value.sync.LongSyncValue;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.value.sync.StringSyncValue;
import brachy.modularui.widgets.ButtonWidget;
import brachy.modularui.widgets.textfield.TextFieldWidget;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.datafixers.util.Pair;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Supplier;

import static com.gregtechceu.gtceu.api.multiblock.Predicates.abilities;
import static com.gregtechceu.gtceu.utils.GTUtil.doExplosion;

// A lot of this is copied from the Active Transformer
public class WEBHubMachine extends WorkableElectricMultiblockMachine {

    public static Map<Integer, Set<Pair<ResourceLocation, BlockPos>>> ENERGY_INPUTS = new HashMap<>();
    public static Map<Integer, Set<Pair<ResourceLocation, BlockPos>>> ENERGY_OUTPUTS = new HashMap<>();

    public static void addEnergyInputs(int freq, List<MultiblockPartMachine> parts) {
        Set<Pair<ResourceLocation, BlockPos>> inputPairs = ENERGY_INPUTS.computeIfAbsent(freq,
                (f) -> new HashSet<>());
        for (MultiblockPartMachine part : parts) {
            ServerLevel level = (ServerLevel) part.getLevel();
            assert level != null;
            ResourceLocation dimension = level.dimension().location();
            BlockPos pos = part.getBlockPos();
            Pair<ResourceLocation, BlockPos> pair = new Pair<>(dimension, pos);
            inputPairs.add(pair);
        }
        ENERGY_INPUTS.put(freq, inputPairs);
    }

    public static void removeEnergyInputs(int freq, List<MultiblockPartMachine> parts) {
        Set<Pair<ResourceLocation, BlockPos>> inputPairs = ENERGY_INPUTS.computeIfAbsent(freq,
                (f) -> new HashSet<>());
        for (MultiblockPartMachine part : parts) {
            ServerLevel level = (ServerLevel) part.getLevel();
            assert level != null;
            ResourceLocation dimension = level.dimension().location();
            BlockPos pos = part.getBlockPos();
            Pair<ResourceLocation, BlockPos> pair = new Pair<>(dimension, pos);
            inputPairs.remove(pair);
        }
        ENERGY_INPUTS.put(freq, inputPairs);
    }

    public static void addEnergyOutputs(int freq, List<MultiblockPartMachine> parts) {
        Set<Pair<ResourceLocation, BlockPos>> outputPairs = ENERGY_OUTPUTS.computeIfAbsent(freq,
                (f) -> new HashSet<>());
        for (MultiblockPartMachine part : parts) {
            ServerLevel level = (ServerLevel) part.getLevel();
            assert level != null;
            ResourceLocation dimension = level.dimension().location();
            BlockPos pos = part.getBlockPos();
            Pair<ResourceLocation, BlockPos> pair = new Pair<>(dimension, pos);
            outputPairs.add(pair);
        }
        ENERGY_OUTPUTS.put(freq, outputPairs);
    }

    public static void removeEnergyOutputs(int freq, List<MultiblockPartMachine> parts) {
        Set<Pair<ResourceLocation, BlockPos>> outputPairs = ENERGY_OUTPUTS.computeIfAbsent(freq,
                (f) -> new HashSet<>());
        for (MultiblockPartMachine part : parts) {
            ServerLevel level = (ServerLevel) part.getLevel();
            assert level != null;
            ResourceLocation dimension = level.dimension().location();
            BlockPos pos = part.getBlockPos();
            Pair<ResourceLocation, BlockPos> pair = new Pair<>(dimension, pos);
            outputPairs.remove(pair);
        }

        ENERGY_OUTPUTS.put(freq, outputPairs);
    }

    public static EnergyContainerList getWirelessEnergyInputs(int freq, ServerLevel serverLevel) {
        Set<Pair<ResourceLocation, BlockPos>> inputPairs = ENERGY_INPUTS.computeIfAbsent(freq,
                (f) -> new HashSet<>());
        List<IEnergyContainer> energyContainerList = new ArrayList<>();
        for (Pair<ResourceLocation, BlockPos> pair : inputPairs) {
            ServerLevel dimension = serverLevel.getServer()
                    .getLevel(ResourceKey.create(Registries.DIMENSION, pair.getFirst()));
            if (dimension != null) {
                MetaMachine machine = MetaMachine.getMachine(dimension, pair.getSecond());
                if (machine instanceof EnergyHatchPartMachine hatch) {
                    energyContainerList.add(hatch.energyContainer);
                }
                if (machine instanceof LaserHatchPartMachine hatch) {
                    // unfortunately the laser hatch's buffer is private, so I have to do this instead
                    for (var handlerList : hatch.getRecipeHandlers()) {
                        var containers = handlerList.getCapability(EURecipeCapability.CAP).stream()
                                .filter(IEnergyContainer.class::isInstance)
                                .map(IEnergyContainer.class::cast)
                                .toList();
                        energyContainerList.addAll(containers);
                    }
                }
            }
        }
        return new EnergyContainerList(energyContainerList);
    }

    public static EnergyContainerList getWirelessEnergyOutputs(int freq, ServerLevel serverLevel) {
        Set<Pair<ResourceLocation, BlockPos>> outputPairs = ENERGY_OUTPUTS.computeIfAbsent(freq,
                (f) -> new HashSet<>());
        List<IEnergyContainer> energyContainerList = new ArrayList<>();
        for (Pair<ResourceLocation, BlockPos> pair : outputPairs) {
            ServerLevel dimension = serverLevel.getServer()
                    .getLevel(ResourceKey.create(Registries.DIMENSION, pair.getFirst()));
            if (dimension != null) {
                MetaMachine machine = MetaMachine.getMachine(dimension, pair.getSecond());

                if (machine instanceof EnergyHatchPartMachine hatch) {
                    energyContainerList.add(hatch.energyContainer);
                }
                if (machine instanceof LaserHatchPartMachine hatch) {
                    // unfortunately the laser hatch's buffer is private, so I have to do this instead
                    for (var handlerList : hatch.getRecipeHandlers()) {
                        var containers = handlerList.getCapability(EURecipeCapability.CAP).stream()
                                .filter(IEnergyContainer.class::isInstance)
                                .map(IEnergyContainer.class::cast)
                                .toList();
                        energyContainerList.addAll(containers);
                    }
                }
            }
        }
        return new EnergyContainerList(energyContainerList);
    }

    private List<MultiblockPartMachine> localPowerInput;

    protected ConditionalSubscriptionHandler converterSubscription;

    @Getter
    private int coolantDrain;

    @SaveField
    @SyncToClient
    @Getter
    private int frequency;

    @SaveField
    @SyncToClient
    private int coolantTimer = 0;

    public WEBHubMachine(BlockEntityCreationInfo info) {
        super(info);
        this.localPowerInput = new ArrayList<>();

        this.converterSubscription = new ConditionalSubscriptionHandler(this, this::convertEnergyTick,
                this::isSubscriptionActive);

        this.frequency = 0;
    }

    public void explode() {
        removeWirelessEnergy();

        long inputVoltage = 0;

        if (!localPowerInput.isEmpty()) {
            EnergyContainerList localInputs = EnergyUtils.getEnergyListFromMultiParts(localPowerInput);
            inputVoltage = localInputs.getInputVoltage();
        }

        long tier = GTUtil.getFloorTierByVoltage(inputVoltage);

        doExplosion(getLevel(), getBlockPos(), 15f + tier);
    }

    public void convertEnergyTick() {
        if (frequency == 0) {
            getRecipeLogic().setStatus(RecipeLogic.Status.SUSPEND);
            return;
        }
        if (isWorkingEnabled()) {
            getRecipeLogic()
                    .setStatus(isSubscriptionActive() ? RecipeLogic.Status.WORKING : RecipeLogic.Status.SUSPEND);
        }
        if (isWorkingEnabled() && getRecipeLogic().getStatus() == RecipeLogic.Status.WORKING &&
                UtilConfig.coolantEnabled() && coolantTimer == 0 && frequency != 0) {

            var coolantTanks = getCapabilitiesFlat(IO.IN, GTRecipeCapabilities.FLUID).stream()
                    .map(NotifiableFluidTank.class::cast).toList();

            List<FluidIngredient> left = List
                    .of(FluidIngredient.of(UtilMaterials.QuantumCoolant.getFluid(), coolantDrain));

            for (var tank : coolantTanks) {
                left = tank.handleRecipe(IO.IN, null, left, false);
                if (left == null) {
                    break;
                }
            }

            if (left != null && !left.isEmpty()) {
                if (!ConfigHolder.INSTANCE.machines.harmlessActiveTransformers) {
                    explode();
                } else {
                    coolantTimer = 0;
                    getRecipeLogic().setStatus(RecipeLogic.Status.SUSPEND);
                    converterSubscription.updateSubscription();
                    return;
                }
            }
        }
        if (isWorkingEnabled() && getRecipeLogic().getStatus() == RecipeLogic.Status.WORKING) {
            coolantTimer = (coolantTimer + 1) % 20;
        }
        if (isWorkingEnabled() && frequency != 0) {
            if (getLevel() instanceof ServerLevel serverLevel) {
                EnergyContainerList powerInput = getWirelessEnergyInputs(frequency, serverLevel);
                EnergyContainerList powerOutput = getWirelessEnergyOutputs(frequency, serverLevel);
                long canDrain = powerInput.getEnergyStored();
                long totalDrained = powerOutput.changeEnergy(canDrain);
                powerInput.removeEnergy(totalDrained);
            }
        }
        converterSubscription.updateSubscription();
    }

    private int calculateCoolantDrain() {
        long inputAmperage = 0;
        long inputVoltage = 0;

        if (!localPowerInput.isEmpty()) {
            EnergyContainerList localInputs = EnergyUtils.getEnergyListFromMultiParts(localPowerInput);
            inputAmperage = localInputs.getInputAmperage();
            inputVoltage = localInputs.getInputVoltage();
        }

        long scalingFactor = inputAmperage * inputVoltage;

        int coolantDrain = (int) (UtilConfig.INSTANCE.features.webCoolantBaseDrain +
                UtilConfig.INSTANCE.features.webCoolantIOMultiplier * (Math.log(scalingFactor) / Math.log(2.0)));
        if (coolantDrain <= 0) {
            coolantDrain = 1;
        }
        return coolantDrain;
    }

    @SuppressWarnings("RedundantIfStatement") // It is cleaner to have the final return true separate.
    protected boolean isSubscriptionActive() {
        if (!isFormed()) return false;

        if (localPowerInput == null) return false;

        return true;
    }

    @Override
    public boolean onWorking() {
        return super.onWorking();
    }

    @Override
    public void formStructure(@NotNull String substructureName) {
        super.formStructure(substructureName);
        if (frequency == 0) {
            setWorkingEnabled(false);
        }

        // capture all energy containers
        List<MultiblockPartMachine> localPowerInput = new ArrayList<>();

        for (MultiblockPartMachine part : getPrioritySortedParts()) {
            for (var handlerList : part.getRecipeHandlers()) {
                var handlerIO = handlerList.getHandlerIO();
                // If IO not compatible
                var energyContainers = handlerList.getCapability(EURecipeCapability.CAP).stream()
                        .filter(IEnergyContainer.class::isInstance)
                        .map(IEnergyContainer.class::cast)
                        .toList();
                if (!energyContainers.isEmpty()) {
                    if (handlerIO == IO.IN) {
                        localPowerInput.add(part);
                    }
                }
            }
        }

        // Invalidate the structure if there is not at least one output or one input
        if (localPowerInput.isEmpty()) {
            this.invalidateStructure();
            getDefaultPatternState().setError(new PatternStringError(Component.literal("No power inputs")));
            return;
        }

        this.localPowerInput = localPowerInput;

        this.coolantDrain = calculateCoolantDrain();

        if (frequency != 0 && isWorkingEnabled()) {
            addWirelessEnergy();
        }

        converterSubscription.updateSubscription();
    }

    @NotNull
    private List<MultiblockPartMachine> getPrioritySortedParts() {
        return getParts().stream().sorted(Comparator.comparing(part -> {
            Block partBlock = part.getBlockState().getBlock();

            if (PartAbility.OUTPUT_ENERGY.isApplicable(partBlock))
                return 1;

            if (PartAbility.SUBSTATION_OUTPUT_ENERGY.isApplicable(partBlock))
                return 2;

            if (PartAbility.OUTPUT_LASER.isApplicable(partBlock))
                return 3;

            return 4;
        })).toList();
    }

    @Override
    public void invalidateStructure() {
        coolantTimer = 0;
        removeWirelessEnergy();
        if ((isWorkingEnabled() && recipeLogic.getStatus() == RecipeLogic.Status.WORKING) &&
                !ConfigHolder.INSTANCE.machines.harmlessActiveTransformers) {
            explode();
        }
        super.invalidateStructure();
        this.localPowerInput = new ArrayList<>();
        setWorkingEnabled(false);
        converterSubscription.unsubscribe();
    }

    private void removeWirelessEnergy() {
        removeEnergyInputs(frequency, localPowerInput);
    }

    private void addWirelessEnergy() {
        addEnergyInputs(frequency, localPowerInput);
    }

    public static MultiPredicate getHatchPredicates() {
        var predicate = abilities(PartAbility.INPUT_ENERGY).setPreviewCount(1)
                .or(abilities(PartAbility.SUBSTATION_INPUT_ENERGY).setPreviewCount(1))
                .or(abilities(PartAbility.INPUT_LASER).setPreviewCount(1));
        if (UtilConfig.coolantEnabled()) {
            predicate = predicate.or(abilities(PartAbility.IMPORT_FLUIDS).setExactLimit(1));
        }
        return predicate;
    }

    public void setFrequencyFromString(String str) {
        removeWirelessEnergy();
        frequency = Integer.parseInt(str);
        if (frequency == 0) {
            setWorkingEnabled(false);
        }
        if (frequency != 0) {
            addWirelessEnergy();
        }
    }

    public String getFrequencyString() {
        return Integer.valueOf(frequency).toString();
    }

    @Override
    public void setWorkingEnabled(boolean isWorkingAllowed) {
        if (frequency == 0) {
            super.setWorkingEnabled(false);
            return;
        }
        super.setWorkingEnabled(isWorkingAllowed);
        if (frequency != 0) {
            if (isWorkingAllowed) {
                addWirelessEnergy();
            } else {
                removeWirelessEnergy();
            }
        }
        if (!isWorkingAllowed) {
            coolantTimer = 0;
        }
    }

    @Override
    public List<IWidget> getWidgetsForDisplay(PanelSyncManager syncManager) {
        // TODO: Add more widgets here like e.g. the unformed widget? I'm just
        // copying it 1:1 and not looking at super rn
        List<IWidget> widgets = new ArrayList<>();

        BooleanSyncValue isFormed = syncManager.getOrCreateSyncHandler("isFormed", BooleanSyncValue.class,
                () -> new BooleanSyncValue(this::isFormed));
        IntSyncValue frequency = syncManager.getOrCreateSyncHandler("frequency", IntSyncValue.class,
                () -> new IntSyncValue(this::getFrequency));
        BooleanSyncValue isWorkingEnabled = syncManager.getOrCreateSyncHandler("isWorkingEnabled",
                BooleanSyncValue.class,
                () -> new BooleanSyncValue(() -> this.getRecipeLogic().isWorkingEnabled()));
        BooleanSyncValue isActive = syncManager.getOrCreateSyncHandler("isActive",
                BooleanSyncValue.class,
                () -> new BooleanSyncValue(() -> this.getRecipeLogic().isActive()));
        LongSyncValue coolantDrain = syncManager.getOrCreateSyncHandler("coolantDrain",
                LongSyncValue.class,
                () -> new LongSyncValue(this::getCoolantDrain));
        LongSyncValue inputTotal = syncManager.getOrCreateSyncHandler("inputTotal",
                LongSyncValue.class,
                () -> new LongSyncValue(() -> {
                    long inputAmperage = 0;
                    long inputVoltage = 0;

                    if (!localPowerInput.isEmpty()) {
                        EnergyContainerList localInputs = EnergyUtils.getEnergyListFromMultiParts(localPowerInput);
                        inputAmperage = localInputs.getInputAmperage();
                        inputVoltage = localInputs.getInputVoltage();
                    }
                    return inputVoltage * inputAmperage;
                }));

        widgets.add(Text.of(
                Component.translatable("gtmutils.web_machines.invalid_frequency")
                        .withStyle(ChatFormatting.RED))
                .asWidget()
                .setEnabledIf(w -> isFormed.getBoolValue() && frequency.getIntValue() == 0));
        widgets.add(Text.of(Component.translatable("gtceu.multiblock.work_paused"))
                .asWidget()
                .setEnabledIf(w -> isFormed.getBoolValue() && frequency.getIntValue() != 0 &&
                        !isWorkingEnabled.getBoolValue()));
        widgets.add(Text.of(Component.translatable("gtceu.multiblock.idling"))
                .asWidget()
                .setEnabledIf(w -> isFormed.getBoolValue() && frequency.getIntValue() != 0 &&
                        isWorkingEnabled.getBoolValue() && !isActive.getBoolValue()));

        // Adding this so I don't have to copy paste it 10x
        Supplier<Boolean> everythingUntilNow = () -> isFormed.getBoolValue() && isWorkingEnabled.getBoolValue() &&
                frequency.getIntValue() != 0 && isActive.getBoolValue();

        widgets.add(Text.of(Component.translatable("gtceu.multiblock.running"))
                .asWidget().setEnabledIf(w -> everythingUntilNow.get()));

        widgets.add(Text.dynamic(() -> Component
                .translatable("gtceu.multiblock.active_transformer.max_input",
                        FormattingUtil.formatNumbers(
                                Math.abs(inputTotal.getLongValue()))))
                .asWidget().setEnabledIf(w -> everythingUntilNow.get() && inputTotal.getLongValue() > 0));
        // I should probably make the config values boolean sync handlers so they sync from server,
        // but I cba so now they use the client sided config vals lol
        widgets.add(Text.dynamic(() -> Component
                .translatable("gtmutils.multiblock.web_hub_machine.coolant_usage",
                        FormattingUtil.formatNumbers(coolantDrain.getLongValue()),
                        UtilMaterials.QuantumCoolant.getLocalizedName()))
                .asWidget()
                .setEnabledIf(w -> everythingUntilNow.get() && UtilConfig.coolantEnabled()));
        widgets.add(Text.of(Component.translatable("gtceu.multiblock.active_transformer.danger_enabled"))
                .asWidget().setEnabledIf(
                        w -> everythingUntilNow.get() && !ConfigHolder.INSTANCE.machines.harmlessActiveTransformers));
        return widgets;
    }

    @Override
    public MachineUIPanelBuilder getPanelBuilder(PosGuiData data, PanelSyncManager syncManager, UISettings settings) {
        var builder = MachineUIPanelBuilder.panelBuilder(self());
        // Partially taken from the popup panels for pattern buffers
        IPanelHandler wirelessFrequencyPanelHandler = syncManager.syncedPanel("wireless_frequency", true,
                (syncManager1, panelHandler) -> PopupPanel.createPopupPanel("wireless_frequency", 85, 86)
                        .child(Text.lang("gtmutils.gui.web_hub.wireless_configurator.title").asWidget().margin(4))
                        .child(new TextFieldWidget()
                                .value(new StringSyncValue(this::getFrequencyString, this::setFrequencyFromString)
                                        .allowC2S())
                                .top(26)
                                .leftRel(0.5f)));

        builder.rightConfigurators(f -> f.child(new ButtonWidget<>()
                .size(18)
                .onMousePressed((context, b) -> {
                    if (b == InputConstants.MOUSE_BUTTON_LEFT) {
                        wirelessFrequencyPanelHandler.openPanel();
                        return true;
                    }
                    return false;
                })
                .overlay(new ItemDrawable(GTItems.SENSOR_UV.asItem()))));
        return builder;
    }
}
