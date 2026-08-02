package net.neganote.gtutilities.common.machine;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.compat.FeCompat;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.CleanroomType;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.machine.property.GTMachineModelProperties;
import com.gregtechceu.gtceu.api.multiblock.pattern.MultiblockPatternBuilder;
import com.gregtechceu.gtceu.api.multiblock.util.RelativeDirection;
import com.gregtechceu.gtceu.api.registry.registrate.MachineBuilder;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.data.machines.GTMachineUtils;
import com.gregtechceu.gtceu.common.data.models.GTMachineModels;
import com.gregtechceu.gtceu.common.machine.electric.BatteryBufferMachine;
import com.gregtechceu.gtceu.common.machine.electric.ConverterMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.CleaningMaintenanceHatchPartMachine;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.utils.FormattingUtil;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neganote.gtutilities.GregTechModernUtilities;
import net.neganote.gtutilities.common.machine.multiblock.WEBHubMachine;
import net.neganote.gtutilities.common.machine.multiblock.WEBReceiverMachine;
import net.neganote.gtutilities.common.machine.singleblock.AutoChargerMachine;
import net.neganote.gtutilities.common.materials.UtilMaterials;
import net.neganote.gtutilities.config.UtilConfig;

import java.util.Locale;
import java.util.function.BiFunction;

import static com.gregtechceu.gtceu.api.GTValues.*;
import static com.gregtechceu.gtceu.api.GTValues.V;
import static com.gregtechceu.gtceu.api.multiblock.Predicates.*;
import static com.gregtechceu.gtceu.common.data.GTBlocks.*;
import static net.neganote.gtutilities.GregTechModernUtilities.REGISTRATE;

@SuppressWarnings("unused")
public class UtilMachines {

    static {
        REGISTRATE.creativeModeTab(() -> GregTechModernUtilities.UTIL_CREATIVE_TAB);
    }

    // Edited slightly from GTMachines
    public static MachineDefinition STERILE_CLEANING_MAINTENANCE_HATCH = null;

    static {
        if (UtilConfig.INSTANCE.features.sterileHatchEnabled || GTCEu.isDataGen()) {
            STERILE_CLEANING_MAINTENANCE_HATCH = REGISTRATE
                    .machine("sterile_cleaning_maintenance_hatch",
                            holder -> new CleaningMaintenanceHatchPartMachine(holder, CleanroomType.STERILE_CLEANROOM))
                    .langValue("Sterile Cleaning Maintenance Hatch")
                    .rotationState(RotationState.ALL)
                    .abilities(PartAbility.MAINTENANCE)
                    .tooltips(Component.translatable("gtceu.part_sharing.disabled"),
                            Component.translatable("gtceu.machine.maintenance_hatch_cleanroom_auto.tooltip.0"),
                            Component.translatable("gtceu.machine.maintenance_hatch_cleanroom_auto.tooltip.1"))
                    .tooltipBuilder((stack, tooltips) -> tooltips.add(Component.literal("  ").append(Component
                            .translatable(CleanroomType.STERILE_CLEANROOM.getTranslationKey())
                            .withStyle(ChatFormatting.GREEN))))
                    .tier(UHV)
                    .modelProperty(GTMachineModelProperties.IS_FORMED, false)
                    .overlayTieredHullModel(
                            GregTechModernUtilities.id("block/machine/part/sterile_cleaning_maintenance_hatch"))
                    // Tier can always be changed later
                    .register();
        }
    }

    public static MachineDefinition[] AUTO_CHARGER_4 = null;

    static {
        if (UtilConfig.INSTANCE.features.autoChargersEnabled || GTCEu.isDataGen()) {
            AUTO_CHARGER_4 = registerCharger(4);
        }
    }

    public static MachineDefinition[] registerCharger(int itemSlotSize) {
        int maxTier;
        if (GTCEuAPI.isHighTier()) {
            maxTier = OpV;
        } else {
            maxTier = UHV;
        }

        return registerTieredMachines("auto_charger_" + itemSlotSize + "x",
                (info, tier) -> new AutoChargerMachine(info, tier, itemSlotSize),
                (tier, builder) -> builder
                        .rotationState(RotationState.ALL)
                        .modelProperty(GTMachineModelProperties.CHARGER_STATE, BatteryBufferMachine.State.IDLE)
                        .model(GTMachineModels.createChargerModel())
                        .langValue("%s %sx Auto Turbo Charger".formatted(
                                VCF[tier] + VOLTAGE_NAMES[tier] + ChatFormatting.RESET,
                                itemSlotSize))
                        .tooltips(Component.translatable("gtceu.universal.tooltip.item_storage_capacity", itemSlotSize),
                                Component.translatable("gtceu.universal.tooltip.voltage_in_out",
                                        FormattingUtil.formatNumbers(GTValues.V[tier]),
                                        GTValues.VNF[tier]),
                                Component.translatable("gtceu.universal.tooltip.amperage_in_till",
                                        itemSlotSize * BatteryBufferMachine.AMPS_PER_BATTERY_CHARGER))
                        .register(),
                GTValues.tiersBetween(ULV, maxTier));
    }

