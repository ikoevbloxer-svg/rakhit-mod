package com.rakhit.kingdom;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/** Живые жители королевства: создание, пропагандистские реплики и "мятежники", нарушающие указы. */
public final class Citizens {
    public static final int VARIANTS = 8;

    private static final String[] NAMES = {
            "Борис", "Фёкла", "Гаврила", "Агафья", "Тимофей", "Матрёна", "Ермолай", "Прасковья", "Евлампий", "Устинья",
            "Тихон", "Аглая", "Савелий", "Забава", "Мирон", "Лукерья", "Потап", "Варвара", "Назар", "Дуня",
            "Кузьма", "Ульяна", "Фрол", "Марфа", "Касьян", "Зоя", "Пахом", "Ядвига", "Митрофан", "Глаша"
    };
    private static final String[] PRAISE = {
            "Слава Королю Рахиту!", "Да здравствует Рахит III!", "Указ - закон!", "Король мудр!",
            "Как скажет Король!", "Всё во благо Короны!", "Мы любим налоги!"
    };
    private static final String[] RIOT = {
            "Долой указ!", "Не буду!", "А мне можно!", "Свободу жителям!", "Я тут по делу!", "Ой, я нечаянно!"
    };

    private Citizens() {}

    // ---------- создание ----------

    /** Создаёт жителя. home - точка у кровати, по ней он возвращается домой. */
    public static KingdomNpc spawn(ServerLevel lvl, double x, double y, double z, BlockPos home, int serial) {
        KingdomNpc n = ModEntities.NPC.get().create(lvl);
        if (n == null) return null;
        n.moveTo(x, y, z, lvl.random.nextFloat() * 360f, 0f);
        n.setRole(KingdomNpc.Role.CITIZEN);
        n.setVariant(lvl.random.nextInt(VARIANTS));
        n.home = home;
        n.setCustomName(Component.literal("Житель " + NAMES[serial % NAMES.length]));
        n.setCustomNameVisible(true);
        n.restrictTo(new BlockPos(Kingdom.cx, Kingdom.cy, Kingdom.cz), Kingdom.R - 3);
        if (lvl.random.nextInt(5) == 0) {                       // дети поменьше
            var sc = n.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SCALE);
            if (sc != null) sc.setBaseValue(0.65);
        }
        lvl.addFreshEntity(n);
        return n;
    }

    public static KingdomNpc spawnGuard(ServerLevel lvl, double x, double y, double z, String name, boolean temp) {
        KingdomNpc n = ModEntities.NPC.get().create(lvl);
        if (n == null) return null;
        n.moveTo(x, y, z, 0f, 0f);
        n.setRole(KingdomNpc.Role.GUARD);
        n.setVariant(0);
        n.tempGuard = temp;
        n.setCustomName(Component.literal(name).withStyle(ChatFormatting.RED));
        n.setCustomNameVisible(true);
        n.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.WHIP.get()));
        n.restrictTo(new BlockPos(Kingdom.cx, Kingdom.cy, Kingdom.cz), Kingdom.R - 3);
        lvl.addFreshEntity(n);
        return n;
    }

    public static KingdomNpc spawnSpecial(ServerLevel lvl, KingdomNpc.Role role, double x, double y, double z, float yaw, String name) {
        KingdomNpc n = ModEntities.NPC.get().create(lvl);
        if (n == null) return null;
        n.moveTo(x, y, z, yaw, 0f);
        n.setRole(role);
        n.setYHeadRot(yaw);
        n.setYBodyRot(yaw);
        n.setCustomName(Component.literal(name));
        n.setCustomNameVisible(true);
        lvl.addFreshEntity(n);
        return n;
    }

    public static List<KingdomNpc> all(ServerLevel lvl) {
        AABB box = new AABB(Kingdom.cx - Kingdom.R - 8, Kingdom.cy - 16, Kingdom.cz - Kingdom.R - 8,
                Kingdom.cx + Kingdom.R + 8, Kingdom.cy + 40, Kingdom.cz + Kingdom.R + 8);
        return lvl.getEntitiesOfClass(KingdomNpc.class, box);
    }

    /** Все старые тэгированные NPC (прежних версий мода) убираем перед достройкой. */
    public static void purgeOld(ServerLevel lvl) {
        AABB box = new AABB(Kingdom.cx - Kingdom.R - 8, Kingdom.cy - 16, Kingdom.cz - Kingdom.R - 8,
                Kingdom.cx + Kingdom.R + 8, Kingdom.cy + 40, Kingdom.cz + Kingdom.R + 8);
        for (var e : lvl.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, box,
                en -> en.getTags().contains("rakhit_npc") || en.getTags().contains("rakhit_king")
                        || en.getTags().contains("rakhit_label") || en.getTags().contains("rakhit_seat")
                        || en instanceof net.minecraft.world.entity.decoration.ArmorStand)) {
            e.discard();
        }
    }

    // ---------- указы и жители ----------

    /** При новом указе: часть жителей восхваляет Короля, а 1-2 "мятежника" решают нарушить его. */
    public static void onDecree(MinecraftServer s, Decrees.Decree d, int number) {
        if (!Kingdom.ready) return;
        ServerLevel lvl = s.overworld();
        List<KingdomNpc> citizens = new ArrayList<>();
        for (KingdomNpc n : all(lvl)) {
            if (n.role() == KingdomNpc.Role.CITIZEN && !n.punished && n.isAlive()) citizens.add(n);
            n.rebelAt = -1;
        }
        if (citizens.isEmpty()) return;

        // хор поддержки
        int praise = Math.min(citizens.size(), 3 + lvl.random.nextInt(3));
        List<KingdomNpc> pool = new ArrayList<>(citizens);
        for (int i = 0; i < praise && !pool.isEmpty(); i++) {
            KingdomNpc c = pool.remove(lvl.random.nextInt(pool.size()));
            int delay = 20 + lvl.random.nextInt(100);
            Scheduler.later(s, delay, () -> { if (c.isAlive()) c.say("§e" + PRAISE[lvl.random.nextInt(PRAISE.length)]); });
        }

        // мятежники
        int rebels = Math.min(pool.size(), 1 + lvl.random.nextInt(2));
        for (int i = 0; i < rebels; i++) {
            KingdomNpc r = pool.remove(lvl.random.nextInt(pool.size()));
            long when = s.getTickCount() + 200 + lvl.random.nextInt(1000);   // 10-60 секунд после объявления
            r.rebelAt = when;
        }
    }

    /** Тик: мятежники нарушают указ на глазах у всех. */
    public static void tick(MinecraftServer s) {
        if (!Kingdom.ready || s.getTickCount() % 20 != 7) return;
        long now = s.getTickCount();
        Decrees.Decree d = Decrees.current();
        if (d == null) return;
        for (KingdomNpc n : all(s.overworld())) {
            if (n.rebelAt < 0 || now < n.rebelAt || !n.isAlive() || n.punished) continue;
            n.rebelAt = -1;
            defy(s, n, d);
        }
    }

    private static void defy(MinecraftServer s, KingdomNpc n, Decrees.Decree d) {
        ServerLevel lvl = (ServerLevel) n.level();
        n.say("§c" + RIOT[lvl.random.nextInt(RIOT.length)]);
        n.swing(InteractionHand.MAIN_HAND);
        lvl.sendParticles(ParticleTypes.ANGRY_VILLAGER, n.getX(), n.getY() + 2.0, n.getZ(), 6, 0.3, 0.2, 0.3, 0.0);
        lvl.playSound(null, n.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1.0f, 1.0f);
        switch (d.act) {
            case JUMP -> n.getJumpControl().jump();
            case SPRINT -> {
                n.setSprinting(true);
                Scheduler.later(s, 60, () -> n.setSprinting(false));
            }
            case SNEAK -> {
                n.setShiftKeyDown(true);
                Scheduler.later(s, 60, () -> n.setShiftKeyDown(false));
            }
            default -> {}
        }
        Justice.violate(n, "нарушил Указ №" + Decrees.number());
    }

    /** Житель, казнённый по приговору, вскоре "заменяется" новым - население не вымирает. */
    public static void respawnLater(MinecraftServer s, BlockPos home, String name) {
        Scheduler.later(s, 20 * 45, () -> {
            ServerLevel lvl = s.overworld();
            KingdomNpc n = spawn(lvl, home.getX() + 0.5, home.getY(), home.getZ() + 0.5, home, lvl.random.nextInt(1000));
            if (n != null) {
                n.setCustomName(Component.literal(name));
                lvl.getServer().getPlayerList().broadcastSystemMessage(
                        Component.literal("👶 В королевстве родился новый житель: " + name).withStyle(ChatFormatting.GRAY), false);
            }
        });
    }

}
