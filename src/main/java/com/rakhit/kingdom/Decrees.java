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
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.function.Predicate;

/** 89 указов + менеджер: новый указ каждые 2 минуты (или по воле Короля), красивое объявление, проверка нарушений. */
public final class Decrees {
    public enum Kind {
        STATE, JUMP, BREAK_ANY, BREAK_LOG, BREAK_STONE, PLACE, CHAT, EAT, CRAFT, PICKUP, DROP, OPEN_CHEST,
        SLEEP, KILL_ANIMAL, KILL_MONSTER, DAMAGED, ATTACK, BUCKET, FLINT, TRADE, PEARL,
        BREAK_DIRT, BREAK_ORE, BREAK_LEAVES, BREAK_PLANT, BREAK_GLASS, SHOOT, THROW, FISH,
        USE_TABLE, USE_FURNACE, USE_DOOR, USE_BUTTON, SHEAR, FEED, KILL_CREEPER, DRINK, PLACE_TORCH
    }

    /** Как жители королевства подчиняются указу (их ИИ читает правило каждый тик). */
    public enum CitizenRule { NONE, KEEP_MOVING, STAY_HOME, NO_JUMP, NO_SPRINT, NO_SNEAK }

    /** Чем "мятежный" житель демонстративно нарушает указ перед тем, как его схватит стража. */
    public enum Act { SHOUT, JUMP, SPRINT, SNEAK }

    /** STATE-указы проверяются опросом игроков; остальные срабатывают по событию (см. KingdomEvents). */
    public static final class Decree {
        public final String text;
        public final Kind kind;
        public final Predicate<ServerPlayer> test;
        public CitizenRule rule = CitizenRule.NONE;
        public Act act = Act.SHOUT;

        Decree(String text, Kind kind, Predicate<ServerPlayer> test) {
            this.text = text; this.kind = kind; this.test = test;
        }

        Decree rule(CitizenRule r) { this.rule = r; return this; }
        Decree act(Act a) { this.act = a; return this; }

        public String text() { return text; }
        public Kind kind() { return kind; }
        public Predicate<ServerPlayer> test() { return test; }

        /** Последняя фраза указа - короткое название запрета (для книги Короля). */
        public String shortName() {
            int cut = text.lastIndexOf("! ", text.length() - 2);
            return cut >= 0 ? text.substring(cut + 2) : text;
        }
    }

    public static final int INTERVAL = 2 * 60 * 20;   // 2 минуты
    private static final int GRACE = 100;              // 5 сек на ознакомление с STATE-указом

    public static final List<Decree> ALL = new ArrayList<>();

    private static Decree s(String text, Predicate<ServerPlayer> test) { Decree d = new Decree(text, Kind.STATE, test); ALL.add(d); return d; }
    private static Decree e(String text, Kind kind) { Decree d = new Decree(text, kind, null); ALL.add(d); return d; }

    private static boolean hold(ServerPlayer p, Item i) { return p.getMainHandItem().is(i) || p.getOffhandItem().is(i); }

    private static boolean invHas(ServerPlayer p, Predicate<ItemStack> f) {
        for (ItemStack st : p.getInventory().items) if (!st.isEmpty() && f.test(st)) return true;
        for (ItemStack st : p.getInventory().offhand) if (!st.isEmpty() && f.test(st)) return true;
        return false;
    }

    private static boolean holdTag(ServerPlayer p, net.minecraft.tags.TagKey<Item> tag) {
        return p.getMainHandItem().is(tag) || p.getOffhandItem().is(tag);
    }

    private static int usedSlots(ServerPlayer p) {
        int n = 0;
        for (ItemStack st : p.getInventory().items) if (!st.isEmpty()) n++;
        return n;
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
        s("Бегущий человек - подозрительный человек! Бегать запрещено!", ServerPlayer::isSprinting).rule(CitizenRule.NO_SPRINT).act(Act.SPRINT);
        s("Король не любит тех, кто крадётся! Приседать запрещено!", ServerPlayer::isShiftKeyDown).rule(CitizenRule.NO_SNEAK).act(Act.SNEAK);
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
                p -> p.level().dimension() == Level.OVERWORLD && p.level().isNight() && p.level().canSeeSky(p.blockPosition())).rule(CitizenRule.STAY_HOME);
        s("Дождь оскорбляет Корону! Мокнуть под дождём запрещено!", p -> p.level().isRainingAt(p.blockPosition())).rule(CitizenRule.STAY_HOME);
        s("В небе живут шпионы! Смотреть вверх запрещено!", p -> p.getXRot() < -60);
        s("Праздность - враг Короны! Стоять на месте дольше 5 секунд запрещено!", p -> idleSeconds(p) >= 5).rule(CitizenRule.KEEP_MOVING);
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