    // Copied from GTMachineUtils
    public static MachineDefinition[] registerConverter(int amperage) {
        return registerTieredMachines(amperage + "a_energy_converter",
                (info, tier) -> new ConverterMachine(info, tier, amperage),
                (tier, builder) -> builder
                        .rotationState(RotationState.ALL)
                        .langValue("%s %s§eA§r Energy Converter".formatted(VCF[tier] + VN[tier] + ChatFormatting.RESET,
                                amperage))
                        .modelProperty(GTMachineModelProperties.IS_FE_TO_EU, false)
                        .model(GTMachineModels.createConverterModel(64))
                        .tooltips(Component.translatable("gtceu.machine.energy_converter.description"),
                                Component.translatable("gtceu.machine.energy_converter.tooltip_tool_usage"),
                                Component.translatable("gtceu.machine.energy_converter.tooltip_conversion_native",
                                        FeCompat.toFeLong(V[tier] * amperage,
                                                FeCompat.ratio(true)),
                                        amperage, V[tier], GTValues.VNF[tier]),
                                Component.translatable("gtceu.machine.energy_converter.tooltip_conversion_eu", amperage,
                                        V[tier], GTValues.VNF[tier],
                                        FeCompat.toFeLong(V[tier] * amperage,
                                                FeCompat.ratio(false))))
                        .register(),
                GTMachineUtils.ALL_TIERS);
    }

    // Copied from GTMachineUtils
    public static MachineDefinition[] registerTieredMachines(String name,
                                                             BiFunction<BlockEntityCreationInfo, Integer, MetaMachine> factory,
                                                             BiFunction<Integer, MachineBuilder<MachineDefinition, ?, ?>, MachineDefinition> builder,
                                                             int... tiers) {
        MachineDefinition[] definitions = new MachineDefinition[GTValues.TIER_COUNT];
        for (int tier : tiers) {
            var register = REGISTRATE
                    .machine(GTValues.VN[tier].toLowerCase(Locale.ROOT) + "_" + name,
                            info -> factory.apply(info, tier))
                    .tier(tier);
            definitions[tier] = builder.apply(tier, register);
        }
        return definitions;
    }

    public static MachineDefinition[] ENERGY_CONVERTER_64A = null;

    static {
        if ((UtilConfig.INSTANCE.features.converters64aEnabled &&
                ConfigHolder.INSTANCE.compat.energy.enableFEConverters) || GTCEu.isDataGen()) {
            ENERGY_CONVERTER_64A = registerConverter(64);
        }
    }

    public static MultiblockMachineDefinition WEB_HUB = null;
    public static MultiblockMachineDefinition WEB_RECEIVER = null;

