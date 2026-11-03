package com.rakhit.kingdom;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.function.Predicate;

/** 45 указов + менеджер: новый указ каждые 2 минуты, красивое объявление, проверка нарушений. */
public final class Decrees {
    public enum Kind {
        STATE, JUMP, BREAK_ANY, BREAK_LOG, BREAK_STONE, PLACE, CHAT, EAT, CRAFT, PICKUP, DROP, OPEN_CHEST,
        SLEEP, KILL_ANIMAL, KILL_MONSTER, DAMAGED, ATTACK, BUCKET, FLINT, TRADE, PEARL
    }

    /** STATE-указы проверяются опросом игроков; остальные срабатывают по событию (см. KingdomEvents). */
    public record Decree(String text, Kind kind, Predicate<ServerPlayer> test) {}

    public static final int INTERVAL = 2 * 60 * 20;   // 2 минуты
    private static final int GRACE = 100;              // 5 сек на ознакомление с STATE-указом

    public static final List<Decree> ALL = new ArrayList<>();

    private static void s(String text, Predicate<ServerPlayer> test) { ALL.add(new Decree(text, Kind.STATE, test)); }
    private static void e(String text, Kind kind) { ALL.add(new Decree(text, kind, null)); }

    private static boolean hold(ServerPlayer p, Item i) { return p.getMainHandItem().is(i) || p.getOffhandItem().is(i); }

    private static boolean invHas(ServerPlayer p, Predicate<ItemStack> f) {
        for (ItemStack st : p.getInventory().items) if (!st.isEmpty() && f.test(st)) return true;
        for (ItemStack st : p.getInventory().offhand) if (!st.isEmpty() && f.test(st)) return true;
        return false;
    }

    private static int invCount(ServerPlayer p, Item item) {
        int n = 0;
        for (ItemStack st : p.getInventory().items) if (st.is(item)) n += st.getCount();
        for (ItemStack st : p.getInventory().offhand) if (st.is(item)) n += st.getCount();
        return n;
    }

