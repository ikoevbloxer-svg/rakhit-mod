package com.rakhit.kingdom;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;

import java.util.*;

/**
 * Трон: каждые 30 минут Король Рахит III созывает всех игроков ко двору и случайно выбирает одного нового Короля.
 * Король получает единственную в мире корону и книгу указов (клик по указу - указ объявляется сразу).
 * По окончании срока корона и книга аннулируются: сверяется номер срока (serial) на предмете.
 */
public final class Throne {
    public static final int INTERVAL = 30 * 60 * 20;          // 30 минут
    private static final int BOOK_COOLDOWN = 20 * 15;          // не чаще одного указа в 15 секунд

    private record Back(ResourceKey<Level> dim, double x, double y, double z, float yaw, float pitch) {}

    private static final Map<UUID, Back> RETURN = new HashMap<>();
    private static final Map<UUID, Long> BOOK_LAST = new HashMap<>();
    private static long audienceEnd;
    private static long lastTick;

    private Throne() {}

    public static void reset() { RETURN.clear(); BOOK_LAST.clear(); audienceEnd = 0; lastTick = 0; }

    public static boolean audience() { return audienceEnd > lastTick; }

    // ==================== проверки ====================

    private static CompoundTag tagOf(ItemStack st) {
        return st.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    public static boolean isCrownStack(ItemStack st) { return st.is(ModItems.CROWN.get()); }

    public static boolean isBookStack(ItemStack st) {
        return st.is(Items.WRITTEN_BOOK) && tagOf(st).getBoolean("rakhit_book");
    }

    /** Действующая ли корона: номер срока на ней совпадает с текущим. */
    public static boolean crownValid(MinecraftServer s, ItemStack st) {
        if (!isCrownStack(st)) return false;
        KingdomData d = KingdomData.get(s);
        return d.termSerial > 0 && tagOf(st).getInt("rakhit_serial") == d.termSerial;
    }

    public static boolean isCrowned(ServerPlayer p) {
        ItemStack head = p.getItemBySlot(EquipmentSlot.HEAD);
        return isCrownStack(head) && crownValid(p.server, head);
    }

    public static boolean isKing(ServerPlayer p) {
        KingdomData d = KingdomData.get(p.server);
        return d.king != null && d.king.equals(p.getUUID());
    }

    /** Корона на голове или статус Короля - неуязвимость к указам и наказаниям. */
    public static boolean immune(ServerPlayer p) { return isCrowned(p) || isKing(p); }

    // ==================== тик ====================

    public static void tick(MinecraftServer s) {
        lastTick = s.getTickCount();
        if (!Kingdom.ready) return;
        KingdomData d = KingdomData.get(s);
        long gt = s.overworld().getGameTime();
        if (d.nextCoronation <= 0) { d.nextCoronation = gt + INTERVAL; d.setDirty(); }
        if (gt >= d.nextCoronation && !audience()) coronation(s, null);
        if (lastTick % 20 == 0) {
            sweepPlayers(s, d);
            if (lastTick % 100 == 0) sweepItemEntities(s, d);
        }
    }

    /** Удаляет недействительные короны/книги/удочки у онлайн-игроков и выдаёт королю книгу, если её нет. */
    private static void sweepPlayers(MinecraftServer s, KingdomData d) {
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            strip(p, d);
            if (d.king != null && d.king.equals(p.getUUID()) && p.isAlive() && !hasValidBook(p, d)) giveBook(p, d);
        }
    }

    private static boolean hasValidBook(ServerPlayer p, KingdomData d) {
        for (ItemStack st : p.getInventory().items)
            if (isBookStack(st) && tagOf(st).getInt("rakhit_serial") == d.termSerial) return true;
        return false;
    }