    static {
        if (UtilConfig.INSTANCE.features.webEnabled || GTCEu.isDataGen()) {
            WEB_HUB = REGISTRATE
                    .multiblock("web_hub", WEBHubMachine::new)
                    .langValue("Wireless Energy Bridge Hub")
                    .rotationState(RotationState.ALL)
                    .recipeType(GTRecipeTypes.DUMMY_RECIPES)
                    .appearanceBlock(CASING_PALLADIUM_SUBSTATION)
                    .tooltips(Component.translatable("tooltip.web_hub_machine.purpose"),
                            Component.translatable("gtceu.machine.active_transformer.tooltip.1"),
                            Component.translatable("tooltip.web_hub_machine.frequencies")
                                    .withStyle(ChatFormatting.GRAY))
                    .conditionalTooltip(
                            Component
                                    .translatable("tooltip.web_hub_machine.uses_coolant",
                                            UtilMaterials.QuantumCoolant !=
                                                    null ? UtilMaterials.QuantumCoolant.getLocalizedName()
                                                            .withStyle(ChatFormatting.AQUA) : "")
                                    .withStyle(ChatFormatting.DARK_RED),
                            UtilConfig.coolantEnabled())
                    .conditionalTooltip(Component.translatable("tooltip.web_hub_machine.input_coolant_before_use")
                            .withStyle(ChatFormatting.DARK_RED), UtilConfig.coolantEnabled())
                    .pattern((definition) -> MultiblockPatternBuilder
                            .start(RelativeDirection.FRONT, RelativeDirection.UP, RelativeDirection.RIGHT)
                            // spotless:off
                            .slice("###XXX###", "####F####", "#########", "####H####", "####H####", "####H####", "####H####", "####H####")
                            .slice("#XXXXXXX#", "###FHF###", "####H####", "####H####", "####H####", "####F####", "#########", "#########")
                            .slice("#XXHHHXX#", "#########", "#########", "#########", "####F####", "####F####", "#########", "#########")
                            .slice("XXHHHHHXX", "#F#####F#", "#########", "####S####", "###SSS###", "###SSS###", "###S#S###", "#########")
                            .slice("XXHHHHHXX", "FH##H##HF", "#H##C##H#", "HH#SSS#HH", "HHFSSSFHH", "HFFSSSFFH", "H#######H", "H#######H")
                            .slice("XXHHHHHXX", "#F#####F#", "#########", "####S####", "###SSS###", "###SSS###", "###S#S###", "#########")
                            .slice("#XXHHHXX#", "#########", "#########", "#########", "####F####", "####F####", "#########", "#########")
                            .slice("#XXXXXXX#", "###FHF###", "####H####", "####H####", "####H####", "####F####", "#########", "#########")
                            .slice("###XXX###", "####F####", "#########", "####H####", "####H####", "####H####", "####H####", "####H####")
                            // spotless:on
                            .where('#', any())
                            .where('X',
                                    blocks(CASING_PALLADIUM_SUBSTATION.get()).setMinGlobalLimited(30)
                                            .or(WEBHubMachine.getHatchPredicates()))
                            .where('S', blocks(SUPERCONDUCTING_COIL.get()))
                            .where('H', blocks(HIGH_POWER_CASING.get()))
                            .where('C', controller(blocks(definition.getBlock())))
                            .where('F', frames(GTMaterials.Neutronium))
                            .build())
                    .workableCasingModel(GTCEu.id("block/casings/solid/machine_casing_palladium_substation"),
                            GTCEu.id("block/multiblock/data_bank"))
                    .allowExtendedFacing(true)
                    .hasBER(true)
                    .register();

            WEB_RECEIVER = REGISTRATE
                    .multiblock("web_receiver", WEBReceiverMachine::new)
                    .langValue("Wireless Energy Bridge Receiver")
                    .rotationState(RotationState.ALL)
                    .recipeType(GTRecipeTypes.DUMMY_RECIPES)
                    .appearanceBlock(HIGH_POWER_CASING)
                    .tooltips(Component.translatable("tooltip.web_receiver_machine.purpose"),
                            Component.translatable("gtceu.machine.active_transformer.tooltip.1"),
                            Component.translatable("tooltip.web_receiver_machine.frequencies")
                                    .withStyle(ChatFormatting.GRAY))
                    .pattern((definition) -> MultiblockPatternBuilder
                            .start(RelativeDirection.FRONT, RelativeDirection.UP, RelativeDirection.RIGHT)
                            .slice("abbba", "aabaa", "aaaaa", "aaaaa", "aaaaa", "aacaa", "aacaa", "aadaa")
                            .slice("bbbbb", "abdba", "aacaa", "aaaaa", "aaaaa", "aacaa", "aaaaa", "aaaaa")
                            .slice("bbbbb", "bdddb", "acdca", "aadaa", "aadaa", "ccdcc", "cadac", "daaad")
                            .slice("bbbbb", "abdba", "aacaa", "aaaaa", "aaaaa", "aacaa", "aaaaa", "aaaaa")
                            .slice("abeba", "aabaa", "aaaaa", "aaaaa", "aaaaa", "aacaa", "aacaa", "aadaa")
                            .where('e', controller(blocks(definition.getBlock())))
                            .where('b',
                                    blocks(HIGH_POWER_CASING.get()).setMinGlobalLimited(12)
                                            .or(WEBReceiverMachine.getHatchPredicates()))
                            .where('d', blocks(SUPERCONDUCTING_COIL.get()))
                            .where('c', frames(GTMaterials.NaquadahAlloy))
                            .where('a', air())
                            .build())
                    .workableCasingModel(GTCEu.id("block/casings/hpca/high_power_casing"),
                            GTCEu.id("block/multiblock/data_bank"))
                    .allowExtendedFacing(true)
                    .hasBER(true)
                    .register();
        }

    }

    public static void init() {}
}
