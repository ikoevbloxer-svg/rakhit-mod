package com.rakhit.kingdom;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, RakhitMod.MODID);

    /** Один тип сущности на всех: король, стражник, житель, Борзый Хома, вонючий житель (роль - в KingdomNpc.Role). */
    public static final DeferredHolder<EntityType<?>, EntityType<KingdomNpc>> NPC = ENTITIES.register("npc",
            () -> EntityType.Builder.<KingdomNpc>of(KingdomNpc::new, MobCategory.MISC)
                    .sized(0.6f, 1.8f)
                    .clientTrackingRange(10)
                    .build("npc"));

    private ModEntities() {}
}
