package net.neganote.gtutilities.common.machine.singleblock;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.*;
import com.gregtechceu.gtceu.api.capability.compat.FeCompat;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.TieredEnergyMachine;
import com.gregtechceu.gtceu.api.machine.feature.IMuiMachine;
import com.gregtechceu.gtceu.api.machine.mui.MachineUIPanel;
import com.gregtechceu.gtceu.api.machine.property.GTMachineModelProperties;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableEnergyContainer;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.sync_system.annotations.RerenderOnChanged;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.common.machine.electric.BatteryBufferMachine;
import com.gregtechceu.gtceu.common.machine.trait.AutoOutputTrait;
import com.gregtechceu.gtceu.common.mui.GTGuiTextures;
import com.gregtechceu.gtceu.common.mui.GTMuiMachineUtil;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.gregtechceu.gtceu.utils.GTTransferUtils;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.IItemHandler;

import brachy.modularui.drawable.progress.ProgressDrawable;
import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.RichTooltip;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.DoubleSyncValue;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.widget.ParentWidget;
import brachy.modularui.widgets.ProgressWidget;
import brachy.modularui.widgets.layout.Flow;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

import static com.gregtechceu.gtceu.utils.GTUtil.doExplosion;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class AutoChargerMachine extends TieredEnergyMachine
                                implements IMuiMachine {

    public static final long AMPS_PER_ITEM = 4L;
    private final int inventorySize;

    @SaveField
    private boolean isWorkingEnabled = true;

    @SaveField
    protected final NotifiableItemStackHandler chargerInventory;

    @SaveField
    @SyncToClient
    protected final ChargedItemAutoOutputTrait autoOutput;

    @SyncToClient
    @RerenderOnChanged
    private BatteryBufferMachine.State state = BatteryBufferMachine.State.IDLE;

    public AutoChargerMachine(BlockEntityCreationInfo info, int tier, int inventorySize) {
        super(info, tier, new EnergyBatteryTrait(inventorySize, tier));

        this.chargerInventory = attachTrait(new NotifiableItemStackHandler(inventorySize, IO.BOTH));
        this.chargerInventory.setFilter(stack -> GTCapabilityHelper.getElectricItem(stack) != null ||
                (ConfigHolder.INSTANCE.compat.energy.nativeEUToFE &&
                        GTCapabilityHelper.getForgeEnergyItem(stack) != null));
        this.inventorySize = inventorySize;

        this.autoOutput = attachTrait(new ChargedItemAutoOutputTrait(this.chargerInventory));
    }

    private void changeState(BatteryBufferMachine.State newState) {
        if (this.state != newState) {
            this.state = newState;
            getSyncDataHolder().markClientSyncFieldDirty("state");
            setRenderState(getRenderState().setValue(GTMachineModelProperties.CHARGER_STATE, newState));
        }
    }

    @Override
    public void onMachineDestroyed() {
        super.onMachineDestroyed();
        this.chargerInventory.dropInventoryInWorld();
    }

    public static boolean isFullyCharged(ItemStack stack) {
        var elec = GTCapabilityHelper.getElectricItem(stack);
        if (elec != null) {
            if (elec.getCharge() >= elec.getMaxCharge()) {
                return true;
            }
        }
        var fe = GTCapabilityHelper.getForgeEnergyItem(stack);
        if (fe != null) {
            if (fe.getEnergyStored() >= fe.getMaxEnergyStored()) {
                return true;
            }
        }
        return false;
    }

    private void getRichTooltip(RichTooltip r) {
        if (GTUtil.isShiftDown()) {
            r.addLine(Component.literal(
                    "%s/%s EU".formatted(
                            energyContainer.getEnergyStored(), energyContainer.getEnergyCapacity())));
        } else {
            r.addLine(Component.literal(
                    "%s/%s EU".formatted(
                            FormattingUtil.formatNumberReadable(energyContainer.getEnergyStored()),
                            FormattingUtil.formatNumberReadable(energyContainer.getEnergyCapacity()))));
        }
    }

    @Override
    public void buildMainUI(ParentWidget<?> mainWidget, PosGuiData guiData, PanelSyncManager syncManager,
                            UISettings settings) {
        // Mostly copied from BatteryBufferMachine.buildMainUI
        String[] matrix;
        if (inventorySize == 8) matrix = new String[] { "BBBB", "BBBB" };
        else matrix = GTMuiMachineUtil.createSquareMatrix(inventorySize, 'B');

        DoubleSyncValue energyPercentage = syncManager.getOrCreateSyncHandler("energyPercentage", DoubleSyncValue.class,
                () -> new DoubleSyncValue(() -> (double) this.energyContainer.getEnergyStored() /
                        this.energyContainer.getEnergyCapacity()));

        var flow = Flow.row().width(MachineUIPanel.DEFAULT_CONTENT_WIDTH).height(90);

        flow.child(new ProgressWidget()
                .texture(GTGuiTextures.PROGRESS_BAR_BOILER_EMPTY_STEEL,
                        GTGuiTextures.PROGRESS_BAR_BOILER_HEAT, ProgressDrawable.Direction.UP)
                .value(energyPercentage)
                .marginLeft(5)
                .size(18, 60)
                .tooltipDynamic(this::getRichTooltip))
                .tooltipAutoUpdate(true)
                .child(GTMuiMachineUtil.createSlotGroupFromInventory(
                        chargerInventory, "batteries",
                        inventorySize, 'B',
                        slot -> slot.background(GTGuiTextures.SLOT, GTGuiTextures.CHARGER_OVERLAY),
                        syncManager,
                        matrix)
                        .center());

        mainWidget.child(flow);
    }

    protected static class EnergyBatteryTrait extends NotifiableEnergyContainer {

        protected EnergyBatteryTrait(int invSize, int tier) {
            super(
                    GTValues.V[tier] * invSize * 32L,
                    GTValues.V[tier],
                    invSize * AMPS_PER_ITEM,
                    0L,
                    0L);
            setSideInputCondition(side -> getChargerMachine().isWorkingEnabled);
            setSideOutputCondition(side -> false);
        }

        private AutoChargerMachine getChargerMachine() {
            return (AutoChargerMachine) getMachine();
        }

        private List<Object> getNonFullElectricItem() {
            List<Object> electricItems = new ArrayList<>();
            for (int i = 0; i < getChargerMachine().chargerInventory.getSlots(); i++) {
                var electricItemStack = getChargerMachine().chargerInventory.getStackInSlot(i);
                var electricItem = GTCapabilityHelper.getElectricItem(electricItemStack);
                if (electricItem != null) {
                    if (electricItem.getCharge() < electricItem.getMaxCharge()) {
                        electricItems.add(electricItem);
                    }
                } else if (ConfigHolder.INSTANCE.compat.energy.nativeEUToFE) {
                    var energyStorage = GTCapabilityHelper.getForgeEnergyItem(electricItemStack);
                    if (energyStorage != null) {
                        if (energyStorage.getEnergyStored() < energyStorage.getMaxEnergyStored()) {
                            electricItems.add(energyStorage);
                        }
                    }
                }
            }
            return electricItems;
        }

        private void changeState(BatteryBufferMachine.State newState) {
            getChargerMachine().changeState(newState);
        }

        @Override
        public long acceptEnergyFromNetwork(@Nullable Direction side, long voltage, long amperage) {
            var latestTimeStamp = getMachine().getOffsetTimer();
            if (lastTimeStamp < latestTimeStamp) {
                amps = 0;
                lastTimeStamp = latestTimeStamp;
            }
            if (amperage <= 0 || voltage <= 0) {
                changeState(BatteryBufferMachine.State.IDLE);
                return 0;
            }

            var electricItems = getNonFullElectricItem();
            var maxAmps = electricItems.size() * AMPS_PER_ITEM - amps;
            var usedAmps = Math.min(maxAmps, amperage);
            if (maxAmps <= 0) {
                return 0;
            }

            if (side == null || inputsEnergy(side)) {
                if (voltage > getInputVoltage()) {
                    doExplosion(getChargerMachine().getLevel(), getChargerMachine().getBlockPos(),
                            GTUtil.getExplosionPower(voltage));
                    return usedAmps;
                }

                long internalAmps = Math.min(maxAmps, Math.max(0, getInternalStorage() / voltage));

                usedAmps = Math.min(usedAmps, maxAmps - internalAmps);
                amps += usedAmps;

                long energy = (usedAmps + internalAmps) * voltage;
                long distributed = energy / electricItems.size();

                boolean changed = false;
                for (var electricItem : electricItems) {
                    long charged = 0;
                    if (electricItem instanceof IElectricItem item) {
                        charged = item.charge(Math.min(distributed, GTValues.V[item.getTier()] * AMPS_PER_ITEM),
                                getChargerMachine().getTier(), true, false);
                    } else if (electricItem instanceof IEnergyStorage energyStorage) {
                        charged = FeCompat.insertEu(energyStorage,
                                Math.min(distributed, GTValues.V[getChargerMachine().getTier()] * AMPS_PER_ITEM),
                                false);
                    }
                    if (charged > 0) {
                        changed = true;
                    }
                    energy -= charged;
                    energyInputPerSec += charged;
                }

                if (changed) {
                    markAsChanged();
                    changeState(BatteryBufferMachine.State.RUNNING);
                }

                setEnergyStored(getInternalStorage() - internalAmps * voltage + energy);
                return usedAmps;
            }
            return 0;
        }

        @Override
        public long getEnergyCapacity() {
            long energyCapacity = 0L;
            for (int i = 0; i < getChargerMachine().chargerInventory.getSlots(); i++) {
                var electricItemStack = getChargerMachine().chargerInventory.getStackInSlot(i);
                var electricItem = GTCapabilityHelper.getElectricItem(electricItemStack);
                if (electricItem != null) {
                    energyCapacity += electricItem.getMaxCharge();
                } else if (ConfigHolder.INSTANCE.compat.energy.nativeEUToFE) {
                    var energyStorage = GTCapabilityHelper.getForgeEnergyItem(electricItemStack);
                    if (energyStorage != null) {
                        energyCapacity += FeCompat.toEu(energyStorage.getMaxEnergyStored(),
                                FeCompat.ratio(false));
                    }
                }
            }

            if (energyCapacity == 0) {
                changeState(BatteryBufferMachine.State.IDLE);
            }

            return energyCapacity;
        }

        @Override
        public long getEnergyStored() {
            long energyStored = 0L;
            for (int i = 0; i < getChargerMachine().chargerInventory.getSlots(); i++) {
                var electricItemStack = getChargerMachine().chargerInventory.getStackInSlot(i);
                var electricItem = GTCapabilityHelper.getElectricItem(electricItemStack);
                if (electricItem != null) {
                    energyStored += electricItem.getCharge();
                } else if (ConfigHolder.INSTANCE.compat.energy.nativeEUToFE) {
                    var energyStorage = GTCapabilityHelper.getForgeEnergyItem(electricItemStack);
                    if (energyStorage != null) {
                        energyStored += FeCompat.toEu(energyStorage.getEnergyStored(),
                                FeCompat.ratio(false));
                    }
                }
            }

            var capacity = getEnergyCapacity();

            if (capacity != 0 && capacity == energyStored) {
                changeState(BatteryBufferMachine.State.FINISHED);
            }

            return energyStored;
        }

        private long getInternalStorage() {
            return energyStored;
        }
    }

    public static class ChargedItemAutoOutputTrait extends AutoOutputTrait {

        public ChargedItemAutoOutputTrait(IItemHandler chargerInventory) {
            super(List.of(chargerInventory), List.of());
        }

        @Override
        protected List<Class<?>> validMachineClasses() {
            return List.of(AutoChargerMachine.class);
        }

        @Override
        protected void autoOutputItems() {
            var direction = getItemOutputDirection();
            if (getMachine().getOffsetTimer() % getTicksPerCycle() == 0 && direction != null) {
                var filter = getMachine().getItemCapFilter(direction, IO.OUT).and(AutoChargerMachine::isFullyCharged);
                GTTransferUtils.getAdjacentItemHandler(getLevel(), getBlockPos(), direction)
                        .ifPresent(adjacent -> {
                            for (var handler : itemHandlers) {
                                GTTransferUtils.transferItemsFiltered(handler, adjacent, filter);
                            }
                        });
            }
            updateItemOutputSubscription();
        }
    }
}