    static {
        // ---------- указы с опросом состояния ----------
        s("Королевские учёные доказали: вода опасна для здоровья! Купание запрещено!", ServerPlayer::isInWater);
        s("Бегущий человек - подозрительный человек! Бегать запрещено!", ServerPlayer::isSprinting);
        s("Король не любит тех, кто крадётся! Приседать запрещено!", ServerPlayer::isShiftKeyDown);
        s("Огонь - привилегия Короны! Гореть без разрешения запрещено!", ServerPlayer::isOnFire);
        s("Небо принадлежит Королю! Парить на элитрах запрещено!", ServerPlayer::isFallFlying);
        s("Верховая езда и лодки - привилегия знати! Простолюдинам ездить запрещено!", ServerPlayer::isPassenger);
        s("Под землёй водятся заговорщики! Находиться ниже уровня Y=50 запрещено!", p -> p.getY() < 50);
        s("Король боится высоты! Подниматься выше уровня Y=90 запрещено!", p -> p.getY() > 90);
        s("Факелы пожароопасны! Держать факел в руках запрещено!", p -> hold(p, Items.TORCH) || hold(p, Items.SOUL_TORCH));
        s("Броня - признак заговора! Носить броню запрещено!", p -> !p.getItemBySlot(EquipmentSlot.HEAD).isEmpty()
                || !p.getItemBySlot(EquipmentSlot.CHEST).isEmpty() || !p.getItemBySlot(EquipmentSlot.LEGS).isEmpty()
                || !p.getItemBySlot(EquipmentSlot.FEET).isEmpty());
        s("Меч в руке - это бунт! Держать мечи запрещено!", p -> p.getMainHandItem().is(ItemTags.SWORDS));
        s("Всё золото принадлежит Короне! Хранить золотые слитки запрещено!", p -> invHas(p, st -> st.is(Items.GOLD_INGOT)));
        s("Земля - собственность Короны! Хранить землю в инвентаре запрещено!", p -> invHas(p, st -> st.is(Items.DIRT)));
        s("Ночью честные подданные спят! Находиться под открытым небом ночью запрещено!",
                p -> p.level().dimension() == Level.OVERWORLD && p.level().isNight() && p.level().canSeeSky(p.blockPosition()));
        s("Дождь оскорбляет Корону! Мокнуть под дождём запрещено!", p -> p.level().isRainingAt(p.blockPosition()));
        s("В небе живут шпионы! Смотреть вверх запрещено!", p -> p.getXRot() < -60);
        s("Праздность - враг Короны! Стоять на месте дольше 5 секунд запрещено!", p -> idleSeconds(p) >= 5);
        s("Сытый подданный - ленивый подданный! Иметь полную шкалу голода запрещено!", p -> p.getFoodData().getFoodLevel() >= 20);
        s("Золотые яблоки - только для Короля! Хранить их запрещено!",
                p -> invHas(p, st -> st.is(Items.GOLDEN_APPLE) || st.is(Items.ENCHANTED_GOLDEN_APPLE)));
        s("Невидимки - шпионы врага! Быть невидимым запрещено!", p -> p.hasEffect(MobEffects.INVISIBILITY));
        s("Зелья - это колдовство! Иметь активные эффекты запрещено!", p -> !p.getActiveEffects().isEmpty());
        s("Слишком умные подданные опасны! Иметь больше 5 уровней опыта запрещено!", p -> p.experienceLevel > 5);
        s("Священные свиньи любят тишину! Приближаться к ним ближе 4 блоков запрещено!",
                p -> !p.level().getEntitiesOfClass(Pig.class, p.getBoundingBox().inflate(4.0)).isEmpty());
        s("Хлеб - только для богатых! Хранить хлеб запрещено!", p -> invHas(p, st -> st.is(Items.BREAD)));
        s("Алмазы - собственность казны! Носить при себе больше 16 алмазов запрещено!", p -> invCount(p, Items.DIAMOND) > 16);

        // ---------- указы по событиям ----------
        e("Прыгать - значит насмехаться над Короной! Прыгать запрещено!", Kind.JUMP);
        e("Земля устала! Ломать блоки запрещено!", Kind.BREAK_ANY);
        e("Деревья - верные подданные Короны! Рубить деревья запрещено!", Kind.BREAK_LOG);
        e("Камень - основа трона! Добывать камень запрещено!", Kind.BREAK_STONE);
        e("Строить без разрешения Короны нельзя! Ставить блоки запрещено!", Kind.PLACE);
        e("Тишина! Король думает! Писать в чат запрещено!", Kind.CHAT);
        e("Король на диете - и вы тоже! Есть запрещено!", Kind.EAT);
        e("Ремесло - дело придворных мастеров! Крафтить запрещено!", Kind.CRAFT);
        e("Всё, что лежит на земле, принадлежит казне! Подбирать предметы запрещено!", Kind.PICKUP);
        e("Мусорить в королевстве нельзя! Выбрасывать предметы запрещено!", Kind.DROP);
        e("Сундуки проверяет налоговая служба! Открывать сундуки и бочки запрещено!", Kind.OPEN_CHEST);
        e("Спать на посту запрещено! Ложиться в кровать запрещено!", Kind.SLEEP);
        e("Скот - налогооблагаемое имущество! Убивать животных запрещено!", Kind.KILL_ANIMAL);
        e("Монстры - наши дорогие соседи! Убивать монстров запрещено!", Kind.KILL_MONSTER);
        e("Получать урон - расточительство! Ранения запрещены!", Kind.DAMAGED);
        e("Мир и порядок! Драться с кем-либо запрещено!", Kind.ATTACK);
        e("Вёдра - оружие заговорщиков! Использовать вёдра запрещено!", Kind.BUCKET);
        e("Поджигатели будут наказаны! Использовать огниво запрещено!", Kind.FLINT);
        e("Торговля мимо банка запрещена! Торговать с жителями запрещено!", Kind.TRADE);
        e("Телепортация - это побег от правосудия! Бросать жемчуг Края запрещено!", Kind.PEARL);
    }

    // ---------- состояние ----------
    private static Decree current;
    private static int number;
    private static long issuedAt, nextAt;
    private static final Deque<Integer> BAG = new ArrayDeque<>();
    private static final ServerBossEvent BAR = new ServerBossEvent(Component.empty(),
            BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_20);
    private static final Map<UUID, Vec3> LAST_POS = new HashMap<>();
    private static final Map<UUID, Integer> IDLE = new HashMap<>();

    private Decrees() {}

    public static void reset() {
        current = null; number = 0; issuedAt = 0; nextAt = 0;
        BAG.clear(); LAST_POS.clear(); IDLE.clear();
        BAR.removeAllPlayers();
        BAR.setVisible(false);
    }

    public static void forceNext() { nextAt = 1; }

    public static void onLogin(ServerPlayer p) {
        BAR.addPlayer(p);
        if (current != null) {
            p.sendSystemMessage(Component.literal("⚜ Действующий указ №" + number + ": " + current.text()).withStyle(ChatFormatting.GOLD));
        }
    }

    public static void onLogout(ServerPlayer p) {
        BAR.removePlayer(p);
        LAST_POS.remove(p.getUUID());
        IDLE.remove(p.getUUID());
    }

    private static int idleSeconds(ServerPlayer p) { return IDLE.getOrDefault(p.getUUID(), 0); }

