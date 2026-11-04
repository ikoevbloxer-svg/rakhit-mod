package com.rakhit.kingdom;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrownEgg;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Нарушения, арест гвардией, королевская карета и 6 наказаний (+ казнь).
 * Наказываются и игроки, и живые жители королевства.
 */
public final class Justice {
    public enum Kind { PRISON, WHEEL, STAGE, CARCER, FISHING, EXECUTION }

    private static final class Sentence {
        Kind kind;
        long end;
        Vec3 pos;
        boolean npc;
        int quota, caught, extra;
        int rounds = 1;
        ItemStack saved = ItemStack.EMPTY;
        int savedSlot;
    }

    private static final Map<UUID, Sentence> ACTIVE = new HashMap<>();
    private static final Map<UUID, Long> IMMUNE = new HashMap<>();
    private static final Set<UUID> PENDING = new HashSet<>();

    public static final int FISH_QUOTA = 4;
    public static final int FISH_SECONDS = 90;

    private Justice() {}

    public static void clear() { ACTIVE.clear(); IMMUNE.clear(); PENDING.clear(); }

    public static boolean wheelBusy() {
        for (Sentence s : ACTIVE.values()) if (s.kind == Kind.WHEEL) return true;
        return false;
    }

    public static boolean isSentenced(UUID id) { return ACTIVE.containsKey(id); }

    public static void dropCase(UUID id) { PENDING.remove(id); }

    // ==================== нарушение ====================

    /** Зафиксировать нарушение игроком или жителем. Причина: "<кто> <reason>". */
    public static void violate(LivingEntity who, String reason) {
        if (!Kingdom.ready || !who.isAlive() || Throne.audience()) return;
        if (who instanceof ServerPlayer p) {
            if (p.isCreative() || p.isSpectator() || Throne.immune(p)) return;
        } else if (!(who instanceof KingdomNpc n) || n.role() != KingdomNpc.Role.CITIZEN || n.punished) {
            return;
        }
        MinecraftServer s = who.getServer();
        if (s == null) return;
        UUID id = who.getUUID();
        long now = s.getTickCount();
        if (ACTIVE.containsKey(id) || PENDING.contains(id) || IMMUNE.getOrDefault(id, 0L) > now) return;
        PENDING.add(id);

        s.getPlayerList().broadcastSystemMessage(Component.literal("🚨 ").withStyle(ChatFormatting.RED)
                .append(who.getDisplayName())
                .append(Component.literal(" " + reason + "! Стража уже бежит!").withStyle(ChatFormatting.RED)), false);
        dispatchGuard(who);
    }

    /** Отправить к нарушителю ближайшего свободного стражника; если стражи рядом нет - появится дежурный офицер. */
    private static void dispatchGuard(LivingEntity who) {
        ServerLevel lvl = (ServerLevel) who.level();
        KingdomNpc best = null;
        double bd = Double.MAX_VALUE;
        for (KingdomNpc g : lvl.getEntitiesOfClass(KingdomNpc.class, who.getBoundingBox().inflate(96, 48, 96),
                n -> n.role() == KingdomNpc.Role.GUARD && !n.tempGuard && n.arrestTarget == null && n.isAlive() && !n.punished)) {
            double d = g.distanceToSqr(who);
            if (d < bd) { bd = d; best = g; }
        }
        if (best == null || bd > 90 * 90) {
            double ang = lvl.random.nextDouble() * Math.PI * 2;
            best = Citizens.spawnGuard(lvl, who.getX() + Math.cos(ang) * 7, who.getY(), who.getZ() + Math.sin(ang) * 7,
                    "Дежурный офицер", true);
            if (best == null) { PENDING.remove(who.getUUID()); return; }
            lvl.sendParticles(ParticleTypes.POOF, best.getX(), best.getY() + 1, best.getZ(), 12, 0.3, 0.5, 0.3, 0.05);
        }
        best.arrestTarget = who.getUUID();
        best.arrestSince = lvl.getGameTime();
        lvl.playSound(null, best.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.NEUTRAL, 2.0f, 1.4f);
    }

    // ==================== арест и карета ====================

