package net.neganote.gtutilities.integration.ae2;

import com.gregtechceu.gtceu.integration.ae2.machine.MEStockingHatchPartMachine;
import com.gregtechceu.gtceu.integration.ae2.slot.ExportOnlyAEFluidSlot;
import com.gregtechceu.gtceu.integration.ae2.slot.ExportOnlyAESlot;
import com.gregtechceu.gtceu.integration.ae2.utils.AEUtil;

import net.minecraftforge.fluids.FluidStack;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public class StockingFluidSlot extends ExportOnlyAEFluidSlot {

    private final MEStockingHatchPartMachine machine;
    private final Supplier<IActionSource> actionSource;

    public StockingFluidSlot(MEStockingHatchPartMachine machine, Supplier<IActionSource> actionSource) {
        super();
        this.machine = machine;
        this.actionSource = actionSource;
    }

    public StockingFluidSlot(MEStockingHatchPartMachine machine, Supplier<IActionSource> actionSource,
                             @Nullable GenericStack config, @Nullable GenericStack stock) {
        super(config, stock);
        this.machine = machine;
        this.actionSource = actionSource;
    }

    @Override
    public ExportOnlyAEFluidSlot copy() {
        return new StockingFluidSlot(machine, actionSource,
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
        long extracted = aeNetwork.extract(key, maxDrain, actionable, actionSource.get());
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
