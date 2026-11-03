package com.rakhit.kingdom;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.ThrownEgg;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/** Нарушения, офицерская карета и 4 наказания. */
public final class Justice {
    public enum Kind { PRISON, WHEEL, STAGE, EXECUTION }

    private record Active(Kind kind, long end, Vec3 pos) {}

    private static final Map<UUID, Active> ACTIVE = new HashMap<>();
    private static final Map<UUID, Long> IMMUNE = new HashMap<>();
    private static final Set<UUID> PENDING = new HashSet<>();

    private Justice() {}

    public static void clear() { ACTIVE.clear(); IMMUNE.clear(); PENDING.clear(); }

    /** Корона на голове = полный иммунитет к указам и наказаниям. */
    public static boolean hasCrown(ServerPlayer p) {
        return p.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.CROWN.get());
    }

    /** Зафиксировать нарушение. Причина подставляется в сообщение: "<игрок> <reason>". */
    public static void violate(ServerPlayer p, String reason) {
        if (!Kingdom.ready || p.isCreative() || p.isSpectator() || !p.isAlive()) return;
        if (hasCrown(p)) return;
        MinecraftServer s = p.server;
        UUID id = p.getUUID();
        long now = s.getTickCount();
        if (ACTIVE.containsKey(id) || PENDING.contains(id) || IMMUNE.getOrDefault(id, 0L) > now) return;
        PENDING.add(id);

        s.getPlayerList().broadcastSystemMessage(Component.literal("🚨 ").withStyle(ChatFormatting.RED)
                .append(p.getDisplayName())
                .append(Component.literal(" " + reason + "! Офицерская карета уже выехала!").withStyle(ChatFormatting.RED)), false);
        callCarriage(p);
    }

    private static void callCarriage(ServerPlayer p) {
        MinecraftServer s = p.server;
        ServerLevel lvl = p.serverLevel();

        Minecart cart = EntityType.MINECART.create(lvl);
        cart.setPos(p.getX(), p.getY(), p.getZ());
        cart.setCustomName(Component.literal("Офицерская карета").withStyle(ChatFormatting.GOLD));
        cart.setCustomNameVisible(true);
        cart.setInvulnerable(true);
        lvl.addFreshEntity(cart);

        Villager officer = EntityType.VILLAGER.create(lvl);
        officer.moveTo(p.getX() + 1.5, p.getY(), p.getZ(), 90f, 0f);
        officer.setNoAi(true);
        officer.setInvulnerable(true);
        officer.setCustomName(Component.literal("Офицер королевской стражи").withStyle(ChatFormatting.RED));
        officer.setCustomNameVisible(true);
        officer.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        officer.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        lvl.addFreshEntity(officer);

        p.startRiding(cart, true);
        lvl.playSound(null, p.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.HOSTILE, 2f, 0.5f);
        lvl.playSound(null, p.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.HOSTILE, 1.5f, 0.8f);
        titles(p, "🚔 ВЫ ЗАДЕРЖАНЫ!", "Офицер пакует вас в карету...", ChatFormatting.RED);

        UUID id = p.getUUID();
        Scheduler.later(s, 50, () -> {
            cart.discard();
            officer.discard();
            if (p.isRemoved() || !p.isAlive()) { PENDING.remove(id); return; }
            p.stopRiding();
            punish(p);
        });
    }

    private static void punish(ServerPlayer p) {
        MinecraftServer s = p.server;
        UUID id = p.getUUID();
        long now = s.getTickCount();
        ServerLevel ow = s.overworld();
        PENDING.remove(id);

        int roll = ow.random.nextInt(100);
        Kind k = roll < 40 ? Kind.PRISON : roll < 70 ? Kind.WHEEL : roll < 95 ? Kind.STAGE : Kind.EXECUTION;
        switch (k) {
            case EXECUTION -> {
                s.getPlayerList().broadcastSystemMessage(Component.literal("☠ КАЗНЬ! ").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
                        .append(p.getDisplayName())
                        .append(Component.literal(" казнён по приказу Короля Рахита III!").withStyle(ChatFormatting.RED)), false);
                p.level().playSound(null, p.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.MASTER, 2f, 1f);
                IMMUNE.put(id, now + 400);
                p.kill();
            }
            case PRISON -> start(p, k, 50 + ow.random.nextInt(51), Kingdom.cell(ow.random), "ТЮРЬМА");
            case WHEEL -> start(p, k, 45, Kingdom.wheel(), "ХОМЯЧЬЕ КОЛЕСО");
            case STAGE -> start(p, k, 30, Kingdom.stage(), "СЦЕНА ПОЗОРА");
        }
    }

    private static void start(ServerPlayer p, Kind k, int secs, Vec3 pos, String name) {
        MinecraftServer s = p.server;
        ACTIVE.put(p.getUUID(), new Active(k, s.getTickCount() + secs * 20L, pos));
        p.teleportTo(s.overworld(), pos.x, pos.y, pos.z, 0f, 0f);
        p.fallDistance = 0;
        if (k == Kind.WHEEL) p.addEffect(new MobEffectInstance(MobEffects.HUNGER, secs * 20, 2, false, false, false));
        if (k == Kind.STAGE) p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, secs * 20, 10, false, false, false));
        titles(p, "⛓ " + name, secs + " секунд", ChatFormatting.DARK_RED);
        s.getPlayerList().broadcastSystemMessage(p.getDisplayName().copy()
                .append(Component.literal(" отправлен(а) в «" + name + "» на " + secs + " с.").withStyle(ChatFormatting.GRAY)), false);
    }

    private static void titles(ServerPlayer p, String title, String sub, ChatFormatting color) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(5, 40, 10));
        p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(title).withStyle(color, ChatFormatting.BOLD)));
        p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(sub).withStyle(ChatFormatting.GRAY)));
    }

    // ---------- каждый тик ----------
    public static void tick(MinecraftServer s) {
        if (ACTIVE.isEmpty()) return;
        long now = s.getTickCount();
        Iterator<Map.Entry<UUID, Active>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Active> en = it.next();
            ServerPlayer p = s.getPlayerList().getPlayer(en.getKey());
            Active a = en.getValue();
            if (p == null) continue;                       // оффлайн: отбудет срок при входе
            if (!p.isAlive()) { it.remove(); continue; }
            if (now >= a.end()) { release(p); it.remove(); continue; }

            Vec3 pos = a.pos();
            if (p.level() != s.overworld()) {
                p.teleportTo(s.overworld(), pos.x, pos.y, pos.z, 0f, 0f);
            }
            double d2 = p.distanceToSqr(pos.x, pos.y, pos.z);
            switch (a.kind()) {
                case PRISON -> { if (d2 > 16) p.connection.teleport(pos.x, pos.y, pos.z, p.getYRot(), p.getXRot()); }
                case WHEEL -> {
                    if (d2 > 36) p.connection.teleport(pos.x, pos.y, pos.z, p.getYRot(), p.getXRot());
                    if (now % 20 == 0) p.causeFoodExhaustion(3.0f);   // бежим, бежим...
                }
                case STAGE -> {
                    if (d2 > 0.09) p.connection.teleport(pos.x, pos.y, pos.z, p.getYRot(), p.getXRot());
                    if (now % 6 == 0) throwEgg(s.overworld(), pos);
                }
                default -> {}
            }
            if (now % 20 == 0) {
                long left = (a.end() - now) / 20;
                String label = switch (a.kind()) {
                    case PRISON -> "⛓ Тюрьма";
                    case WHEEL -> "🐹 Беги, хомячок!";
                    case STAGE -> "🥚 Сцена позора";
                    default -> "";
                };
                p.displayClientMessage(Component.literal(label + " - осталось " + left + " с").withStyle(ChatFormatting.RED), true);
            }
        }
    }

    /** Житель из зрительного ряда швыряет яйцо в наказуемого. */
    private static void throwEgg(ServerLevel lvl, Vec3 victim) {
        double ax = Kingdom.cx + 9 + lvl.random.nextInt(11) + 0.5;
        double ay = Kingdom.cy + 2.6;
        double az = Kingdom.cz - 8.5;
        ThrownEgg egg = new ThrownEgg(lvl, ax, ay, az);
        double dx = victim.x - ax, dz = victim.z - az;
        double dy = victim.y + 1.4 - ay + Math.sqrt(dx * dx + dz * dz) * 0.12;
        egg.shoot(dx, dy, dz, 1.3f, 1.5f);
        lvl.addFreshEntity(egg);
        lvl.playSound(null, ax, ay, az, SoundEvents.EGG_THROW, SoundSource.NEUTRAL, 0.8f, 0.6f + lvl.random.nextFloat() * 0.8f);
    }

    private static void release(ServerPlayer p) {
        MinecraftServer s = p.server;
        Vec3 e = Kingdom.exit();
        p.removeEffect(MobEffects.HUNGER);
        p.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        p.teleportTo(s.overworld(), e.x, e.y, e.z, 0f, 0f);
        IMMUNE.put(p.getUUID(), s.getTickCount() + 200L);   // 10 секунд "амнистии"
        p.sendSystemMessage(Component.literal("Вы отбыли наказание. Слава Королю Рахиту III!").withStyle(ChatFormatting.GOLD));
    }
}
