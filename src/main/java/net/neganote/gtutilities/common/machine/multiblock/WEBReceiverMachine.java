package net.neganote.gtutilities.common.machine.multiblock;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.mui.MachineUIPanelBuilder;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.part.MultiblockPartMachine;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic;
import com.gregtechceu.gtceu.api.misc.EnergyContainerList;
import com.gregtechceu.gtceu.api.multiblock.MultiPredicate;
import com.gregtechceu.gtceu.api.multiblock.error.PatternStringError;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.mui.widgets.PopupPanel;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
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
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Supplier;

import static com.gregtechceu.gtceu.api.multiblock.Predicates.abilities;
import static com.gregtechceu.gtceu.utils.GTUtil.doExplosion;
import static net.neganote.gtutilities.common.machine.multiblock.WEBHubMachine.*;

// A lot of this is copied from the Active Transformer
public class WEBReceiverMachine extends WorkableElectricMultiblockMachine {

    private List<MultiblockPartMachine> localPowerOutput;

    @SaveField
    @SyncToClient
    @Getter
    private int frequency;

    public WEBReceiverMachine(BlockEntityCreationInfo info) {
        super(info);
        this.localPowerOutput = new ArrayList<>();

        this.frequency = 0;
    }

    public void explode() {
        removeWirelessEnergy();

        long outputVoltage = 0;

        if (!localPowerOutput.isEmpty()) {
            EnergyContainerList localOutputs = EnergyUtils.getEnergyListFromMultiParts(localPowerOutput);
            outputVoltage = localOutputs.getOutputVoltage();
        }

        long tier = GTUtil.getFloorTierByVoltage(outputVoltage);

        doExplosion(getLevel(), getBlockPos(), 15f + tier);
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
        List<MultiblockPartMachine> localPowerOutput = new ArrayList<>();

        for (MultiblockPartMachine part : getPrioritySortedParts()) {
            for (var handlerList : part.getRecipeHandlers()) {
                var handlerIO = handlerList.getHandlerIO();
                // If IO not compatible
                var energyContainers = handlerList.getCapability(EURecipeCapability.CAP).stream()
                        .filter(IEnergyContainer.class::isInstance)
                        .map(IEnergyContainer.class::cast)
                        .toList();
                if (!energyContainers.isEmpty()) {
                    if (handlerIO == IO.OUT) {
                        localPowerOutput.add(part);
                    }
                }
            }
        }

        // Invalidate the structure if there is not at least one output or one input
        if (localPowerOutput.isEmpty()) {
            this.invalidateStructure();
            getDefaultPatternState().setError(new PatternStringError(Component.literal("No power outputs")));
            return;
        }

        this.localPowerOutput = localPowerOutput;

        if (frequency != 0 && isWorkingEnabled()) {
            addWirelessEnergy();
        }
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
        removeWirelessEnergy();
        if ((isWorkingEnabled() && recipeLogic.getStatus() == RecipeLogic.Status.WORKING) &&
                !ConfigHolder.INSTANCE.machines.harmlessActiveTransformers) {
            explode();
        }
        super.invalidateStructure();
        this.localPowerOutput = new ArrayList<>();
        setWorkingEnabled(false);
    }

    private void removeWirelessEnergy() {
        removeEnergyOutputs(frequency, localPowerOutput);
    }

    private void addWirelessEnergy() {
        addEnergyOutputs(frequency, localPowerOutput);
    }

    public static MultiPredicate getHatchPredicates() {
        return abilities(PartAbility.OUTPUT_ENERGY).setPreviewCount(2)
                .or(abilities(PartAbility.SUBSTATION_OUTPUT_ENERGY).setPreviewCount(1))
                .or(abilities(PartAbility.OUTPUT_LASER).setPreviewCount(1));
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
    }

    @Override
    public List<IWidget> getWidgetsForDisplay(PanelSyncManager syncManager) {
        // TODO: Add more widgets here like e.g. the unformed widget? I'm just
        // copying it 1:1 and not looking at super rn
        List<IWidget> widgets = new ArrayList<>();

        IntSyncValue frequency = syncManager.getOrCreateSyncHandler("frequency", IntSyncValue.class,
                () -> new IntSyncValue(this::getFrequency));
        BooleanSyncValue isWorkingEnabled = syncManager.getOrCreateSyncHandler("isWorkingEnabled",
                BooleanSyncValue.class,
                () -> new BooleanSyncValue(() -> this.getRecipeLogic().isWorkingEnabled()));
        BooleanSyncValue isActive = syncManager.getOrCreateSyncHandler("isActive",
                BooleanSyncValue.class,
                () -> new BooleanSyncValue(() -> this.getRecipeLogic().isActive()));
        LongSyncValue outputTotal = syncManager.getOrCreateSyncHandler("outputTotal",
                LongSyncValue.class,
                () -> new LongSyncValue(() -> {
                    long outputAmperage = 0;
                    long outputVoltage = 0;

                    if (!localPowerOutput.isEmpty()) {
                        EnergyContainerList localOutputs = EnergyUtils.getEnergyListFromMultiParts(localPowerOutput);
                        outputAmperage = localOutputs.getOutputAmperage();
                        outputVoltage = localOutputs.getOutputVoltage();
                    }
                    return outputVoltage * outputAmperage;
                }));

        widgets.add(Text.of(
                Component.translatable("gtmutils.web_machines.invalid_frequency")
                        .withStyle(ChatFormatting.RED))
                .asWidget()
                .setEnabledIf(w -> frequency.getIntValue() == 0));
        widgets.add(Text.of(Component.translatable("gtceu.multiblock.work_paused"))
                .asWidget()
                .setEnabledIf(w -> frequency.getIntValue() != 0 && !isWorkingEnabled.getBoolValue()));
        widgets.add(Text.of(Component.translatable("gtceu.multiblock.idling"))
                .asWidget()
                .setEnabledIf(w -> frequency.getIntValue() != 0 && isWorkingEnabled.getBoolValue() &&
                        !isActive.getBoolValue()));

        // Adding this so I don't have to copy paste it 10x
        Supplier<Boolean> everythingUntilNow = () -> isWorkingEnabled.getBoolValue() &&
                frequency.getIntValue() != 0 && isActive.getBoolValue();

        widgets.add(Text.of(Component.translatable("gtceu.multiblock.running"))
                .asWidget().setEnabledIf(w -> everythingUntilNow.get()));

        widgets.add(Text.dynamic(() -> Component
                .translatable("gtceu.multiblock.active_transformer.max_output",
                        FormattingUtil.formatNumbers(
                                Math.abs(outputTotal.getLongValue()))))
                .asWidget().setEnabledIf(w -> everythingUntilNow.get() && outputTotal.getLongValue() > 0));
        // I should probably make the config values boolean sync handlers so they sync from server,
        // but I cba so now they use the client sided config vals lol
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
