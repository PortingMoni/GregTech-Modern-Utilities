package net.neganote.gtutilities.integration.ae2.machine;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.integration.ae2.gui.AEConfigWidget;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.neganote.gtutilities.config.UtilConfig;
import net.neganote.gtutilities.integration.ae2.AEStockingSlots;

import brachy.modularui.api.widget.IWidget;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.widget.ScrollWidget;
import brachy.modularui.widget.scroll.VerticalScrollData;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class MEEnlargedTagStockingInputBusPartMachine extends METagStockingInputBusPartMachine {

    private static final int ENLARGED_DECISION_CACHE_LIMIT = 16384;
    private static final int VISIBLE_ROWS = 3;

    private static final int TOTAL_ROWS = Math.min(64, UtilConfig.INSTANCE.features.enlargedStockingSizeRows);
    private static final int TOTAL_SLOTS = SLOTS_PER_ROW * TOTAL_ROWS;

    public MEEnlargedTagStockingInputBusPartMachine(BlockEntityCreationInfo info) {
        super(info, ENLARGED_DECISION_CACHE_LIMIT);
        AEStockingSlots.growConfigSlots(this, aeItemHandler, TOTAL_SLOTS);
    }

    @Override
    protected IWidget createConfigDisplay(PanelSyncManager syncManager) {
        int slots = aeItemHandler.getSlots();
        int rows = Math.max(1, slots / SLOTS_PER_ROW);

        return new ScrollWidget<>(new VerticalScrollData())
                .size(SLOTS_PER_ROW * 18 + 8, Math.min(VISIBLE_ROWS, rows) * ROW_HEIGHT)
                .child(new AEConfigWidget(aeItemHandler, slots, false)
                        .syncManager(syncManager)
                        .size(SLOTS_PER_ROW * 18, rows * ROW_HEIGHT));
    }
}