    private static void trackIdle(MinecraftServer s) {
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            Vec3 now = p.position();
            Vec3 last = LAST_POS.put(p.getUUID(), now);
            if (last != null && last.distanceToSqr(now) < 0.02) IDLE.merge(p.getUUID(), 1, Integer::sum);
            else IDLE.put(p.getUUID(), 0);
        }
    }

    public static void tick(MinecraftServer s) {
        if (!Kingdom.ready) return;
        long t = s.getTickCount();
        if (t % 20 == 0) trackIdle(s);
        if (nextAt == 0) nextAt = t + 600;            // первый указ через 30 секунд после старта
        if (t >= nextAt) issue(s, t);
        if (current == null) return;
        if (t % 20 == 0) BAR.setProgress(Mth.clamp((float) (nextAt - t) / INTERVAL, 0f, 1f));
        if (current.kind() == Kind.STATE && t - issuedAt >= GRACE && t % 10 == 0) {
            for (ServerPlayer p : new ArrayList<>(s.getPlayerList().getPlayers())) {
                if (p.isSpectator() || p.isCreative()) continue;
                if (current.test().test(p)) Justice.violate(p, "нарушил Указ №" + number);
            }
        }
    }

    /** Вызывается обработчиками событий: если действует указ этого типа - игрок нарушитель. */
    public static void report(ServerPlayer p, Kind k) {
        if (current != null && current.kind() == k) Justice.violate(p, "нарушил Указ №" + number);
    }

    public static int number() { return number; }

    // ---------- выдача и красивое объявление ----------
    private static void issue(MinecraftServer s, long t) {
        if (BAG.isEmpty()) {
            List<Integer> idx = new ArrayList<>();
            for (int i = 0; i < ALL.size(); i++) idx.add(i);
            Collections.shuffle(idx);
            BAG.addAll(idx);
        }
        current = ALL.get(BAG.poll());
        number++;
        issuedAt = t;
        nextAt = t + INTERVAL;
        BAR.setVisible(true);
        BAR.setProgress(1f);
        BAR.setName(Component.literal("⚜ УКАЗ №" + number + " ⚜  ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal(current.text()).withStyle(ChatFormatting.YELLOW)));
        announce(s, current, number);
    }

    private static void announce(MinecraftServer s, Decree d, int n) {
        Component title = Component.literal("⚜ ВЫСОЧАЙШИЙ УКАЗ №" + n + " ⚜").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        Component sub = Component.literal(d.text()).withStyle(ChatFormatting.YELLOW, ChatFormatting.ITALIC);
        Component line = Component.literal("═══════════════════════════════════").withStyle(ChatFormatting.GOLD);

        s.getPlayerList().broadcastSystemMessage(line, false);
        s.getPlayerList().broadcastSystemMessage(Component.literal("  ⚜ УКАЗ №" + n + " ⚜ ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal("Его Величества Короля Рахита III").withStyle(ChatFormatting.YELLOW, ChatFormatting.ITALIC)), false);
        s.getPlayerList().broadcastSystemMessage(Component.literal("  " + d.text()).withStyle(ChatFormatting.WHITE), false);
        s.getPlayerList().broadcastSystemMessage(Component.literal("  Нарушителей заберёт офицерская карета!").withStyle(ChatFormatting.DARK_RED), false);
        s.getPlayerList().broadcastSystemMessage(line, false);

        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 120, 25));
            p.connection.send(new ClientboundSetTitleTextPacket(title));
            p.connection.send(new ClientboundSetSubtitleTextPacket(sub));
            p.playNotifySound(SoundEvents.BELL_BLOCK, SoundSource.MASTER, 1.5f, 0.7f);
            p.serverLevel().sendParticles(ParticleTypes.TOTEM_OF_UNDYING, p.getX(), p.getY() + 1.0, p.getZ(), 40, 0.7, 0.9, 0.7, 0.3);
        }
        Scheduler.later(s, 12, () -> s.getPlayerList().getPlayers()
                .forEach(p -> p.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 1f, 1f)));
        Scheduler.later(s, 30, () -> s.getPlayerList().getPlayers()
                .forEach(p -> p.playNotifySound(SoundEvents.VILLAGER_CELEBRATE, SoundSource.MASTER, 1f, 0.6f)));

        // эффект "печатной машинки" в action bar
        String text = d.text();
        for (int i = 1; i <= text.length(); i++) {
            final String part = text.substring(0, i) + (i < text.length() ? "▌" : "");
            Scheduler.later(s, i, () -> {
                if (current != d) return;
                Component c = Component.literal(part).withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC);
                for (ServerPlayer p : s.getPlayerList().getPlayers()) p.displayClientMessage(c, true);
            });
        }
    }
}
