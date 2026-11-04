package com.rakhit.kingdom.client;

import com.rakhit.kingdom.ModEntities;
import com.rakhit.kingdom.RakhitMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = RakhitMod.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class RakhitClient {
    private RakhitClient() {}

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(ModEntities.NPC.get(), NpcRenderer::new);
    }
}
