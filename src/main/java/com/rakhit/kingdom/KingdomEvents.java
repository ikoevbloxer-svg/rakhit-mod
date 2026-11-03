package com.rakhit.kingdom;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = RakhitMod.MODID)
public final class KingdomEvents {
    private KingdomEvents() {}

    // ===================== жизненный цикл =====================

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent e) {
        MinecraftServer s = e.getServer();
        ServerLevel ow = s.overworld();
        KingdomData d = KingdomData.get(s);
        if (!d.built) {
            BlockPos sp = ow.getSharedSpawnPos();
            int cx = sp.getX() + 50, cz = sp.getZ() + 40;   // ~64 блока от спавна, весь комплекс внутри ~100
            ow.getChunk(cx >> 4, cz >> 4);
            int h = ow.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz);
            int cy = Math.max(h - 1, ow.getSeaLevel() + 1);
            KingdomBuilder.build(ow, cx, cy, cz);
            d.built = true; d.cx = cx; d.cy = cy; d.cz = cz;
            d.setDirty();
        }
        Kingdom.init(d);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent e) {
        Kingdom.reset(); Decrees.reset(); Justice.clear(); Scheduler.clear(); Bank.reset();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post e) {
        MinecraftServer s = e.getServer();
        Scheduler.tick(s);
        if (!Kingdom.ready) return;
        Decrees.tick(s);
        Justice.tick(s);
        Bank.tick(s);
        if (s.getTickCount() % 10 == 0) {
            for (ServerPlayer p : s.getPlayerList().getPlayers()) {
                if (Kingdom.inRoyal(p)) Justice.violate(p, "вторгся в королевские покои");
            }
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            Decrees.onLogin(p);
            if (Kingdom.ready) {
                p.sendSystemMessage(Component.literal("⚜ Королевство Рахита III: X=" + Kingdom.cx + " Z=" + Kingdom.cz
                        + " (команда /rakhit where)").withStyle(ChatFormatting.GOLD));
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) Decrees.onLogout(p);
    }

    // ===================== королевская территория =====================

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent e) {
        if (!(e.getPlayer() instanceof ServerPlayer p) || p.isCreative()) return;
        if (Kingdom.contains(p.level(), e.getPos())) {
            e.setCanceled(true);
            Justice.violate(p, "посмел сломать королевское имущество");
            return;
        }
        BlockState st = e.getState();
        Decrees.report(p, Decrees.Kind.BREAK_ANY);
        if (st.is(BlockTags.LOGS)) Decrees.report(p, Decrees.Kind.BREAK_LOG);
        if (st.is(BlockTags.BASE_STONE_OVERWORLD)) Decrees.report(p, Decrees.Kind.BREAK_STONE);
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || p.isCreative()) return;
        if (Kingdom.contains(p.level(), e.getPos())) {
            e.setCanceled(true);
            Justice.violate(p, "посмел застраивать королевскую землю");
            return;
        }
        Decrees.report(p, Decrees.Kind.PLACE);
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate e) {
        if (!Kingdom.ready) return;
        e.getAffectedBlocks().removeIf(pos -> Kingdom.contains(e.getLevel(), pos));
    }

    // ===================== свиньи и неприкосновенные жители =====================

    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent e) {
        LivingEntity victim = e.getEntity();
        if (victim.getTags().contains("rakhit_npc")) { e.setCanceled(true); return; }
        if (e.getSource().getEntity() instanceof ServerPlayer att && victim != att) {
            if (victim instanceof Pig) Justice.violate(att, "посмел обидеть священную свинью Короля");
            else Decrees.report(att, Decrees.Kind.ATTACK);
        }
        if (victim instanceof ServerPlayer vp) Decrees.report(vp, Decrees.Kind.DAMAGED);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        if (e.getSource().getEntity() instanceof ServerPlayer p) {
            LivingEntity v = e.getEntity();
            if (v instanceof Animal) Decrees.report(p, Decrees.Kind.KILL_ANIMAL);
            else if (v instanceof Monster) Decrees.report(p, Decrees.Kind.KILL_MONSTER);
        }
    }

    // ===================== события для указов =====================

    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) Decrees.report(p, Decrees.Kind.JUMP);
    }

    @SubscribeEvent
    public static void onChat(ServerChatEvent e) {
        ServerPlayer p = e.getPlayer();
        p.server.execute(() -> Decrees.report(p, Decrees.Kind.CHAT));
    }

    @SubscribeEvent
    public static void onEat(LivingEntityUseItemEvent.Finish e) {
        if (e.getEntity() instanceof ServerPlayer p && e.getItem().has(DataComponents.FOOD))
            Decrees.report(p, Decrees.Kind.EAT);
    }

    @SubscribeEvent
    public static void onCraft(PlayerEvent.ItemCraftedEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) Decrees.report(p, Decrees.Kind.CRAFT);
    }

    @SubscribeEvent
    public static void onPickup(ItemEntityPickupEvent.Pre e) {
        if (e.getPlayer() instanceof ServerPlayer p) Decrees.report(p, Decrees.Kind.PICKUP);
    }

    @SubscribeEvent
    public static void onToss(ItemTossEvent e) {
        if (e.getPlayer() instanceof ServerPlayer p) Decrees.report(p, Decrees.Kind.DROP);
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        Block b = e.getLevel().getBlockState(e.getPos()).getBlock();
        if (b instanceof ChestBlock || b instanceof BarrelBlock || b instanceof ShulkerBoxBlock || b instanceof EnderChestBlock)
            Decrees.report(p, Decrees.Kind.OPEN_CHEST);
        if (b instanceof BedBlock) Decrees.report(p, Decrees.Kind.SLEEP);
        if (e.getItemStack().is(Items.FLINT_AND_STEEL)) Decrees.report(p, Decrees.Kind.FLINT);
        if (e.getItemStack().getItem() instanceof BucketItem) Decrees.report(p, Decrees.Kind.BUCKET);
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        if (e.getItemStack().getItem() instanceof BucketItem) Decrees.report(p, Decrees.Kind.BUCKET);
        if (e.getItemStack().is(Items.ENDER_PEARL)) Decrees.report(p, Decrees.Kind.PEARL);
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        Entity t = e.getTarget();
        if (t.getTags().contains("rakhit_npc")) {    // король, зрители - без торговли
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        if (t instanceof Villager) Decrees.report(p, Decrees.Kind.TRADE);
    }

    // ===================== команды =====================

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("rakhit")
                .then(Commands.literal("where").executes(c -> {
                    c.getSource().sendSuccess(() -> Component.literal(Kingdom.ready
                            ? "⚜ Королевство Рахита III: X=" + Kingdom.cx + " Y=" + Kingdom.cy + " Z=" + Kingdom.cz
                            : "Королевство ещё не построено").withStyle(ChatFormatting.GOLD), false);
                    return 1;
                }))
                .then(Commands.literal("tp").requires(src -> src.hasPermission(2)).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    Vec3 v = Kingdom.exit();
                    p.teleportTo(c.getSource().getServer().overworld(), v.x, v.y, v.z, 0f, 0f);
                    return 1;
                }))
                .then(Commands.literal("decree").requires(src -> src.hasPermission(2)).executes(c -> {
                    Decrees.forceNext();
                    return 1;
                })));
    }
}
