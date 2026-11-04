package com.rakhit.kingdom;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Сохраняемое состояние: где построено королевство, банковские счета, срок правления и приговоры. */
public class KingdomData extends SavedData {
    public boolean built;
    public int layout;
    public int cx, cy, cz;
    /** Алмазы, ушедшие на корону Короля после лопнувших банков. */
    public int crownFund;
    public final Map<UUID, Integer> deposits = new HashMap<>();
    public final Map<UUID, Integer> claimable = new HashMap<>();

    // --- правление ---
    /** Номер срока: корона и книга указов с другим номером недействительны. */
    public int termSerial;
    public UUID king;
    public String kingName = "";
    /** Игровое время (тики), когда Король созовёт двор и выберет нового правителя. */
    public long nextCoronation;

    // --- приговоры, не доживавшие до перезапуска сервера (UUID -> NBT) ---
    public final Map<UUID, CompoundTag> sentences = new HashMap<>();

    public static KingdomData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(KingdomData::new, KingdomData::load, null), "rakhit_kingdom");
    }

    public static KingdomData load(CompoundTag tag, HolderLookup.Provider lookup) {
        KingdomData d = new KingdomData();
        d.built = tag.getBoolean("built");
        d.layout = tag.getInt("layout");
        d.cx = tag.getInt("cx");
        d.cy = tag.getInt("cy");
        d.cz = tag.getInt("cz");
        d.crownFund = tag.getInt("crownFund");
        readMap(tag.getCompound("deposits"), d.deposits);
        readMap(tag.getCompound("claimable"), d.claimable);
        d.termSerial = tag.getInt("termSerial");
        if (tag.hasUUID("king")) d.king = tag.getUUID("king");
        d.kingName = tag.getString("kingName");
        d.nextCoronation = tag.getLong("nextCoronation");
        CompoundTag s = tag.getCompound("sentences");
        for (String k : s.getAllKeys()) {
            try { d.sentences.put(UUID.fromString(k), s.getCompound(k)); } catch (IllegalArgumentException ignored) {}
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
        tag.putBoolean("built", built);
        tag.putInt("layout", layout);
        tag.putInt("cx", cx);
        tag.putInt("cy", cy);
        tag.putInt("cz", cz);
        tag.putInt("crownFund", crownFund);
        tag.put("deposits", writeMap(deposits));
        tag.put("claimable", writeMap(claimable));
        tag.putInt("termSerial", termSerial);
        if (king != null) tag.putUUID("king", king);
        tag.putString("kingName", kingName);
        tag.putLong("nextCoronation", nextCoronation);
        CompoundTag s = new CompoundTag();
        sentences.forEach((k, v) -> s.put(k.toString(), v));
        tag.put("sentences", s);
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
