package net.neganote.gtutilities.integration.ae2;

import com.gregtechceu.gtceu.integration.ae2.machine.MEStockingBusPartMachine;
import com.gregtechceu.gtceu.integration.ae2.slot.ExportOnlyAEItemList;
import com.gregtechceu.gtceu.integration.ae2.slot.ExportOnlyAEItemSlot;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

public final class AEStockingSlots {

    private AEStockingSlots() {}

    public static void growConfigSlots(MEStockingBusPartMachine machine, ExportOnlyAEItemList list, int target) {
        ExportOnlyAEItemSlot[] current = list.getInventory();
        if (current.length >= target) return;

        ExportOnlyAEItemSlot[] grown = Arrays.copyOf(current, target);
        for (int i = current.length; i < target; i++) {
            grown[i] = current[0].copy();
            grown[i].setOnContentsChanged(list::onContentsChanged);
        }

        try {
            Field inventoryField = ExportOnlyAEItemList.class.getDeclaredField("inventory");
            inventoryField.setAccessible(true);
            inventoryField.set(list, grown);

            Method bindSlots = list.getClass()
                    .getDeclaredMethod("setStockingBusPartMachine", MEStockingBusPartMachine.class);
            bindSlots.setAccessible(true);
            bindSlots.invoke(list, machine);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("FATAL: Failed to enlarge the stocking bus config slots.", e);
        }
    }
}