    public static void arrest(KingdomNpc guard, LivingEntity target) {
        if (!target.isAlive()) { PENDING.remove(target.getUUID()); return; }
        ServerLevel lvl = (ServerLevel) target.level();
        MinecraftServer s = lvl.getServer();
        UUID id = target.getUUID();

        guard.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        lvl.playSound(null, target.blockPosition(), ModSounds.WHIP_CRACK.get(), SoundSource.HOSTILE, 1.5f, 1.0f);
        lvl.playSound(null, target.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.HOSTILE, 2f, 0.5f);

        Minecart cart = EntityType.MINECART.create(lvl);
        if (cart != null) {
            cart.setPos(target.getX(), target.getY(), target.getZ());
            cart.setCustomName(Component.literal("Офицерская карета").withStyle(ChatFormatting.GOLD));
            cart.setCustomNameVisible(true);
            cart.setInvulnerable(true);
            lvl.addFreshEntity(cart);
            target.startRiding(cart, true);
        }
        guard.moveTo(target.getX() + 1.4, target.getY(), target.getZ(), 90f, 0f);
        guard.getNavigation().stop();
        if (target instanceof ServerPlayer p) {
            titles(p, "🚔 ВЫ ЗАДЕРЖАНЫ!", "Гвардеец пакует вас в карету...", ChatFormatting.RED);
            lvl.playSound(null, p.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.HOSTILE, 1.5f, 0.8f);
        }

        Scheduler.later(s, 50, () -> {
            if (cart != null) cart.discard();
            if (guard.tempGuard) {
                lvl.sendParticles(ParticleTypes.POOF, guard.getX(), guard.getY() + 1, guard.getZ(), 10, 0.3, 0.5, 0.3, 0.05);
                guard.discard();
            }
            if (target.isRemoved() || !target.isAlive()) { PENDING.remove(id); return; }
            target.stopRiding();
            punish(target);
        });
    }

    // ==================== выбор наказания ====================

    private static void punish(LivingEntity who) {
        MinecraftServer s = who.getServer();
        if (s == null) return;
        UUID id = who.getUUID();
        ServerLevel ow = s.overworld();
        PENDING.remove(id);

        int roll = ow.random.nextInt(100);
        Kind k = roll < 25 ? Kind.PRISON : roll < 45 ? Kind.WHEEL : roll < 65 ? Kind.STAGE
                : roll < 80 ? Kind.CARCER : roll < 95 ? Kind.FISHING : Kind.EXECUTION;

        if (k == Kind.EXECUTION) {
            execute(who, s);
            return;
        }
        Sentence st = new Sentence();
        st.kind = k;
        st.npc = !(who instanceof ServerPlayer);
        int secs;
        String name;
        switch (k) {
            case PRISON -> { secs = 50 + ow.random.nextInt(51); st.pos = Kingdom.cell(ow.random); name = "ТЮРЬМА"; }
            case WHEEL -> { secs = 45; st.pos = Kingdom.wheel(); name = "ХОМЯЧЬЕ КОЛЕСО"; }
            case STAGE -> { secs = 30; st.pos = Kingdom.stage(); name = "СЦЕНА ПОЗОРА"; }
            case CARCER -> { secs = 45 + ow.random.nextInt(16); st.pos = Kingdom.carcer(); name = "ПОДЗЕМНЫЙ КАРЦЕР"; }
            default -> { secs = FISH_SECONDS; st.pos = Kingdom.pier(); name = "ПРИНУДИТЕЛЬНАЯ РЫБАЛКА"; st.quota = FISH_QUOTA; }
        }
        st.end = s.getTickCount() + secs * 20L;
        ACTIVE.put(id, st);
        begin(who, st, s);

        String extra = k == Kind.FISHING ? " Норма: " + FISH_QUOTA + " рыбы!" : "";
        titles(who, "⛓ " + name, k == Kind.FISHING ? "Наловить " + FISH_QUOTA + " рыбы за " + secs + " с" : secs + " секунд", ChatFormatting.DARK_RED);
        s.getPlayerList().broadcastSystemMessage(who.getDisplayName().copy()
                .append(Component.literal(" отправлен(а) в «" + name + "» на " + secs + " с." + extra).withStyle(ChatFormatting.GRAY)), false);
    }

    private static void execute(LivingEntity who, MinecraftServer s) {
        s.getPlayerList().broadcastSystemMessage(Component.literal("☠ КАЗНЬ! ").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
                .append(who.getDisplayName())
                .append(Component.literal(" казнён по приказу Короля Рахита III!").withStyle(ChatFormatting.RED)), false);
        who.level().playSound(null, who.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.MASTER, 2f, 1f);
        IMMUNE.put(who.getUUID(), s.getTickCount() + 400L);
        if (who instanceof KingdomNpc n) {
            ((ServerLevel) n.level()).sendParticles(ParticleTypes.SOUL, n.getX(), n.getY() + 1, n.getZ(), 30, 0.4, 0.8, 0.4, 0.05);
            Citizens.respawnLater(s, n.home.equals(net.minecraft.core.BlockPos.ZERO) ? net.minecraft.core.BlockPos.containing(Kingdom.exit()) : n.home,
                    n.getCustomName() == null ? "Житель" : n.getCustomName().getString());
            n.discard();
        } else {
            who.kill();
        }
    }

