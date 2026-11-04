package com.rakhit.kingdom;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

@Mod(RakhitMod.MODID)
public class RakhitMod {
    public static final String MODID = "rakhit";

    public RakhitMod(IEventBus modBus) {
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModItems.ARMOR_MATERIALS.register(modBus);
        ModSounds.SOUNDS.register(modBus);
        ModEntities.ENTITIES.register(modBus);
        modBus.addListener(this::addToTabs);
        modBus.addListener(this::attributes);
    }

    private void attributes(EntityAttributeCreationEvent e) {
        e.put(ModEntities.NPC.get(), KingdomNpc.createAttributes().build());
    }

    private void addToTabs(BuildCreativeModeTabContentsEvent e) {
        // Корона в творческий набор не добавляется: в мире она одна и выдаётся только на коронации (/rakhit coronation).
        if (e.getTabKey() == CreativeModeTabs.COMBAT) {
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
