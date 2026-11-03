package com.rakhit.kingdom;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

@Mod(RakhitMod.MODID)
public class RakhitMod {
    public static final String MODID = "rakhit";

    public RakhitMod(IEventBus modBus) {
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModItems.ARMOR_MATERIALS.register(modBus);
        ModSounds.SOUNDS.register(modBus);
        modBus.addListener(this::addToTabs);
    }

    private void addToTabs(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() == CreativeModeTabs.COMBAT) {
            e.accept(ModItems.CROWN);
            e.accept(ModItems.WHIP);
        }
        if (e.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            e.accept(ModItems.BANK_TELLER_ITEM);
        }
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