    /** Телепорт на место отбывания и подготовка (эффекты, удочка). */
    private static void begin(LivingEntity who, Sentence st, MinecraftServer s) {
        Vec3 pos = st.pos;
        if (who instanceof ServerPlayer p) {
            p.teleportTo(s.overworld(), pos.x, pos.y, pos.z, st.kind == Kind.FISHING ? -90f : 0f, st.kind == Kind.FISHING ? 18f : 0f);
            p.fallDistance = 0;
            int ticks = (int) (st.end - s.getTickCount());
            if (st.kind == Kind.WHEEL) p.addEffect(new MobEffectInstance(MobEffects.HUNGER, ticks, 2, false, false, false));
            if (st.kind == Kind.STAGE || st.kind == Kind.FISHING)
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 10, false, false, false));
            if (st.kind == Kind.FISHING) giveRod(p, st);
        } else if (who instanceof KingdomNpc n) {
            n.punished = true;
            n.getNavigation().stop();
            n.moveTo(pos.x, pos.y, pos.z, st.kind == Kind.FISHING ? -90f : 0f, 0f);
            if (st.kind == Kind.FISHING) n.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.FISHING_ROD));
        }
    }

    // ==================== удочка принудительной рыбалки ====================

    private static void giveRod(ServerPlayer p, Sentence st) {
        ItemStack rod = new ItemStack(Items.FISHING_ROD);
        var ench = p.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        rod.enchant(ench.getOrThrow(Enchantments.LURE), 3);
        rod.set(DataComponents.CUSTOM_NAME, Component.literal("Королевская удочка").withStyle(ChatFormatting.GOLD));
        CustomData.update(DataComponents.CUSTOM_DATA, rod, t -> t.putBoolean("rakhit_rod", true));
        int slot = p.getInventory().selected;
        st.savedSlot = slot;
        st.saved = p.getInventory().getItem(slot).copy();
        p.getInventory().setItem(slot, rod);
        p.containerMenu.broadcastChanges();
    }

    public static boolean isRod(ItemStack st) {
        return !st.isEmpty() && st.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).contains("rakhit_rod");
    }

    private static void takeRod(ServerPlayer p, Sentence st) {
        var inv = p.getInventory();
        boolean removed = false;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (isRod(inv.getItem(i))) { inv.setItem(i, ItemStack.EMPTY); removed = true; }
        }
        if (isRod(p.containerMenu.getCarried())) p.containerMenu.setCarried(ItemStack.EMPTY);
        if (st != null && !st.saved.isEmpty()) {
            if (inv.getItem(st.savedSlot).isEmpty()) inv.setItem(st.savedSlot, st.saved);
            else inv.placeItemBackInInventory(st.saved);
        }
        if (removed) p.containerMenu.broadcastChanges();
    }

    /** Игрок вытащил улов (вызывается из ItemFishedEvent). */
    public static void onFish(ServerPlayer p, List<ItemStack> drops) {
        Sentence st = ACTIVE.get(p.getUUID());
        if (st == null || st.kind != Kind.FISHING) return;
        int fish = 0;
        for (ItemStack d : drops) if (d.is(ItemTags.FISHES)) fish += d.getCount();
        if (fish <= 0) {
            p.displayClientMessage(Component.literal("🎣 Это не рыба! Король требует рыбу!").withStyle(ChatFormatting.RED), true);
            return;
        }
        st.caught += fish;
        p.level().playSound(null, p.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1f, 1.2f);
        if (st.caught >= st.quota) st.end = p.server.getTickCount();   // норма выполнена - досрочное освобождение
    }

    // ==================== каждый тик ====================

    public static void tick(MinecraftServer s) {
        long now = s.getTickCount();
        if (now % 20 == 0) stinkerAura(s);
        if (ACTIVE.isEmpty()) return;

        Iterator<Map.Entry<UUID, Sentence>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Sentence> en = it.next();
            Sentence a = en.getValue();
            LivingEntity e = a.npc ? (s.overworld().getEntity(en.getKey()) instanceof LivingEntity le ? le : null)
                    : s.getPlayerList().getPlayer(en.getKey());
            if (e == null) {
                if (a.npc && now >= a.end) it.remove();      // житель выгружен из мира - срок истёк
                continue;                                    // игрок оффлайн: отбудет срок при входе
            }
            if (!e.isAlive()) { it.remove(); continue; }

            if (a.kind == Kind.FISHING && now >= a.end && a.caught < a.quota) {
                // норма не выполнена - наказание повторяется
                a.rounds++;
                a.caught = 0;
                a.end = now + FISH_SECONDS * 20L;
                titles(e, "🎣 НОРМА НЕ ВЫПОЛНЕНА!", "Наказание повторяется (попытка " + a.rounds + ")", ChatFormatting.DARK_RED);
                if (e instanceof ServerPlayer p) p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, FISH_SECONDS * 20, 10, false, false, false));
            }
            if (now >= a.end) { release(e, a, s); it.remove(); continue; }

            Vec3 pos = a.pos;
            if (e.level() != s.overworld() && e instanceof ServerPlayer p) {
                p.teleportTo(s.overworld(), pos.x, pos.y, pos.z, 0f, 0f);
            }
            double d2 = e.distanceToSqr(pos.x, pos.y, pos.z);
            switch (a.kind) {
                case PRISON -> { if (d2 > 16) snap(e, pos); }
                case CARCER -> { if (d2 > 30) snap(e, pos); }
                case WHEEL -> {
                    double dz = Math.abs(e.getZ() - pos.z), dx = Math.abs(e.getX() - pos.x);
                    if (dz > 2.6 || dx > 2.2) snap(e, pos);          // колесо "откатывает" бегуна назад
                    if (e instanceof ServerPlayer p && now % 20 == 0) {
                        p.causeFoodExhaustion(3.0f);
                        if (!p.isSprinting() && a.extra < 600) { a.end += 10; a.extra += 10; }   // бездельник - срок растёт
                    }
                }
                case STAGE -> {
                    if (d2 > 0.09) snap(e, pos);
                    if (now % 6 == 0) throwEgg(s.overworld(), pos);
                }
                case FISHING -> { if (d2 > 0.04) snap(e, pos); }
                default -> {}
            }
            if (now % 20 == 0 && e instanceof ServerPlayer p) {
                long left = Math.max(0, (a.end - now) / 20);
                String label = switch (a.kind) {
                    case PRISON -> "⛓ Тюрьма - осталось " + left + " с";
                    case WHEEL -> "🐹 Беги, хомячок! Бегущий не стоит - осталось " + left + " с";
                    case STAGE -> "🥚 Сцена позора - осталось " + left + " с";
                    case CARCER -> "☣ Карцер: дыши глубже! - осталось " + left + " с";
                    case FISHING -> "🎣 Рыбалка: " + a.caught + "/" + a.quota + " - осталось " + left + " с";
                    default -> "";
                };
                p.displayClientMessage(Component.literal(label).withStyle(ChatFormatting.RED), true);
                if (a.kind == Kind.FISHING && !p.getMainHandItem().is(Items.FISHING_ROD))
                    p.getInventory().selected = a.savedSlot;
            }
        }
    }

    private static void snap(LivingEntity e, Vec3 pos) {
        if (e instanceof ServerPlayer p) p.connection.teleport(pos.x, pos.y, pos.z, p.getYRot(), p.getXRot());
        else e.moveTo(pos.x, pos.y, pos.z, e.getYRot(), e.getXRot());
    }

    /** Вонючий житель отравляет всех в радиусе, но яд никогда не добивает: 1 хп остаётся. */
    private static void stinkerAura(MinecraftServer s) {
        if (!Kingdom.ready) return;
        ServerLevel lvl = s.overworld();
        Vec3 c = Kingdom.stinker();
        AABB box = new AABB(c.x - 8, c.y - 4, c.z - 8, c.x + 8, c.y + 5, c.z + 8);
        for (LivingEntity e : lvl.getEntitiesOfClass(LivingEntity.class, box, le -> !(le instanceof KingdomNpc) && le.isAlive())) {
            if (e instanceof ServerPlayer p && (p.isCreative() || p.isSpectator())) continue;
            e.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 1, false, true, true));
            e.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 120, 0, false, false, true));
            e.addEffect(new MobEffectInstance(MobEffects.HUNGER, 80, 1, false, false, false));
        }
        lvl.sendParticles(ParticleTypes.ITEM_SLIME, c.x, c.y + 1.2, c.z, 12, 0.6, 0.6, 0.6, 0.02);
        lvl.sendParticles(ParticleTypes.SNEEZE, c.x, c.y + 1.0, c.z, 8, 0.8, 0.5, 0.8, 0.03);
    }

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

    // ==================== освобождение ====================

    private static void release(LivingEntity e, Sentence a, MinecraftServer s) {
        Vec3 ex = Kingdom.exit();
        if (e instanceof ServerPlayer p) {
            p.removeEffect(MobEffects.HUNGER);
            p.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            p.removeEffect(MobEffects.POISON);
            p.removeEffect(MobEffects.CONFUSION);
            if (a.kind == Kind.FISHING) takeRod(p, a);
            p.teleportTo(s.overworld(), ex.x, ex.y, ex.z, 0f, 0f);
            boolean ok = a.kind != Kind.FISHING || a.caught >= a.quota;
            p.sendSystemMessage(Component.literal(ok ? "Вы отбыли наказание. Слава Королю Рахиту III!" : "Вы свободны. Слава Королю Рахиту III!")
                    .withStyle(ChatFormatting.GOLD));
        } else if (e instanceof KingdomNpc n) {
            n.punished = false;
            n.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            n.moveTo(ex.x, ex.y, ex.z, n.getYRot(), 0f);
        }
        IMMUNE.put(e.getUUID(), s.getTickCount() + 200L);   // 10 секунд "амнистии"
    }

    /** Амнистия по случаю коронации: все отпущены (игроки потом возвращаются на прежние места). */
    public static void amnesty(MinecraftServer s) {
        for (Map.Entry<UUID, Sentence> en : new ArrayList<>(ACTIVE.entrySet())) {
            Sentence a = en.getValue();
            LivingEntity e = a.npc ? (s.overworld().getEntity(en.getKey()) instanceof LivingEntity le ? le : null)
                    : s.getPlayerList().getPlayer(en.getKey());
            if (e instanceof ServerPlayer p) {
                p.removeEffect(MobEffects.HUNGER);
                p.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                p.removeEffect(MobEffects.POISON);
                p.removeEffect(MobEffects.CONFUSION);
                if (a.kind == Kind.FISHING) takeRod(p, a);
                IMMUNE.put(p.getUUID(), s.getTickCount() + 600L);
            } else if (e instanceof KingdomNpc n) {
                n.punished = false;
                n.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            }
        }
        ACTIVE.clear();
        PENDING.clear();
    }

    // ==================== сохранение приговоров между запусками ====================

    public static void saveAll(MinecraftServer s) {
        KingdomData d = KingdomData.get(s);
        d.sentences.clear();
        long now = s.getTickCount();
        for (Map.Entry<UUID, Sentence> en : ACTIVE.entrySet()) {
            Sentence a = en.getValue();
            if (a.npc) continue;
            CompoundTag t = new CompoundTag();
            t.putInt("kind", a.kind.ordinal());
            t.putLong("left", Math.max(20, a.end - now));
            t.putInt("quota", a.quota);
            t.putInt("caught", a.caught);
            d.sentences.put(en.getKey(), t);
        }
        d.setDirty();
    }

    /** Игрок зашёл на сервер: если он сидел, когда сервер остановили - продолжаем отбывать. */
    public static void onLogin(ServerPlayer p) {
        MinecraftServer s = p.server;
        KingdomData d = KingdomData.get(s);
        CompoundTag t = d.sentences.remove(p.getUUID());
        if (t == null || !Kingdom.ready) return;
        d.setDirty();
        Kind[] ks = Kind.values();
        Sentence a = new Sentence();
        a.kind = ks[Math.max(0, Math.min(ks.length - 1, t.getInt("kind")))];
        a.end = s.getTickCount() + t.getLong("left");
        a.quota = t.getInt("quota");
        a.caught = t.getInt("caught");
        a.npc = false;
        a.pos = switch (a.kind) {
            case PRISON -> Kingdom.cell(s.overworld().random);
            case WHEEL -> Kingdom.wheel();
            case STAGE -> Kingdom.stage();
            case CARCER -> Kingdom.carcer();
            default -> Kingdom.pier();
        };
        ACTIVE.put(p.getUUID(), a);
        begin(p, a, s);
    }

    // ==================== утилиты ====================

    private static void titles(LivingEntity e, String title, String sub, ChatFormatting color) {
        if (!(e instanceof ServerPlayer p)) return;
        p.connection.send(new ClientboundSetTitlesAnimationPacket(5, 40, 10));
        p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(title).withStyle(color, ChatFormatting.BOLD)));
        p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(sub).withStyle(ChatFormatting.GRAY)));
    }
}
