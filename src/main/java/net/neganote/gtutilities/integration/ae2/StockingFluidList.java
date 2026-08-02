package net.neganote.gtutilities.integration.ae2;

import com.gregtechceu.gtceu.integration.ae2.machine.MEStockingHatchPartMachine;
import com.gregtechceu.gtceu.integration.ae2.slot.ExportOnlyAEFluidList;

import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.GenericStack;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public class StockingFluidList extends ExportOnlyAEFluidList {

    private final MEStockingHatchPartMachine machine;
    private final BooleanSupplier autoPull;

    public StockingFluidList(MEStockingHatchPartMachine machine, int slots, BooleanSupplier autoPull,
                             Supplier<IActionSource> actionSource) {
        super(machine, slots, () -> new StockingFluidSlot(machine, actionSource));
        this.machine = machine;
        this.autoPull = autoPull;
    }

    @Override
    public boolean isAutoPull() {
        return autoPull.getAsBoolean();
    }

    @Override
    public boolean isStocking() {
        return true;
    }

    @Override
    public boolean hasStackInConfig(GenericStack stack, boolean checkExternal) {
        if (super.hasStackInConfig(stack, false)) return true;
        return checkExternal && machine.testConfiguredInOtherPart(stack);
    }
}
