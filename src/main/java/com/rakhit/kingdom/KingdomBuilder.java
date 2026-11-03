package com.rakhit.kingdom;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/** Процедурно строит королевство (без NBT-структур, всё в коде). */
final class KingdomBuilder {
    private static ServerLevel L;
    private static int X, Y, Z;
    private static final int R = Kingdom.R;
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private KingdomBuilder() {}

    static void build(ServerLevel level, int cx, int cy, int cz) {
        L = level; X = cx; Y = cy; Z = cz;

        // 1. прогрузить чанки
        for (int chx = (cx - R - 2) >> 4; chx <= (cx + R + 2) >> 4; chx++)
            for (int chz = (cz - R - 2) >> 4; chz <= (cz + R + 2) >> 4; chz++)
                level.getChunk(chx, chz);

        // 2. выровнять участок: засыпать ямы, срезать холмы, убрать деревья
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
        for (int dx = -R; dx <= R; dx++) {
            for (int dz = -R; dz <= R; dz++) {
                int wx = cx + dx, wz = cz + dz;
                int ground = level.getHeight(Heightmap.Types.OCEAN_FLOOR, wx, wz);
                for (int y = ground; y < cy; y++) level.setBlock(p.set(wx, y, wz), dirt, 2);
                int top = Math.max(ground, cy + 26);
                for (int y = cy + 1; y < top; y++) level.setBlock(p.set(wx, y, wz), AIR, 2);
                level.setBlock(p.set(wx, cy, wz), grass, 2);
            }
        }

        BlockState gold = Blocks.GOLD_BLOCK.defaultBlockState();
        BlockState brick = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState black = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        BlockState bars = Blocks.IRON_BARS.defaultBlockState();
        BlockState lamp = Blocks.SEA_LANTERN.defaultBlockState();

        // 3. забор по периметру, угловые башни, ворота (юг, z=+R)
        BlockState fence = Blocks.NETHER_BRICK_FENCE.defaultBlockState();
        for (int i = -R; i <= R; i++) {
            for (int y = 1; y <= 3; y++) {
                set(i, y, -R, fence); set(i, y, R, fence);
                set(-R, y, i, fence); set(R, y, i, fence);
            }
        }
        for (int sx : new int[]{-R, R}) {
            for (int sz : new int[]{-R, R}) {
                fill(sx, 1, sz, sx, 7, sz, black);
                set(sx, 8, sz, Blocks.GLOWSTONE.defaultBlockState());
            }
        }
        fill(-1, 1, R, 1, 5, R, AIR);
        fill(-2, 1, R, -2, 6, R, gold);
        fill(2, 1, R, 2, 6, R, gold);
        fill(-2, 6, R, 2, 6, R, gold);

        // 4. дороги и площадь
        fill(-1, 0, 2, 1, 0, R, brick);
        fill(-6, 0, -12, 6, 0, 2, Blocks.POLISHED_ANDESITE.defaultBlockState());
        fill(1, 0, 11, 7, 0, 13, brick);
        fill(-7, 0, 11, -1, 0, 13, brick);
        fill(0, 1, -5, 0, 3, -5, gold);
        set(0, 4, -5, Blocks.GLOWSTONE.defaultBlockState());

        // 5. КОРОЛЕВСКИЕ ПОКОИ (x -21..-7, z -21..-7)
        room(-21, -21, -7, -7, 6, Blocks.QUARTZ_BLOCK.defaultBlockState(), Blocks.SMOOTH_QUARTZ.defaultBlockState());
        for (int sx : new int[]{-21, -7}) for (int sz : new int[]{-21, -7}) fill(sx, 1, sz, sx, 6, sz, gold);
        fill(-7, 1, -11, -7, 3, -9, AIR);
        fill(-19, 1, -11, -7, 1, -9, Blocks.RED_CARPET.defaultBlockState());
        fill(-20, 1, -11, -20, 4, -9, gold);   // спинка трона
        set(-19, 1, -10, gold);                // сиденье
        for (int lx : new int[]{-16, -12}) for (int lz : new int[]{-16, -12}) set(lx, 6, lz, lamp);

        // 6. СЦЕНА ПОЗОРА (x 8..20, z -20..-12)
        fill(8, 1, -20, 20, 2, -12, Blocks.SPRUCE_PLANKS.defaultBlockState());
        BlockState stairs = Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH);
        for (int sx = 12; sx <= 14; sx++) set(sx, 1, -11, stairs);
        BlockState darkFence = Blocks.DARK_OAK_FENCE.defaultBlockState();
        fill(11, 3, -19, 11, 7, -19, darkFence);
        fill(15, 3, -19, 15, 7, -19, darkFence);
        fill(11, 8, -19, 15, 8, -19, Blocks.DARK_OAK_PLANKS.defaultBlockState());
        fill(13, 5, -19, 13, 7, -19, Blocks.CHAIN.defaultBlockState());
        set(11, 9, -19, Blocks.LANTERN.defaultBlockState());
        set(15, 9, -19, Blocks.LANTERN.defaultBlockState());