    /** Забирает у игрока всё, что не должно у него быть. */
    public static void strip(ServerPlayer p, KingdomData d) {
        boolean king = d.king != null && d.king.equals(p.getUUID());
        boolean changed = false;
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.isEmpty()) continue;
            boolean bad = false;
            if (isCrownStack(st)) bad = !(d.termSerial > 0 && tagOf(st).getInt("rakhit_serial") == d.termSerial);
            else if (isBookStack(st)) bad = !(king && tagOf(st).getInt("rakhit_serial") == d.termSerial);
            else if (Justice.isRod(st)) bad = !Justice.isSentenced(p.getUUID());
            if (bad) { inv.setItem(i, ItemStack.EMPTY); changed = true; }
        }
        ItemStack carried = p.containerMenu.getCarried();
        if (isBookStack(carried) && !king) { p.containerMenu.setCarried(ItemStack.EMPTY); changed = true; }
        if (changed) p.containerMenu.broadcastChanges();
    }

    private static void sweepItemEntities(MinecraftServer s, KingdomData d) {
        for (ServerLevel lvl : s.getAllLevels()) {
            List<Entity> kill = new ArrayList<>();
            for (Entity e : lvl.getAllEntities()) {
                if (!(e instanceof ItemEntity ie)) continue;
                ItemStack st = ie.getItem();
                if (isCrownStack(st) && !(d.termSerial > 0 && tagOf(st).getInt("rakhit_serial") == d.termSerial)) kill.add(e);
                else if (isBookStack(st) || Justice.isRod(st)) kill.add(e);   // книгу и удочку выбросить нельзя
            }
            kill.forEach(Entity::discard);
        }
    }

    public static void onLogin(ServerPlayer p) {
        KingdomData d = KingdomData.get(p.server);
        strip(p, d);
        Back b = RETURN.get(p.getUUID());
        if (b != null && !audience()) { RETURN.remove(p.getUUID()); sendBack(p, b); }
    }

    /** Король пытался выбросить книгу или удочку: вернуть, если предмет пропал из инвентаря. */
    public static void afterToss(ServerPlayer p) {
        KingdomData d = KingdomData.get(p.server);
        if (d.king != null && d.king.equals(p.getUUID()) && !hasValidBook(p, d)) giveBook(p, d);
    }

    // ==================== коронация ====================

    public static void coronation(MinecraftServer s, ServerPlayer forced) {
        if (!Kingdom.ready || audience()) return;
        KingdomData d = KingdomData.get(s);
        ServerLevel ow = s.overworld();
        d.nextCoronation = ow.getGameTime() + INTERVAL;
        d.setDirty();

        List<ServerPlayer> all = new ArrayList<>();
        for (ServerPlayer p : s.getPlayerList().getPlayers()) if (!p.isSpectator() && p.isAlive()) all.add(p);
        if (all.isEmpty()) {                       // двор пуст: срок просто заканчивается
            endTerm(s, d);
            return;
        }
        final ServerPlayer winner = forced != null && all.contains(forced) ? forced : all.get(ow.random.nextInt(all.size()));

        Justice.amnesty(s);
        s.getPlayerList().broadcastSystemMessage(Component.literal("🔔 Король Рахит III созывает всех подданных ко двору!")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);

        // барабанная дробь: интервалы между сменой имён в "рулетке"
        int[] steps = {2, 2, 2, 2, 3, 3, 3, 4, 4, 5, 6, 7, 9, 11, 14, 18};
        int reveal = 70;
        for (int st : steps) reveal += st * 2;
        audienceEnd = s.getTickCount() + reveal + 200;

        for (ServerPlayer p : all) {
            RETURN.put(p.getUUID(), new Back(p.level().dimension(), p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot()));
            var pos = Kingdom.throneHall(ow.random);
            p.stopRiding();
            p.teleportTo(ow, pos.x, pos.y, pos.z, 90f, 0f);
            p.fallDistance = 0;
            p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 15));
            p.connection.send(new ClientboundSetTitleTextPacket(Component.literal("⚜ ВЫЗОВ КО ДВОРУ ⚜").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
            p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("Король Рахит III выбирает преемника...").withStyle(ChatFormatting.YELLOW, ChatFormatting.ITALIC)));
            p.playNotifySound(SoundEvents.BELL_BLOCK, SoundSource.MASTER, 2f, 0.6f);
            ow.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, p.getX(), p.getY() + 1, p.getZ(), 30, 0.6, 0.9, 0.6, 0.2);
        }

        // "рулетка" имён в action bar
        int t = 70;
        for (int i = 0; i < steps.length; i++) {
            t += steps[i] * 2;
            final boolean last = i == steps.length - 1;
            final float pitch = 0.6f + 0.05f * i;
            Scheduler.later(s, t, () -> {
                ServerPlayer shown = last ? winner : all.get(ow.random.nextInt(all.size()));
                Component c = Component.literal("♛ ").withStyle(ChatFormatting.GOLD)
                        .append(shown.getDisplayName().copy().withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                        .append(Component.literal(" ♛").withStyle(ChatFormatting.GOLD));
                for (ServerPlayer p : s.getPlayerList().getPlayers()) {
                    p.displayClientMessage(c, true);
                    p.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.MASTER, 1f, pitch);
                }
            });
        }
        Scheduler.later(s, reveal + 6, () -> crown(s, winner));
        Scheduler.later(s, reveal + 200, () -> returnAll(s));
    }

    private static void crown(MinecraftServer s, ServerPlayer winner) {
        KingdomData d = KingdomData.get(s);
        endTerm(s, d);
        if (winner.isRemoved() || !winner.isAlive()) {
            s.getPlayerList().broadcastSystemMessage(Component.literal("Избранник не явился - трон пустует до следующего срока.").withStyle(ChatFormatting.GRAY), false);
            return;
        }
        d.king = winner.getUUID();
        d.kingName = winner.getGameProfile().getName();
        d.setDirty();

        giveCrown(winner, d);
        giveBook(winner, d);
        int fund = d.crownFund;
        d.crownFund = 0;
        d.setDirty();
        while (fund > 0) {
            int n = Math.min(64, fund);
            winner.getInventory().placeItemBackInInventory(new ItemStack(Items.DIAMOND, n));
            fund -= n;
        }

        Component name = winner.getDisplayName();
        s.getPlayerList().broadcastSystemMessage(Component.literal("♛ ").withStyle(ChatFormatting.GOLD)
                .append(name.copy().withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                .append(Component.literal(" - новый Король! Срок правления - 30 минут.").withStyle(ChatFormatting.GOLD)), false);
        ServerLevel ow = s.overworld();
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
            p.connection.send(new ClientboundSetTitleTextPacket(Component.literal("♛ НОВЫЙ КОРОЛЬ ♛").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
            p.connection.send(new ClientboundSetSubtitleTextPacket(name.copy().withStyle(ChatFormatting.YELLOW)));
            p.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 1f, 1f);
            p.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.MASTER, 1f, 0.8f);
            p.playNotifySound(SoundEvents.VILLAGER_CELEBRATE, SoundSource.MASTER, 1f, 0.7f);
            ow.sendParticles(ParticleTypes.FIREWORK, p.getX(), p.getY() + 1.5, p.getZ(), 40, 0.8, 1.0, 0.8, 0.15);
        }
        winner.sendSystemMessage(Component.literal("Правой кнопкой откройте «Книгу указов» и нажмите на любой указ - он будет объявлен немедленно. "
                + "Корона и книга исчезнут по окончании срока.").withStyle(ChatFormatting.GOLD));
    }

    /** Закончить срок: новый номер, старые корона и книга становятся недействительными и сразу изымаются. */
    private static void endTerm(MinecraftServer s, KingdomData d) {
        d.termSerial++;
        d.king = null;
        d.kingName = "";
        d.setDirty();
        sweepPlayers(s, d);
        sweepItemEntities(s, d);
    }

    private static void returnAll(MinecraftServer s) {
        for (Map.Entry<UUID, Back> en : new ArrayList<>(RETURN.entrySet())) {
            ServerPlayer p = s.getPlayerList().getPlayer(en.getKey());
            if (p == null) continue;
            RETURN.remove(en.getKey());
            sendBack(p, en.getValue());
        }
        audienceEnd = 0;
    }

    private static void sendBack(ServerPlayer p, Back b) {
        ServerLevel lvl = p.server.getLevel(b.dim());
        if (lvl == null) lvl = p.server.overworld();
        p.teleportTo(lvl, b.x(), b.y(), b.z(), b.yaw(), b.pitch());
        p.sendSystemMessage(Component.literal("Аудиенция окончена. Вас вернули на прежнее место.").withStyle(ChatFormatting.GRAY));
    }

    // ==================== корона и книга ====================

    private static void giveCrown(ServerPlayer p, KingdomData d) {
        ItemStack crown = new ItemStack(ModItems.CROWN.get());
        CustomData.update(DataComponents.CUSTOM_DATA, crown, t -> t.putInt("rakhit_serial", d.termSerial));
        crown.set(DataComponents.CUSTOM_NAME, Component.literal("Корона Короля " + d.kingName).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        crown.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("Единственная корона в мире.").withStyle(ChatFormatting.YELLOW),
                Component.literal("Носящий её неуязвим к указам и наказаниям.").withStyle(ChatFormatting.GRAY))));
        if (p.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) p.setItemSlot(EquipmentSlot.HEAD, crown);
        else p.getInventory().placeItemBackInInventory(crown);
    }

    public static void giveBook(ServerPlayer p, KingdomData d) {
        p.getInventory().placeItemBackInInventory(makeBook(d));
    }

    private static ItemStack makeBook(KingdomData d) {
        List<Filterable<Component>> pages = new ArrayList<>();
        pages.add(Filterable.passThrough(Component.literal("⚜ КНИГА УКАЗОВ ⚜\n")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal("\nКороль " + d.kingName + "\n\n").withStyle(ChatFormatting.DARK_RED))
                .append(Component.literal("Нажмите на любой указ на следующих страницах - и он будет объявлен немедленно.\n\n")
                        .withStyle(ChatFormatting.BLACK))
                .append(Component.literal("Один указ в 15 секунд. Книга исчезнет вместе с короной.").withStyle(ChatFormatting.DARK_GRAY))));

        MutableComponent page = null;
        int lines = 0, pageNo = 1;
        for (int i = 0; i < Decrees.ALL.size(); i++) {
            Decrees.Decree dec = Decrees.ALL.get(i);
            String label = "№" + (i + 1) + " " + dec.shortName();
            int need = (int) Math.ceil(label.length() / 18.0);
            if (page == null || lines + need > 13) {
                if (page != null) pages.add(Filterable.passThrough(page));
                pageNo++;
                page = Component.literal("⚜ Указы, стр. " + (pageNo - 1) + "\n").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
                lines = 1;
            }
            final String full = dec.text();
            final int idx = i + 1;
            page.append(Component.literal(label + "\n").withStyle(st -> st
                    .withColor(ChatFormatting.DARK_BLUE)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/rakhit issue " + idx))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(full).withStyle(ChatFormatting.YELLOW)))));
            lines += need;
        }
        if (page != null) pages.add(Filterable.passThrough(page));

        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
                Filterable.passThrough("Книга указов"), d.kingName.isEmpty() ? "Король" : d.kingName, 0, pages, true));
        book.set(DataComponents.CUSTOM_NAME, Component.literal("Книга указов Короля").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        CustomData.update(DataComponents.CUSTOM_DATA, book, t -> {
            t.putBoolean("rakhit_book", true);
            t.putInt("rakhit_serial", d.termSerial);
        });
        return book;
    }

    // ==================== команда из книги ====================

    public static void issueFromBook(ServerPlayer p, int number) {
        MinecraftServer s = p.server;
        KingdomData d = KingdomData.get(s);
        if (!isKing(p)) {
            p.sendSystemMessage(Component.literal("Только действующий Король может издавать указы из книги!").withStyle(ChatFormatting.RED));
            return;
        }
        if (audience()) {
            p.sendSystemMessage(Component.literal("Идёт аудиенция - указы подождут.").withStyle(ChatFormatting.RED));
            return;
        }
        if (number < 1 || number > Decrees.ALL.size()) return;
        long now = s.getTickCount();
        long last = BOOK_LAST.getOrDefault(p.getUUID(), -100000L);
        if (now - last < BOOK_COOLDOWN) {
            p.displayClientMessage(Component.literal("Королевские писари ещё переписывают предыдущий указ... ("
                    + ((BOOK_COOLDOWN - (now - last)) / 20 + 1) + " с)").withStyle(ChatFormatting.RED), true);
            return;
        }
        BOOK_LAST.put(p.getUUID(), now);
        Decrees.issueByKing(s, number - 1, d.kingName);
    }
}
