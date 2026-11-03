package com.rakhit.kingdom;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Сохраняемое состояние: где построено королевство и банковские счета. */
public class KingdomData extends SavedData {
    public boolean built;
    public int cx, cy, cz;
    /** Алмазы, ушедшие на новую корону Короля после лопнувших банков. */
    public int crownFund;
    public final Map<UUID, Integer> deposits = new HashMap<>();
    public final Map<UUID, Integer> claimable = new HashMap<>();

    public static KingdomData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(KingdomData::new, KingdomData::load, null), "rakhit_kingdom");
    }

    public static KingdomData load(CompoundTag tag, HolderLookup.Provider lookup) {
        KingdomData d = new KingdomData();
        d.built = tag.getBoolean("built");
        d.cx = tag.getInt("cx");
        d.cy = tag.getInt("cy");
        d.cz = tag.getInt("cz");
        d.crownFund = tag.getInt("crownFund");
        readMap(tag.getCompound("deposits"), d.deposits);
        readMap(tag.getCompound("claimable"), d.claimable);
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
        tag.putBoolean("built", built);
        tag.putInt("cx", cx);
        tag.putInt("cy", cy);
        tag.putInt("cz", cz);
        tag.putInt("crownFund", crownFund);
        tag.put("deposits", writeMap(deposits));
        tag.put("claimable", writeMap(claimable));
        return tag;
    }

    private static void readMap(CompoundTag t, Map<UUID, Integer> out) {
        for (String k : t.getAllKeys()) {
            try { out.put(UUID.fromString(k), t.getInt(k)); } catch (IllegalArgumentException ignored) {}
        }
    }

    private static CompoundTag writeMap(Map<UUID, Integer> m) {
        CompoundTag t = new CompoundTag();
        m.forEach((k, v) -> t.putInt(k.toString(), v));
        return t;
    }
}