        // 7. ХОМЯЧЬЕ КОЛЕСО (клетка x -4..4, z -22..-14)
        fill(-4, 1, -22, 4, 4, -14, bars);
        fill(-3, 1, -21, 3, 4, -15, AIR);
        fill(-4, 5, -22, 4, 5, -14, bars);
        fill(-3, 0, -21, 3, 0, -15, Blocks.HAY_BLOCK.defaultBlockState());
        set(-3, 1, -21, Blocks.CAULDRON.defaultBlockState());

        // 8. ТЮРЬМА (x 8..20, z 6..18), 4 камеры
        room(8, 6, 20, 18, 5, brick, Blocks.STONE.defaultBlockState());
        fill(14, 1, 7, 14, 3, 17, bars);
        fill(9, 1, 12, 19, 3, 12, bars);
        fill(8, 1, 11, 8, 2, 13, bars);
        for (int[] c : new int[][]{{11, 9}, {17, 9}, {11, 15}, {17, 15}}) set(c[0], 5, c[1], lamp);

        // 9. БАНК (x -20..-8, z 6..18)
        room(-20, 6, -8, 18, 6, black, Blocks.SMOOTH_QUARTZ.defaultBlockState());
        fill(-8, 1, 11, -8, 3, 13, AIR);
        fill(-17, 1, 7, -17, 3, 17, bars);
        fill(-19, 1, 7, -18, 3, 17, gold);
        fill(-15, 1, 11, -15, 1, 11, Blocks.IRON_BLOCK.defaultBlockState());
        fill(-15, 1, 13, -15, 1, 13, Blocks.IRON_BLOCK.defaultBlockState());
        set(-15, 1, 12, ModBlocks.BANK_TELLER.get().defaultBlockState());
        for (int lx : new int[]{-12, -16}) for (int lz : new int[]{9, 15}) set(lx, 6, lz, lamp);

        // 10. вывески, король и зрители
        label(0, 1, 21, "§6§l⚜ КОРОЛЕВСТВО РАХИТА III ⚜");
        label(-6, 1, -10, "§4§lКОРОЛЕВСКИЕ ПОКОИ §c(ВХОД ЗАПРЕЩЁН)");
        label(13, 3, -12, "§c§lСЦЕНА ПОЗОРА");
        label(7, 1, 12, "§8§lТЮРЬМА");
        label(-7, 1, 12, "§e§lКОРОЛЕВСКИЙ БАНК");
        label(0, 6, -23, "§6§lХОМЯЧЬЕ КОЛЕСО");

        Villager king = npc(-19 + 0.5, 2, -10 + 0.5, -90f, "§6§l♛ Король Рахит III ♛");
        king.addTag("rakhit_king");
        for (int ax = 9; ax <= 19; ax += 2) npc(ax + 0.5, 1, -9 + 0.5, 180f, "§7Житель");
    }

    // ---------- helpers (координаты относительно центра) ----------

    private static void set(int dx, int dy, int dz, BlockState s) {
        L.setBlock(new BlockPos(X + dx, Y + dy, Z + dz), s, 3);
    }

    private static void fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockState s) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++)
                    set(x, y, z, s);
    }

    /** Полая коробка: стены+крыша из wall, пол из floor, внутри воздух. h - высота крыши над полом. */
    private static void room(int x1, int z1, int x2, int z2, int h, BlockState wall, BlockState floor) {
        fill(x1, 0, z1, x2, h, z2, wall);
        fill(x1 + 1, 1, z1 + 1, x2 - 1, h - 1, z2 - 1, AIR);
        fill(x1 + 1, 0, z1 + 1, x2 - 1, 0, z2 - 1, floor);
    }

    private static void label(double dx, double dy, double dz, String text) {
        ArmorStand a = EntityType.ARMOR_STAND.create(L);
        a.moveTo(X + dx + 0.5, Y + dy, Z + dz + 0.5, 0f, 0f);
        a.setInvisible(true);
        a.setNoGravity(true);
        a.setInvulnerable(true);
        a.setCustomName(Component.literal(text));
        a.setCustomNameVisible(true);
        L.addFreshEntity(a);
    }

    private static Villager npc(double dx, double dy, double dz, float yaw, String name) {
        Villager v = EntityType.VILLAGER.create(L);
        v.moveTo(X + dx, Y + dy, Z + dz, yaw, 0f);
        v.setNoAi(true);
        v.setInvulnerable(true);
        v.setPersistenceRequired();
        v.addTag("rakhit_npc");
        v.setCustomName(Component.literal(name));
        v.setCustomNameVisible(true);
        L.addFreshEntity(v);
        return v;
    }
}
