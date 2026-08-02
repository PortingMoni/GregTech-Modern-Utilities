package net.neganote.gtutilities.integration.ae2.machine;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.machine.mui.MachineUIPanelBuilder;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.mui.widgets.PopupPanel;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.integration.ae2.gui.AEConfigWidget;
import com.gregtechceu.gtceu.integration.ae2.machine.MEHatchPartMachine;
import com.gregtechceu.gtceu.integration.ae2.machine.MEStockingHatchPartMachine;
import com.gregtechceu.gtceu.integration.ae2.slot.ExportOnlyAEFluidList;
import com.gregtechceu.gtceu.integration.ae2.slot.ExportOnlyAEFluidSlot;
import com.gregtechceu.gtceu.integration.ae2.slot.ExportOnlyAESlot;
import com.gregtechceu.gtceu.integration.ae2.utils.AEUtil;
import com.gregtechceu.gtceu.utils.ExtendedUseOnContext;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.fluids.FluidStack;
import net.neganote.gtutilities.utils.TagFilter;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import brachy.modularui.api.IPanelHandler;
import brachy.modularui.api.drawable.Text;
import brachy.modularui.api.widget.IWidget;
import brachy.modularui.drawable.ItemDrawable;
import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.RichTooltip;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.BooleanSyncValue;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.value.sync.SyncHandlers;
import brachy.modularui.widget.ParentWidget;
import brachy.modularui.widgets.ButtonWidget;
import brachy.modularui.widgets.layout.Flow;
import brachy.modularui.widgets.textfield.TextFieldWidget;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.function.Predicate;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class METagStockingInputHatchPartMachine extends MEStockingHatchPartMachine {

    protected static final int DECISION_CACHE_LIMIT = 8192;

    protected static final int SLOTS_PER_ROW = 8;
    protected static final int ROW_HEIGHT = 18 * 2 + 2;

    @SaveField
    protected String whitelistExpr = "";
    @SaveField
    protected String blacklistExpr = "";

    protected final TagFilter tagFilter;

    private Predicate<GenericStack> tagAutoPullTest = $ -> true;

    public METagStockingInputHatchPartMachine(BlockEntityCreationInfo info) {
        this(info, DECISION_CACHE_LIMIT);
    }

    protected METagStockingInputHatchPartMachine(BlockEntityCreationInfo info, int decisionCacheLimit) {
        super(info);
        this.tagFilter = new TagFilter(decisionCacheLimit);
    }

    @Override
    protected NotifiableFluidTank createTank(int initialCapacity, int slots) {
        this.aeFluidHandler = new TagStockingFluidList(this, configSlotCount());
        return this.aeFluidHandler;
    }

    protected int configSlotCount() {
        return MEHatchPartMachine.CONFIG_SIZE;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (!isRemote()) {
            disableVanillaAutoPull();
            tagFilter.invalidate();

            if (updateMEStatus()) {
                refreshListFromTags();
                super.syncME();
                updateTankSubscription();
            }
        }
    }

    @Override
    public boolean isAutoPull() {
        return true;
    }

    @Override
    public void setAutoPull(boolean autoPull) {
        super.setAutoPull(false);
    }

    protected void disableVanillaAutoPull() {
        if (super.isAutoPull()) {
            super.setAutoPull(false);
        }
    }

    @Override
    public void autoIO() {
        if (!isRemote()) {
            disableVanillaAutoPull();
        }

        if (getTicksPerCycle() == 0) {
            setTicksPerCycle(ConfigHolder.INSTANCE.compat.ae2.updateIntervals);
        }

        if (getOffsetTimer() % (long) getTicksPerCycle() == 0L) {
            if (!isRemote() && updateMEStatus()) {
                refreshListFromTags();
                super.syncME();
                updateTankSubscription();
            }
        }
    }

    protected boolean isAllowed(AEFluidKey key) {
        tagFilter.update(whitelistExpr, blacklistExpr);
        return tagFilter.isAllowed(key);
    }

    protected void refreshListFromTags() {
        IGrid grid = getMainNode().getGrid();
        if (grid == null) {
            aeFluidHandler.clearInventory(0);
            return;
        }

        MEStorage networkStorage = grid.getStorageService().getInventory();
        var counter = networkStorage.getAvailableStacks();

        final int size = aeFluidHandler.getInventory().length;

        PriorityQueue<Object2LongMap.Entry<AEKey>> top = new PriorityQueue<>(
                Comparator.comparingLong(Object2LongMap.Entry::getLongValue));

        for (Object2LongMap.Entry<AEKey> entry : counter) {
            long amount = entry.getLongValue();
            AEKey what = entry.getKey();

            if (amount <= 0) continue;
            if (!(what instanceof AEFluidKey fluidKey)) continue;
            if (!isAllowed(fluidKey)) continue;

            long request = networkStorage.extract(what, amount, Actionable.SIMULATE, actionSource);
            if (request == 0L) continue;

            if (tagAutoPullTest != null && !tagAutoPullTest.test(new GenericStack(fluidKey, amount))) continue;

            if (top.size() < size) {
                top.offer(entry);
            } else if (amount > Objects.requireNonNull(top.peek()).getLongValue()) {
                top.poll();
                top.offer(entry);
            }
        }

        int fluidAmount = top.size();

        int index;
        for (index = 0; index < size && !top.isEmpty(); index++) {
            Object2LongMap.Entry<AEKey> entry = top.poll();
            AEKey what = entry.getKey();
            long amount = entry.getLongValue();
            long request = networkStorage.extract(what, amount, Actionable.SIMULATE, actionSource);

            ExportOnlyAEFluidSlot slot = aeFluidHandler.getInventory()[fluidAmount - index - 1];
            slot.setConfig(new GenericStack(what, 1L));
            slot.setStock(new GenericStack(what, request));
        }
        aeFluidHandler.clearInventory(index);
    }

    protected void setWhitelistExpr(String expr) {
        this.whitelistExpr = expr;
        onFilterChanged();
    }

    protected void setBlacklistExpr(String expr) {
        this.blacklistExpr = expr;
        onFilterChanged();
    }

    protected void onFilterChanged() {
        if (isRemote()) return;

        tagFilter.invalidate();

        if (updateMEStatus()) {
            refreshListFromTags();
            super.syncME();
            updateTankSubscription();
        }
    }

    @Override
    public void setAutoPullTest(Predicate<GenericStack> autoPullTest) {
        this.tagAutoPullTest = autoPullTest;
        super.setAutoPullTest(autoPullTest);
    }

    @Override
    protected InteractionResult onScrewdriverClick(ExtendedUseOnContext context) {
        if (!isRemote()) {
            context.getPlayer().sendSystemMessage(
                    Component.literal("This hatch is always Tag-Mode (no manual/autoPull toggle)."));
        }
        return InteractionResult.sidedSuccess(isRemote());
    }

    @Override
    protected CompoundTag writeConfigToTag() {
        CompoundTag tag = super.writeConfigToTag();
        tag.putString("WhitelistExpr", whitelistExpr);
        tag.putString("BlacklistExpr", blacklistExpr);
        tag.putBoolean("TagMode", true);
        return tag;
    }

    @Override
    protected void readConfigFromTag(CompoundTag tag) {
        super.readConfigFromTag(tag);
        if (!isRemote()) {
            disableVanillaAutoPull();
        }

        if (tag.contains("WhitelistExpr")) whitelistExpr = tag.getString("WhitelistExpr");
        if (tag.contains("BlacklistExpr")) blacklistExpr = tag.getString("BlacklistExpr");

        onFilterChanged();
    }

    ///////////////////////////////
    // ********** GUI ***********//
    ///////////////////////////////

    @Override
    public MachineUIPanelBuilder getPanelBuilder(PosGuiData data, PanelSyncManager syncManager, UISettings settings) {
        IPanelHandler settingsPanelHandler = syncManager.syncedPanel("stocking_settings", true,
                (sm, sh) -> PopupPanel.createPopupPanel("stocking_settings_panel", 140, 70)
                        .child(Flow.col()
                                .coverChildren()
                                .child(Text.lang("gtceu.gui.me_network.min_stack_size").asWidget())
                                .child(new TextFieldWidget()
                                        .size(120, 18)
                                        .value(SyncHandlers.intNumber(this::getMinStackSize, this::setMinStackSize)
                                                .allowC2S())
                                        .setNumbers(1, Integer.MAX_VALUE))
                                .child(Text.lang("gtceu.gui.me_network.ticks_per_cycle").asWidget())
                                .child(new TextFieldWidget()
                                        .size(120, 18)
                                        .value(SyncHandlers.intNumber(this::getTicksPerCycle, this::setTicksPerCycle)
                                                .allowC2S())
                                        .setNumbers(1, 200))
                                .margin(5)));

        return MachineUIPanelBuilder.panelBuilder(this)
                .rightConfigurators(f -> f.child(new ButtonWidget<>()
                        .size(18)
                        .onMousePressed((context, b) -> {
                            settingsPanelHandler.openPanel();
                            return true;
                        })
                        .overlay(new ItemDrawable(GTItems.TOOL_DATA_STICK.asItem()).asIcon().size(16))
                        .tooltip(new RichTooltip()
                                .addLine(Text.lang("gtceu.gui.me_network.stocking_settings")))));
    }

    @Override
    public void buildMainUI(ParentWidget<?> mainWidget, PosGuiData guiData, PanelSyncManager syncManager,
                            UISettings settings) {
        BooleanSyncValue isOnlineValue = new BooleanSyncValue(this::isOnline, this::setOnline);
        syncManager.syncValue("is_online", isOnlineValue);

        registerConfigActions(syncManager);

        var flow = Flow.col().coverChildren();

        flow.child(Text.dynamic(() -> isOnlineValue.getBoolValue() ?
                Component.translatable("gtceu.gui.me_network.online") :
                Component.translatable("gtceu.gui.me_network.offline"))
                .asWidget().marginTop(2).marginBottom(4));

        flow.child(createFilterFields(syncManager));
        flow.child(createConfigDisplay(syncManager));

        mainWidget.child(flow.center());
    }

    protected Flow createFilterFields(PanelSyncManager syncManager) {
        return Flow.col()
                .coverChildrenHeight()
                .width(SLOTS_PER_ROW * 18)
                .child(new TextFieldWidget()
                        .size(SLOTS_PER_ROW * 18, 18)
                        .marginBottom(2)
                        .hintText(Component.literal("Whitelist tags..."))
                        .setMaxLength(Short.MAX_VALUE)
                        .value(SyncHandlers.string(() -> whitelistExpr, this::setWhitelistExpr).allowC2S()))
                .child(new TextFieldWidget()
                        .size(SLOTS_PER_ROW * 18, 18)
                        .marginBottom(4)
                        .hintText(Component.literal("Blacklist tags..."))
                        .setMaxLength(Short.MAX_VALUE)
                        .value(SyncHandlers.string(() -> blacklistExpr, this::setBlacklistExpr).allowC2S()));
    }

    protected IWidget createConfigDisplay(PanelSyncManager syncManager) {
        int slots = aeFluidHandler.getInventory().length;
        return new AEConfigWidget(aeFluidHandler, slots, true)
                .syncManager(syncManager)
                .size(SLOTS_PER_ROW * 18, (slots / SLOTS_PER_ROW) * ROW_HEIGHT);
    }

    protected static class TagStockingFluidList extends ExportOnlyAEFluidList {

        private final METagStockingInputHatchPartMachine machine;

        public TagStockingFluidList(METagStockingInputHatchPartMachine machine, int slots) {
            super(machine, slots, () -> new TagStockingFluidSlot(machine));
            this.machine = machine;
        }

        @Override
        public boolean isAutoPull() {
            return true;
        }

        @Override
        public boolean isStocking() {
            return true;
        }

        @Override
        public boolean hasStackInConfig(GenericStack stack, boolean checkExternal) {
            boolean inThisHatch = super.hasStackInConfig(stack, false);
            if (inThisHatch) return true;
            return checkExternal && machine.testConfiguredInOtherPart(stack);
        }
    }

    protected static class TagStockingFluidSlot extends ExportOnlyAEFluidSlot {

        private final METagStockingInputHatchPartMachine machine;

        public TagStockingFluidSlot(METagStockingInputHatchPartMachine machine) {
            super();
            this.machine = machine;
        }

        public TagStockingFluidSlot(METagStockingInputHatchPartMachine machine,
                                    @Nullable GenericStack config, @Nullable GenericStack stock) {
            super(config, stock);
            this.machine = machine;
        }

        @Override
        public ExportOnlyAEFluidSlot copy() {
            return new TagStockingFluidSlot(machine,
                    this.config == null ? null : copy(this.config),
                    this.stock == null ? null : copy(this.stock));
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            if (this.stock == null || this.config == null) return FluidStack.EMPTY;
            if (!machine.isOnline()) return FluidStack.EMPTY;

            IGrid grid = machine.getMainNode().getGrid();
            if (grid == null) return FluidStack.EMPTY;

            MEStorage aeNetwork = grid.getStorageService().getInventory();
            Actionable actionable = action.simulate() ? Actionable.SIMULATE : Actionable.MODULATE;
            var key = config.what();
            long extracted = aeNetwork.extract(key, maxDrain, actionable, machine.actionSource);
            if (extracted <= 0) return FluidStack.EMPTY;

            FluidStack resultStack = key instanceof AEFluidKey fluidKey ?
                    AEUtil.toFluidStack(fluidKey, extracted) : FluidStack.EMPTY;
            if (action.execute()) {
                this.stock = ExportOnlyAESlot.copy(stock, stock.amount() - extracted);
                if (this.stock.amount() == 0) {
                    this.stock = null;
                }
                if (this.onContentsChanged != null) {
                    this.onContentsChanged.run();
                }
            }
            return resultStack;
        }
    }
}
