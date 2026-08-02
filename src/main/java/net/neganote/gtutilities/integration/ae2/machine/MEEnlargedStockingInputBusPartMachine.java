package net.neganote.gtutilities.integration.ae2.machine;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.integration.ae2.gui.AEConfigWidget;
import com.gregtechceu.gtceu.integration.ae2.machine.MEStockingBusPartMachine;
import com.gregtechceu.gtceu.integration.ae2.slot.ExportOnlyAEItemSlot;
import com.gregtechceu.gtceu.integration.ae2.slot.ExportOnlyAESlot;
import com.gregtechceu.gtceu.utils.ExtendedUseOnContext;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.neganote.gtutilities.config.UtilConfig;
import net.neganote.gtutilities.integration.ae2.AEStockingSlots;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.stacks.AEItemKey;
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
public class MEEnlargedStockingInputBusPartMachine extends MEStockingBusPartMachine {

    private static final int SLOTS_PER_ROW = 8;
    private static final int ROW_HEIGHT = 18 * 2 + 2;
    private static final int VISIBLE_ROWS = 4;

    private static final int TOTAL_ROWS = Math.min(64, UtilConfig.INSTANCE.features.enlargedStockingSizeRows);
    private static final int TOTAL_SLOTS = SLOTS_PER_ROW * TOTAL_ROWS;

    @SaveField
    private boolean enlargedAutoPull = false;

    private Predicate<GenericStack> enlargedAutoPullTest = $ -> false;

    public MEEnlargedStockingInputBusPartMachine(BlockEntityCreationInfo info) {
        super(info);
        AEStockingSlots.growConfigSlots(this, aeItemHandler, TOTAL_SLOTS);
        super.setAutoPull(false);
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
                this.aeItemHandler.clearInventory(0);
            } else if (updateMEStatus()) {
                refreshListEnlarged();
                updateInventorySubscription();
            }
        }

        // Keep the superclass' own auto pull disabled: its refresh only covers the first CONFIG_SIZE slots.
        super.setAutoPull(false);
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
                    updateInventorySubscription();
                }
            }
        }
    }

    private void refreshListEnlarged() {
        IGrid grid = this.getMainNode().getGrid();
        if (grid == null) {
            aeItemHandler.clearInventory(0);
            return;
        }

        MEStorage networkStorage = grid.getStorageService().getInventory();
        var counter = networkStorage.getAvailableStacks();

        final int size = this.aeItemHandler.getSlots();
        final int min = this.getMinStackSize();

        PriorityQueue<Object2LongMap.Entry<AEKey>> topItems = new PriorityQueue<>(
                Comparator.comparingLong(Object2LongMap.Entry<AEKey>::getLongValue));

        for (Object2LongMap.Entry<AEKey> entry : counter) {
            long amount = entry.getLongValue();
            AEKey what = entry.getKey();

            if (amount <= 0) continue;
            if (!(what instanceof AEItemKey itemKey)) continue;

            long request = networkStorage.extract(what, amount, Actionable.SIMULATE, actionSource);
            if (request == 0) continue;

            if (enlargedAutoPullTest != null && !enlargedAutoPullTest.test(new GenericStack(itemKey, amount))) continue;

            if (amount >= min) {
                if (topItems.size() < size) {
                    topItems.offer(entry);
                } else if (amount > topItems.peek().getLongValue()) {
                    topItems.poll();
                    topItems.offer(entry);
                }
            }
        }

        int index;
        int itemAmount = topItems.size();
        for (index = 0; index < size; index++) {
            if (topItems.isEmpty()) break;

            Object2LongMap.Entry<AEKey> entry = topItems.poll();
            AEKey what = entry.getKey();
            long amount = entry.getLongValue();

            long request = networkStorage.extract(what, amount, Actionable.SIMULATE, actionSource);

            ExportOnlyAEItemSlot slot = this.aeItemHandler.getInventory()[itemAmount - index - 1];
            slot.setConfig(new GenericStack(what, 1));
            slot.setStock(new GenericStack(what, request));
        }

        aeItemHandler.clearInventory(index);
    }

    @Override
    public void setAutoPullTest(Predicate<GenericStack> autoPullTest) {
        this.enlargedAutoPullTest = autoPullTest;
        super.setAutoPullTest(autoPullTest);
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

        int slots = aeItemHandler.getSlots();
        int rows = Math.max(1, slots / SLOTS_PER_ROW);

        flow.child(new ScrollWidget<>(new VerticalScrollData())
                .size(SLOTS_PER_ROW * 18 + 8, Math.min(VISIBLE_ROWS, rows) * ROW_HEIGHT)
                .child(new AEConfigWidget(aeItemHandler, slots, false)
                        .syncManager(syncManager)
                        .size(SLOTS_PER_ROW * 18, rows * ROW_HEIGHT)));

        mainWidget.child(flow.center());
    }

    @Override
    protected void registerConfigActions(PanelSyncManager syncManager) {
        // Mostly copies but with the getSlots enlarged
        syncManager.registerServerSyncedAction("ae_config_set", packet -> {
            int index = packet.readVarInt();
            if (index < 0 || index >= aeItemHandler.getSlots()) return;
            var slot = aeItemHandler.getInventory()[index];
            ItemStack held = syncManager.getPlayer().containerMenu.getCarried();
            if (!held.isEmpty()) {
                slot.setConfig(GenericStack.fromItemStack(held));
            }
        });

        syncManager.registerServerSyncedAction("ae_config_clear", packet -> {
            int index = packet.readVarInt();
            if (index < 0 || index >= aeItemHandler.getSlots()) return;
            aeItemHandler.getInventory()[index].setConfig(null);
        });

        syncManager.registerServerSyncedAction("ae_config_amount", packet -> {
            int index = packet.readVarInt();
            long amount = packet.readVarLong();
            if (index < 0 || index >= aeItemHandler.getSlots()) return;
            var slot = aeItemHandler.getInventory()[index];
            if (slot.getConfig() != null && amount > 0) {
                slot.setConfig(new GenericStack(slot.getConfig().what(), amount));
            }
        });

        syncManager.registerServerSyncedAction("ae_stock_pickup", packet -> {
            int index = packet.readVarInt();
            if (index < 0 || index >= aeItemHandler.getSlots()) return;
            var slot = aeItemHandler.getInventory()[index];
            if (slot.getStock() != null && slot.getStock().what() instanceof AEItemKey key) {
                var player = syncManager.getPlayer();
                if (!player.containerMenu.getCarried().isEmpty()) return;
                ItemStack stack = new ItemStack(key.getItem());
                stack.setCount(Math.min((int) slot.getStock().amount(), stack.getMaxStackSize()));
                if (key.hasTag()) stack.setTag(key.getTag().copy());
                player.containerMenu.setCarried(stack);
                GenericStack remaining = ExportOnlyAESlot.copy(slot.getStock(),
                        Math.max(0, slot.getStock().amount() - stack.getCount()));
                slot.setStock(remaining.amount() == 0 ? null : remaining);
            }
        });

        syncManager.registerServerSyncedAction("ae_config_set_ghost", packet -> {
            int index = packet.readVarInt();
            if (index < 0 || index >= aeItemHandler.getSlots()) return;
            boolean isFluid = packet.readBoolean();
            if (!isFluid) {
                ItemStack item = packet.readItem();
                if (!item.isEmpty()) {
                    aeItemHandler.getInventory()[index].setConfig(GenericStack.fromItemStack(item));
                }
            }
        });
    }
}