        // ---------- новые указы с опросом состояния ----------
        s("Кирка - орудие заговорщиков-шахтёров! Держать кирки запрещено!", p -> holdTag(p, ItemTags.PICKAXES));
        s("Топор - оружие палачей, а палач у нас один! Держать топоры запрещено!", p -> holdTag(p, ItemTags.AXES));
        s("Лопаты выкапывают государственные тайны! Держать лопаты запрещено!", p -> holdTag(p, ItemTags.SHOVELS));
        s("Лучники видят слишком далеко! Держать луки и арбалеты запрещено!", p -> hold(p, Items.BOW) || hold(p, Items.CROSSBOW));
        s("Прятаться за щитом - значит не доверять Королю! Держать щиты запрещено!", p -> hold(p, Items.SHIELD));
        s("Булыжник годится лишь для мостовой Короны! Хранить булыжник запрещено!", p -> invHas(p, st -> st.is(Items.COBBLESTONE)));
        s("Железо плавится только в королевских печах! Хранить железные слитки запрещено!", p -> invHas(p, st -> st.is(Items.IRON_INGOT)));
        s("Единственная валюта - королевская! Хранить изумруды запрещено!", p -> invHas(p, st -> st.is(Items.EMERALD)));
        s("Динамит - прерогатива Короля! Хранить TNT запрещено!", p -> invHas(p, st -> st.is(Items.TNT)));
        s("Цветы растут только в королевских садах! Хранить цветы запрещено!", p -> invHas(p, st -> st.is(ItemTags.FLOWERS)));
        s("Яйца нужны для сцены позора! Хранить яйца запрещено!", p -> invHas(p, st -> st.is(Items.EGG)));
        s("Свинина - это кощунство! Хранить свинину запрещено!", p -> invHas(p, st -> st.is(Items.PORKCHOP) || st.is(Items.COOKED_PORKCHOP)));
        s("Подданный обязан быть бодрым! Иметь меньше 5 сердец здоровья запрещено!", p -> p.getHealth() < 10.0f);
        s("Король не терпит истощённых! Иметь голод ниже 6 делений запрещено!", p -> p.getFoodData().getFoodLevel() <= 6);
        s("Ад - владения конкурентов! Находиться в Нижнем мире запрещено!", p -> p.level().dimension() == Level.NETHER);
        s("Трава - пастбище священных свиней! Ходить по траве запрещено!", p -> p.onGround() && p.getBlockStateOn().is(Blocks.GRASS_BLOCK));
        s("Песок - собственность пустыни! Стоять на песке запрещено!", p -> p.onGround() && p.getBlockStateOn().is(BlockTags.SAND));
        s("Ползающий подданный подозрителен! Ползать запрещено!", ServerPlayer::isVisuallyCrawling);
        s("У Короля нет лицензии на лазание! Лазать по лестницам и лозам запрещено!", ServerPlayer::onClimbable);
        s("Падение - признак слабости! Падать с высоты больше 3 блоков запрещено!", p -> !p.onGround() && p.fallDistance > 3.0f);
        s("Заговоры шепчутся вдвоём! Стоять ближе 3 блоков к другому подданному запрещено!",
                p -> !p.level().getEntitiesOfClass(ServerPlayer.class, p.getBoundingBox().inflate(3.0),
                        o -> o != p && !o.isSpectator()).isEmpty());
        s("Беглецам не место за стенами! Удаляться от королевства дальше 150 блоков запрещено!",
                p -> p.level().dimension() == Level.OVERWORLD && Kingdom.distFromCenter(p) > 150);
        s("Время принадлежит Королю! Хранить часы запрещено!", p -> invHas(p, st -> st.is(Items.CLOCK)));
        s("Карты и компасы - орудия шпионов! Хранить карты и компасы запрещено!",
                p -> invHas(p, st -> st.is(Items.MAP) || st.is(Items.FILLED_MAP) || st.is(Items.COMPASS)));
        s("Питомцы объедают казну! Находиться рядом с прирученными животными запрещено!",
                p -> !p.level().getEntitiesOfClass(TamableAnimal.class, p.getBoundingBox().inflate(6.0), TamableAnimal::isTame).isEmpty());
        s("Жадность - порок! Занимать больше 20 слотов инвентаря запрещено!", p -> usedSlots(p) > 20);
        s("Пустая рука - рука бездельника! Ходить с пустой рукой запрещено!", p -> p.getMainHandItem().isEmpty());

        // ---------- указы по событиям ----------
        e("Прыгать - значит насмехаться над Короной! Прыгать запрещено!", Kind.JUMP).rule(CitizenRule.NO_JUMP).act(Act.JUMP);
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

