package net.neganote.gtutilities.integration.ae2.machine;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.integration.ae2.gui.AEConfigWidget;
import com.gregtechceu.gtceu.integration.ae2.machine.MEStockingHatchPartMachine;
import com.gregtechceu.gtceu.integration.ae2.slot.ExportOnlyAESlot;
import com.gregtechceu.gtceu.integration.ae2.utils.AEUtil;
import com.gregtechceu.gtceu.utils.ExtendedUseOnContext;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.neganote.gtutilities.config.UtilConfig;
import net.neganote.gtutilities.integration.ae2.StockingFluidList;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import brachy.modularui.api.drawable.Text;
import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.BooleanSyncValue;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.widget.ParentWidget;
import brachy.modularui.widget.ScrollWidget;
import brachy.modularui.widget.scroll.VerticalScrollData;
import brachy.modularui.widgets.layout.Flow;
import it.unimi.dsi.fastutil.objects.Object2LongMap;

import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.function.Predicate;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class MEEnlargedStockingInputHatchPartMachine extends MEStockingHatchPartMachine {

    private static final int SLOTS_PER_ROW = 8;
    private static final int ROW_HEIGHT = 18 * 2 + 2;
    private static final int VISIBLE_ROWS = 4;

    private static final int TOTAL_ROWS = Math.min(64, UtilConfig.INSTANCE.features.enlargedStockingSizeRows);
    private static final int TOTAL_SLOTS = SLOTS_PER_ROW * TOTAL_ROWS;

    @SaveField
    private boolean enlargedAutoPull = false;

    private Predicate<GenericStack> enlargedAutoPullTest = $ -> false;

    public MEEnlargedStockingInputHatchPartMachine(BlockEntityCreationInfo info) {
        super(info);
        super.setAutoPull(false);
    }

    @Override
    protected NotifiableFluidTank createTank(int initialCapacity, int slots) {
        this.aeFluidHandler = new StockingFluidList(this, TOTAL_SLOTS, this::isAutoPull, () -> actionSource);
        return this.aeFluidHandler;
    }

    @Override
    public boolean isAutoPull() {
        return enlargedAutoPull;
    }

    @Override
    public void setAutoPull(boolean autoPull) {
        this.enlargedAutoPull = autoPull;

        if (!isRemote()) {
            if (!this.enlargedAutoPull) {
                this.aeFluidHandler.clearInventory(0);
            } else if (updateMEStatus()) {
                refreshListEnlarged();
                updateTankSubscription();
            }
        }

        super.setAutoPull(false);
    }

    @Override
    public void setAutoPullTest(Predicate<GenericStack> autoPullTest) {
        this.enlargedAutoPullTest = autoPullTest;
        super.setAutoPullTest(autoPullTest);
    }

    @Override
    protected InteractionResult onScrewdriverClick(ExtendedUseOnContext context) {
        if (!isRemote()) {
            setAutoPull(!isAutoPull());
            if (isAutoPull()) {
                context.getPlayer().sendSystemMessage(
                        Component.translatable("gtceu.machine.me.stocking_auto_pull_enabled"));
            } else {
                context.getPlayer().sendSystemMessage(
                        Component.translatable("gtceu.machine.me.stocking_auto_pull_disabled"));
            }
        }
        return InteractionResult.sidedSuccess(isRemote());
    }

    @Override
    public void autoIO() {
        super.autoIO();

        if (getTicksPerCycle() == 0) {
            setTicksPerCycle(ConfigHolder.INSTANCE.compat.ae2.updateIntervals);
        }

        if (getOffsetTimer() % (long) getTicksPerCycle() == 0L) {
            if (!isRemote() && enlargedAutoPull) {
                if (updateMEStatus()) {
                    refreshListEnlarged();
                    super.syncME();
                    updateTankSubscription();
                }
            }
        }
    }

    private void refreshListEnlarged() {
        IGrid grid = getMainNode().getGrid();
        if (grid == null) {
            aeFluidHandler.clearInventory(0);
            return;
        }

        MEStorage networkStorage = grid.getStorageService().getInventory();
        var counter = networkStorage.getAvailableStacks();

        final int size = aeFluidHandler.getInventory().length;
        final int min = getMinStackSize();

        PriorityQueue<Object2LongMap.Entry<AEKey>> topFluids = new PriorityQueue<>(
                Comparator.comparingLong(Object2LongMap.Entry<AEKey>::getLongValue));

        for (Object2LongMap.Entry<AEKey> entry : counter) {
            long amount = entry.getLongValue();
            AEKey what = entry.getKey();

            if (amount <= 0) continue;
            if (!(what instanceof AEFluidKey fluidKey)) continue;

            long request = networkStorage.extract(what, amount, Actionable.SIMULATE, actionSource);
            if (request == 0) continue;

            if (enlargedAutoPullTest != null && !enlargedAutoPullTest.test(new GenericStack(fluidKey, amount)))
                continue;

            if (amount >= min) {
                if (topFluids.size() < size) {
                    topFluids.offer(entry);
                } else if (amount > topFluids.peek().getLongValue()) {
                    topFluids.poll();
                    topFluids.offer(entry);
                }
            }
        }

        int index;
        int fluidAmount = topFluids.size();
        for (index = 0; index < size; index++) {
            if (topFluids.isEmpty()) break;

            Object2LongMap.Entry<AEKey> entry = topFluids.poll();
            AEKey what = entry.getKey();
            long amount = entry.getLongValue();

            long request = networkStorage.extract(what, amount, Actionable.SIMULATE, actionSource);

            var slot = aeFluidHandler.getInventory()[fluidAmount - index - 1];
            slot.setConfig(new GenericStack(what, 1));
            slot.setStock(new GenericStack(what, request));
        }

        aeFluidHandler.clearInventory(index);
    }

    @Override
    protected CompoundTag writeConfigToTag() {
        if (!enlargedAutoPull) {
            CompoundTag tag = super.writeConfigToTag();
            tag.putBoolean("AutoPull", false);
            return tag;
        }
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("AutoPull", true);
        tag.putByte("GhostCircuit", (byte) circuitSlot.getCurrentCircuit());
        return tag;
    }

    @Override
    protected void readConfigFromTag(CompoundTag tag) {
        if (tag.getBoolean("AutoPull")) {
            setAutoPull(true);
            circuitSlot.setCurrentCircuit(tag.getByte("GhostCircuit"));
            return;
        }
        setAutoPull(false);
        super.readConfigFromTag(tag);
    }

    ///////////////////////////////
    // ********** GUI ***********//
    ///////////////////////////////

    @Override
    public void buildMainUI(ParentWidget<?> mainWidget, PosGuiData guiData, PanelSyncManager syncManager,
                            UISettings settings) {
        BooleanSyncValue isOnlineValue = new BooleanSyncValue(this::isOnline, this::setOnline);
        syncManager.syncValue("is_online", isOnlineValue);

        syncManager.syncValue("auto_pull", new BooleanSyncValue(this::isAutoPull, this::setAutoPull));

        registerConfigActions(syncManager);

        var flow = Flow.col().coverChildren();

        flow.child(Text.dynamic(() -> isOnlineValue.getBoolValue() ?
                Component.translatable("gtceu.gui.me_network.online") :
                Component.translatable("gtceu.gui.me_network.offline"))
                .asWidget().marginTop(2).marginBottom(4));

        int slots = aeFluidHandler.getInventory().length;
        int rows = Math.max(1, slots / SLOTS_PER_ROW);

        flow.child(new ScrollWidget<>(new VerticalScrollData())
                .size(SLOTS_PER_ROW * 18 + 8, Math.min(VISIBLE_ROWS, rows) * ROW_HEIGHT)
                .child(new AEConfigWidget(aeFluidHandler, slots, true)
                        .syncManager(syncManager)
                        .size(SLOTS_PER_ROW * 18, rows * ROW_HEIGHT)));

        mainWidget.child(flow.center());
    }

    @Override
    protected void registerConfigActions(PanelSyncManager syncManager) {
        syncManager.registerServerSyncedAction("ae_config_set", packet -> {
            int index = packet.readVarInt();
            if (index < 0 || index >= aeFluidHandler.getInventory().length) return;
            var slot = aeFluidHandler.getInventory()[index];
            ItemStack held = syncManager.getPlayer().containerMenu.getCarried();
            FluidUtil.getFluidContained(held).ifPresent(fluid -> slot.setConfig(AEUtil.fromFluidStack(fluid)));
        });

        syncManager.registerServerSyncedAction("ae_config_clear", packet -> {
            int index = packet.readVarInt();
            if (index < 0 || index >= aeFluidHandler.getInventory().length) return;
            aeFluidHandler.getInventory()[index].setConfig(null);
        });

        syncManager.registerServerSyncedAction("ae_config_amount", packet -> {
            int index = packet.readVarInt();
            long amount = packet.readVarLong();
            if (index < 0 || index >= aeFluidHandler.getInventory().length) return;
            var slot = aeFluidHandler.getInventory()[index];
            if (slot.getConfig() != null && amount > 0) {
                slot.setConfig(ExportOnlyAESlot.copy(slot.getConfig(), amount));
            }
        });

        syncManager.registerServerSyncedAction("ae_config_set_ghost", packet -> {
            int index = packet.readVarInt();
            if (index < 0 || index >= aeFluidHandler.getInventory().length) return;
            boolean isFluidGhost = packet.readBoolean();
            if (isFluidGhost) {
                FluidStack fluid = FluidStack.readFromPacket(packet);
                if (!fluid.isEmpty()) {
                    aeFluidHandler.getInventory()[index].setConfig(AEUtil.fromFluidStack(fluid));
                }
            }
        });
    }
}
