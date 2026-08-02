package net.neganote.gtutilities.integration.ae2.machine;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.common.mui.GTGuiTextures;
import com.gregtechceu.gtceu.integration.ae2.machine.MEPatternBufferPartMachine;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import appeng.api.crafting.IPatternDetails;
import appeng.api.inventories.InternalInventory;
import appeng.crafting.pattern.EncodedPatternItem;
import brachy.modularui.api.drawable.Text;
import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.BooleanSyncValue;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.value.sync.SyncHandlers;
import brachy.modularui.widget.ParentWidget;
import brachy.modularui.widgets.layout.Flow;
import brachy.modularui.widgets.layout.Grid;
import brachy.modularui.widgets.slot.ItemSlot;
import brachy.modularui.widgets.slot.SlotGroup;

import java.lang.reflect.Field;
import java.util.Arrays;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class ExpandedPatternBufferPartMachine extends MEPatternBufferPartMachine {

    protected static final int EXPANDED_MAX_PATTERN_COUNT = 72;
    private static final int PATTERN_COLUMNS = 9;
    private final InternalInventory expandedTerminalInventory = new InternalInventory() {

        @Override
        public int size() {
            return EXPANDED_MAX_PATTERN_COUNT;
        }

        @Override
        public ItemStack getStackInSlot(int slotIndex) {
            return getPatternInventory().getStackInSlot(slotIndex);
        }

        @Override
        public void setItemDirect(int slotIndex, ItemStack stack) {
            getPatternInventory().setStackInSlot(slotIndex, stack);
            getPatternInventory().onContentsChanged(slotIndex);
            onPatternChange(slotIndex);
        }
    };

    public ExpandedPatternBufferPartMachine(BlockEntityCreationInfo info) {
        super(info);
        getPatternInventory().setSize(EXPANDED_MAX_PATTERN_COUNT);
        growInternalInventory();
        growPatternSlotDetails();
    }

    private void growInternalInventory() {
        InternalSlot[] current = getSuperField("internalInventory");
        InternalSlot[] expanded = Arrays.copyOf(current, EXPANDED_MAX_PATTERN_COUNT);
        for (int i = current.length; i < expanded.length; i++) {
            expanded[i] = new InternalSlot();
        }
        setSuperField("internalInventory", expanded);
    }

    private void growPatternSlotDetails() {
        IPatternDetails[] current = getSuperField("patternSlotDetails");
        setSuperField("patternSlotDetails", Arrays.copyOf(current, EXPANDED_MAX_PATTERN_COUNT));
    }

    @SuppressWarnings("unchecked")
    private <T> T getSuperField(String name) {
        try {
            Field field = MEPatternBufferPartMachine.class.getDeclaredField(name);
            field.setAccessible(true);
            return (T) field.get(this);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("FATAL: Failed to read " + name + " of the pattern buffer.", e);
        }
    }

    private void setSuperField(String name, Object value) {
        try {
            Field field = MEPatternBufferPartMachine.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(this, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("FATAL: Failed to expand " + name + " of the pattern buffer.", e);
        }
    }

    @Override
    public InternalInventory getTerminalPatternInventory() {
        return expandedTerminalInventory;
    }

    @Override
    public void buildMainUI(ParentWidget<?> mainWidget, PosGuiData guiData, PanelSyncManager syncManager,
                            UISettings settings) {
        // Mirrors MEPatternBufferPartMachine#buildMainUI, with the pattern grid grown to the expanded slot count.
        // TODO: Potentially copy more stuff from there for better UX
        SlotGroup patternSlotGroup = new SlotGroup("pattern_slots", PATTERN_COLUMNS, 0, true);

        BooleanSyncValue isOnlineValue = new BooleanSyncValue(this::isOnline, this::setOnline);
        syncManager.syncValue("is_online", isOnlineValue);

        var flow = Flow.col().coverChildren();

        flow.child(Text.dynamic(() -> isOnlineValue.getBoolValue() ?
                Component.translatable("gtceu.gui.me_network.online") :
                Component.translatable("gtceu.gui.me_network.offline"))
                .asWidget().marginTop(2).marginBottom(4));

        flow.child(new Grid()
                .height(18 * (EXPANDED_MAX_PATTERN_COUNT / PATTERN_COLUMNS))
                .minElementMargin(0, 0)
                .minColWidth(18).minRowHeight(18)
                .leftRel(0.5f)
                .gridOfSizeWidth(EXPANDED_MAX_PATTERN_COUNT, PATTERN_COLUMNS, (x, y, index) -> new ItemSlot()
                        .slot(SyncHandlers.itemSlot(getPatternInventory(), index)
                                .slotGroup(patternSlotGroup)
                                .accessibility(true, true)
                                .filter(stack -> stack.getItem() instanceof EncodedPatternItem)
                                .changeListener((i, o, c, init) -> onPatternChange(index)))
                        .background(GTGuiTextures.SLOT, GTGuiTextures.PATTERN_OVERLAY)));

        mainWidget.child(flow.center());
    }
}