        // ---------- новые указы по событиям ----------
        e("Земля и трава - собственность Короны! Копать землю запрещено!", Kind.BREAK_DIRT);
        e("Руды принадлежат казне! Добывать руду запрещено!", Kind.BREAK_ORE);
        e("Листва - уши Короля! Ломать листву запрещено!", Kind.BREAK_LEAVES);
        e("Цветы и злаки радуют Короля! Срывать растения запрещено!", Kind.BREAK_PLANT);
        e("Стекло хрупко, как терпение Короля! Бить стекло запрещено!", Kind.BREAK_GLASS);
        e("Стрелы летят в сторону трона! Стрелять из лука запрещено!", Kind.SHOOT);
        e("Снежки и яйца - только для сцены позора! Бросать их запрещено!", Kind.THROW);
        e("Вся рыба - королевская! Ловить рыбу запрещено!", Kind.FISH);
        e("Верстаки и наковальни - государственная тайна! Пользоваться ими запрещено!", Kind.USE_TABLE);
        e("Печи коптят королевское небо! Пользоваться печами запрещено!", Kind.USE_FURNACE);
        e("Двери пропускают заговорщиков! Открывать двери и калитки запрещено!", Kind.USE_DOOR);
        e("Нажимать на кнопки может только Король! Трогать кнопки и рычаги запрещено!", Kind.USE_BUTTON);
        e("Овцы - наши голые подданные! Стричь овец запрещено!", Kind.SHEAR);
        e("Королевские животные на диете! Кормить животных запрещено!", Kind.FEED);
        e("Криперы - любимцы Короля! Убивать криперов запрещено!", Kind.KILL_CREEPER);
        e("Жидкости пьют с разрешения! Пить зелья и молоко запрещено!", Kind.DRINK);
        e("Факелы и фонари слепят Короля! Ставить факелы запрещено!", Kind.PLACE_TORCH);
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
        if (Throne.audience()) {                       // во время аудиенции у Короля указы стоят
            if (nextAt != 0) nextAt = Math.max(nextAt, t + 200);
            return;
        }
        if (nextAt == 0) nextAt = t + 600;            // первый указ через 30 секунд после старта
        if (t >= nextAt) issue(s, t, -1, null);
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
    private static void issue(MinecraftServer s, long t, int index, String byKing) {
        if (index >= 0 && index < ALL.size()) {
            current = ALL.get(index);
        } else {
            if (BAG.isEmpty()) {
                List<Integer> idx = new ArrayList<>();
                for (int i = 0; i < ALL.size(); i++) idx.add(i);
                Collections.shuffle(idx);
                BAG.addAll(idx);
            }
            current = ALL.get(BAG.poll());
        }
        number++;
        issuedAt = t;
        nextAt = t + INTERVAL;
        BAR.setVisible(true);
        BAR.setProgress(1f);
        BAR.setName(Component.literal("⚜ УКАЗ №" + number + " ⚜  ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal(current.text()).withStyle(ChatFormatting.YELLOW)));
        announce(s, current, number, byKing);
        Citizens.onDecree(s, current, number);
    }

    /** Указ по воле Короля из книги: выдаётся сразу, таймер очередного указа начинается заново. */
    public static void issueByKing(MinecraftServer s, int index, String kingName) {
        issue(s, s.getTickCount(), index, kingName);
    }

    public static Decree current() { return current; }

    public static CitizenRule npcRule() { return current == null ? CitizenRule.NONE : current.rule; }

    private static void announce(MinecraftServer s, Decree d, int n, String byKing) {
        Component title = Component.literal("⚜ ВЫСОЧАЙШИЙ УКАЗ №" + n + " ⚜").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        Component sub = Component.literal(d.text()).withStyle(ChatFormatting.YELLOW, ChatFormatting.ITALIC);
        Component line = Component.literal("═══════════════════════════════════").withStyle(ChatFormatting.GOLD);

        s.getPlayerList().broadcastSystemMessage(line, false);
        s.getPlayerList().broadcastSystemMessage(Component.literal("  ⚜ УКАЗ №" + n + " ⚜ ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal(byKing == null ? "Его Величества Короля Рахита III"
                        : "Его Величества Короля " + byKing + " (по велению книги указов)").withStyle(ChatFormatting.YELLOW, ChatFormatting.ITALIC)), false);
        s.getPlayerList().broadcastSystemMessage(Component.literal("  " + d.text()).withStyle(ChatFormatting.WHITE), false);
        s.getPlayerList().broadcastSystemMessage(Component.literal("  Нарушителей схватит королевская гвардия и увезёт в карете!").withStyle(ChatFormatting.DARK_RED), false);
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
