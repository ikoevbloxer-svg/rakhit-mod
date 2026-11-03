package com.rakhit.kingdom;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Map;
import java.util.UUID;

/**
 * Королевский банк. Раунд = 5 минут. В конце раунда:
 *  - с шансом BURST_CHANCE банк ЛОПАЕТ: все вклады идут на новую корону Короля;
 *  - иначе каждый вкладчик получает вклад + 25% (забрать у кассы).
 * При BURST_CHANCE = 0.20 математическое ожидание ровно 1.0 (честная лотерея); подкрутите по вкусу.
 */
public final class Bank {
    public static final int ROUND_TICKS = 5 * 60 * 20;
    public static final double BURST_CHANCE = 0.20;
    public static final double INTEREST = 0.25;

    private static long nextRound = -1;

    private Bank() {}

    public static void reset() { nextRound = -1; }

    public static void tick(MinecraftServer s) {
        long t = s.getTickCount();
        if (nextRound < 0) nextRound = t + ROUND_TICKS;
        if (t >= nextRound) {
            nextRound = t + ROUND_TICKS;
            settle(s);
        }
    }

    private static int secondsLeft(MinecraftServer s) {
        return (int) Math.max(0, (nextRound - s.getTickCount()) / 20);
    }

    public static void deposit(ServerPlayer p, int amount) {
        KingdomData d = KingdomData.get(p.server);
        int total = d.deposits.merge(p.getUUID(), amount, Integer::sum);
        d.setDirty();
        p.level().playSound(null, p.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1f, 0.8f);
        p.displayClientMessage(Component.literal("💎 Вложено " + amount + ". Ваш вклад: " + total
                + ". Итоги раунда через " + secondsLeft(p.server) + " с").withStyle(ChatFormatting.AQUA), true);
    }

    public static void claimOrStatus(ServerPlayer p) {
        KingdomData d = KingdomData.get(p.server);
        Integer claim = d.claimable.remove(p.getUUID());
        if (claim != null && claim > 0) {
            d.setDirty();
            p.getInventory().placeItemBackInInventory(new ItemStack(Items.DIAMOND, claim));
            p.displayClientMessage(Component.literal("💰 Выплата получена: " + claim + " алмазов. Слава Королю!")
                    .withStyle(ChatFormatting.GOLD), true);
            return;
        }
        int dep = d.deposits.getOrDefault(p.getUUID(), 0);
        p.displayClientMessage(Component.literal("🏦 Ваш вклад: " + dep + " | итоги через " + secondsLeft(p.server)
                + " с | ПКМ с алмазами - вложить (Shift - один)").withStyle(ChatFormatting.YELLOW), true);
    }

    private static void settle(MinecraftServer s) {
        KingdomData d = KingdomData.get(s);
        if (d.deposits.isEmpty()) return;
        boolean burst = s.overworld().random.nextDouble() < BURST_CHANCE;
        if (burst) {
            int total = d.deposits.values().stream().mapToInt(Integer::intValue).sum();
            d.crownFund += total;
            d.deposits.clear();
            d.setDirty();
            s.getPlayerList().broadcastSystemMessage(Component.literal("💥 БАНК ЛОПНУЛ! ").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
                    .append(Component.literal(total + " алмазов пошли на новую корону Его Величества Рахита III!").withStyle(ChatFormatting.GOLD)), false);
            for (ServerPlayer p : s.getPlayerList().getPlayers())
                p.playNotifySound(SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.MASTER, 1f, 0.8f);
        } else {
            for (Map.Entry<UUID, Integer> e : d.deposits.entrySet()) {
                int dep = e.getValue();
                double raw = dep * INTEREST;
                int profit = (int) raw;
                if (s.overworld().random.nextDouble() < raw - profit) profit++;   // честное округление
                d.claimable.merge(e.getKey(), dep + profit, Integer::sum);
            }
            d.deposits.clear();
            d.setDirty();
            s.getPlayerList().broadcastSystemMessage(Component.literal("🏦 Банк устоял! Вкладчики, заберите деньги +25% у кассы.")
                    .withStyle(ChatFormatting.GREEN), false);
            for (ServerPlayer p : s.getPlayerList().getPlayers())
                p.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.MASTER, 1f, 1f);
        }
    }
}
