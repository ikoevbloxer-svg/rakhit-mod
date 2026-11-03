package com.rakhit.kingdom;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(RakhitMod.MODID);

    /** Касса банка: ПКМ с алмазами - вложить, ПКМ пустой рукой - забрать выплату / узнать статус. */
    public static final DeferredBlock<BankBlock> BANK_TELLER = BLOCKS.register("bank_teller",
            () -> new BankBlock(BlockBehaviour.Properties.of()
                    .strength(-1.0f, 3600000.0f)
                    .sound(SoundType.METAL)
                    .lightLevel(s -> 8)));

    private ModBlocks() {}
}
