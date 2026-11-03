package com.rakhit.kingdom;

import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, RakhitMod.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> WHIP_CRACK = SOUNDS.register("whip_crack",
            () -> SoundEvent.createVariableRangeEvent(RakhitMod.id("whip_crack")));

    private ModSounds() {}
}
