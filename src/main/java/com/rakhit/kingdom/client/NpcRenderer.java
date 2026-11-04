package com.rakhit.kingdom.client;

import com.rakhit.kingdom.KingdomNpc;
import com.rakhit.kingdom.RakhitMod;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Рисует всех жителей королевства моделью игрока с собственными скинами (форма стражи, мантия короля, костюм хомяка). */
public class NpcRenderer extends HumanoidMobRenderer<KingdomNpc, PlayerModel<KingdomNpc>> {
    private static final ResourceLocation[] CITIZENS = new ResourceLocation[8];
    private static final ResourceLocation GUARD = RakhitMod.id("textures/entity/guard.png");
    private static final ResourceLocation KING = RakhitMod.id("textures/entity/king.png");
    private static final ResourceLocation HAMA = RakhitMod.id("textures/entity/hama.png");
    private static final ResourceLocation STINKER = RakhitMod.id("textures/entity/stinker.png");

    static {
        for (int i = 0; i < CITIZENS.length; i++) CITIZENS[i] = RakhitMod.id("textures/entity/citizen_" + i + ".png");
    }

    public NpcRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
    }

    @Override
    public ResourceLocation getTextureLocation(KingdomNpc e) {
        return switch (e.role()) {
            case GUARD -> GUARD;
            case KING -> KING;
            case HAMA -> HAMA;
            case STINKER -> STINKER;
            default -> CITIZENS[Math.floorMod(e.variant(), CITIZENS.length)];
        };
    }
}
