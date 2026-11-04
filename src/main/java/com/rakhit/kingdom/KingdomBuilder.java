package com.rakhit.kingdom;

import net.minecraft.core.BlockPos;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** Процедурно строит королевство 97x97 блоков (без NBT-структур, всё в коде). Kingdom.init(...) вызывается заранее. */
final class KingdomBuilder {
    private static ServerLevel L;
    private static int X, Y, Z;
    private static final int R = Kingdom.R;
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private KingdomBuilder() {}

    static void build(ServerLevel level) {
        L = level; X = Kingdom.cx; Y = Kingdom.cy; Z = Kingdom.cz;
        RandomSource rnd = RandomSource.create(X * 341873128712L + Z * 132897987541L);

        // 1. прогрузить чанки и убрать прежних обитателей и вывески
        for (int chx = (X - R - 2) >> 4; chx <= (X + R + 2) >> 4; chx++)
            for (int chz = (Z - R - 2) >> 4; chz <= (Z + R + 2) >> 4; chz++)
                level.getChunk(chx, chz);
        Citizens.purgeOld(level);
        Wheel.removeAll(level);

        terrain();
        fenceAndGate();
        roads();
        royalChambers();
        stage();
        wheelCage();
        prison();
        carcer();
        bank();
        pond();
        java.util.List<BlockPos> homes = houses(rnd);
        pigsty();
        barracks();
        decor(rnd);
        labels();
        populate(rnd, homes);
        Wheel.build(level);
    }

    // =============== местность ===============

    private static void terrain() {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
        for (int dx = -R; dx <= R; dx++) {
            for (int dz = -R; dz <= R; dz++) {
                int wx = X + dx, wz = Z + dz;
                int ground = L.getHeight(Heightmap.Types.OCEAN_FLOOR, wx, wz);
                for (int y = ground; y < Y; y++) L.setBlock(p.set(wx, y, wz), dirt, 2);
                int top = Math.max(ground, Y + 26);
                for (int y = Y + 1; y < top; y++) L.setBlock(p.set(wx, y, wz), AIR, 2);
                L.setBlock(p.set(wx, Y, wz), grass, 2);
            }
        }
    }

    private static void fenceAndGate() {
        BlockState fence = Blocks.NETHER_BRICK_FENCE.defaultBlockState();
        BlockState black = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        BlockState gold = Blocks.GOLD_BLOCK.defaultBlockState();
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
        // башни посередине сторон
        for (int[] t : new int[][]{{0, -R}, {-R, 0}, {R, 0}}) {
            fill(t[0], 1, t[1], t[0], 6, t[1], black);
            set(t[0], 7, t[1], Blocks.LANTERN.defaultBlockState());
        }
        // ворота на юге
        fill(-1, 1, R, 1, 5, R, AIR);
        fill(-2, 1, R, -2, 7, R, gold);
        fill(2, 1, R, 2, 7, R, gold);
        fill(-2, 7, R, 2, 7, R, gold);
        set(-3, 1, R, Blocks.LANTERN.defaultBlockState());
        set(3, 1, R, Blocks.LANTERN.defaultBlockState());
    }

    private static void roads() {
        BlockState brick = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState gravel = Blocks.GRAVEL.defaultBlockState();
        BlockState path = Blocks.POLISHED_ANDESITE.defaultBlockState();
        // кольцевая дорога вокруг центра (ширина 3 на |x| или |z| = 29..31)
        for (int x = -31; x <= 31; x++) {
            for (int z = -31; z <= 31; z++) {
                int m = Math.max(Math.abs(x), Math.abs(z));
                if (m < 29) continue;
                set(x, 0, z, m == 30 ? brick : gravel);
            }
        }
        // главный проспект от ворот до площади
        fill(-1, 0, 2, 1, 0, R, brick);
        fill(-2, 0, 2, -2, 0, R, gravel);
        fill(2, 0, 2, 2, 0, R, gravel);
        // площадь перед троном/колесом и дороги к банку, тюрьме, сцене, пруду, казарме
        fill(-6, 0, -12, 6, 0, 2, path);
        fill(1, 0, 11, 7, 0, 13, brick);
        fill(-7, 0, 11, -1, 0, 13, brick);
        fill(7, 0, -1, 28, 0, 1, brick);
        fill(-28, 0, -1, -7, 0, 1, brick);
        fill(12, 0, -11, 14, 0, -9, brick);
        // статуя Короля на площади
        fill(0, 1, -5, 0, 3, -5, Blocks.GOLD_BLOCK.defaultBlockState());
        set(0, 4, -5, Blocks.GLOWSTONE.defaultBlockState());
    }

    // =============== королевский квартал ===============

    private static void royalChambers() {
        BlockState gold = Blocks.GOLD_BLOCK.defaultBlockState();
        BlockState lamp = Blocks.SEA_LANTERN.defaultBlockState();
        room(-21, -21, -7, -7, 6, Blocks.QUARTZ_BLOCK.defaultBlockState(), Blocks.SMOOTH_QUARTZ.defaultBlockState());
        for (int sx : new int[]{-21, -7}) for (int sz : new int[]{-21, -7}) fill(sx, 1, sz, sx, 6, sz, gold);
        fill(-7, 1, -11, -7, 3, -9, AIR);
        fill(-19, 1, -11, -7, 1, -9, Blocks.RED_CARPET.defaultBlockState());
        fill(-20, 1, -11, -20, 4, -9, gold);   // спинка трона
        set(-19, 1, -10, gold);                // сиденье
        set(-19, 1, -11, Blocks.GOLD_BLOCK.defaultBlockState());
        set(-19, 1, -9, Blocks.GOLD_BLOCK.defaultBlockState());
        for (int lx : new int[]{-16, -12}) for (int lz : new int[]{-16, -12}) set(lx, 6, lz, lamp);
    }

    private static void stage() {
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
    }

    /** Клетка колеса: решётка высотой 8; внутри вращается Wheel. */
    private static void wheelCage() {
        BlockState bars = Blocks.IRON_BARS.defaultBlockState();
        fill(-5, 1, -22, 5, 8, -14, bars);
        fill(-4, 1, -21, 4, 8, -15, AIR);
        fill(-5, 9, -22, 5, 9, -14, bars);
        fill(-4, 0, -21, 4, 0, -15, Blocks.HAY_BLOCK.defaultBlockState());
        for (int sx : new int[]{-5, 5}) for (int sz : new int[]{-22, -14}) fill(sx, 1, sz, sx, 9, sz, Blocks.DARK_OAK_LOG.defaultBlockState());
        // миска с кормом и поилка для Борзого Хомы
        set(-3, 1, -20, Blocks.CAULDRON.defaultBlockState());
        set(3, 1, -20, Blocks.HAY_BLOCK.defaultBlockState());
        set(3, 2, -20, Blocks.CARROTS.defaultBlockState());
    }

    private static void prison() {
        BlockState brick = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState bars = Blocks.IRON_BARS.defaultBlockState();
        room(8, 6, 20, 18, 5, brick, Blocks.STONE.defaultBlockState());
        fill(14, 1, 7, 14, 3, 17, bars);
        fill(9, 1, 12, 19, 3, 12, bars);
        fill(8, 1, 11, 8, 2, 13, bars);
        for (int[] c : new int[][]{{11, 9}, {17, 9}, {11, 15}, {17, 15}}) set(c[0], 5, c[1], Blocks.SEA_LANTERN.defaultBlockState());
    }

    /** Подземный карцер под тюрьмой: люк с лестницей, мох, цепи и Вонючий житель посередине. */
    private static void carcer() {
        BlockState mossy = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
        fill(9, -9, 7, 19, -3, 17, mossy);
        fill(10, -8, 8, 18, -4, 16, AIR);
        fill(10, -9, 8, 18, -9, 16, Blocks.MUD_BRICKS.defaultBlockState());
        // шахта: люк в полу тюрьмы, лестница вдоль стены
        fill(18, -2, 7, 18, -1, 7, Blocks.STONE_BRICKS.defaultBlockState());
        fill(18, -3, 8, 18, -1, 8, AIR);
        for (int y = -8; y <= -1; y++) set(18, y, 8, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
        set(18, 0, 8, Blocks.IRON_TRAPDOOR.defaultBlockState());
        // обстановка
        for (int x = 11; x <= 17; x += 3) {
            set(x, -4, 8, Blocks.CHAIN.defaultBlockState());
            set(x, -5, 8, Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        }
        fill(10, -8, 15, 12, -8, 16, Blocks.HAY_BLOCK.defaultBlockState());
        set(17, -8, 15, Blocks.CAULDRON.defaultBlockState());
        set(10, -7, 12, Blocks.COBWEB.defaultBlockState());
        set(18, -7, 12, Blocks.COBWEB.defaultBlockState());
        fill(10, -8, 12, 10, -6, 12, Blocks.IRON_BARS.defaultBlockState());
        set(14, -9, 11, Blocks.SLIME_BLOCK.defaultBlockState());
    }

    private static void bank() {
        BlockState black = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        BlockState gold = Blocks.GOLD_BLOCK.defaultBlockState();
        BlockState bars = Blocks.IRON_BARS.defaultBlockState();
        room(-20, 6, -8, 18, 6, black, Blocks.SMOOTH_QUARTZ.defaultBlockState());
        fill(-8, 1, 11, -8, 3, 13, AIR);
        fill(-17, 1, 7, -17, 3, 17, bars);
        fill(-19, 1, 7, -18, 3, 17, gold);
        set(-15, 1, 11, Blocks.IRON_BLOCK.defaultBlockState());
        set(-15, 1, 13, Blocks.IRON_BLOCK.defaultBlockState());
        set(-15, 1, 12, ModBlocks.BANK_TELLER.get().defaultBlockState());
        for (int lx : new int[]{-12, -16}) for (int lz : new int[]{9, 15}) set(lx, 6, lz, Blocks.SEA_LANTERN.defaultBlockState());
    }

    // =============== пруд и причал ===============

    private static void pond() {
        for (int x = 34; x <= 47; x++) {
            for (int z = -9; z <= 9; z++) {
                double e = Math.pow((x - 40.5) / 6.5, 2) + Math.pow(z / 8.5, 2);
                if (e <= 1.0) {
                    set(x, 0, z, Blocks.WATER.defaultBlockState());
                    set(x, -1, z, Blocks.WATER.defaultBlockState());
                    set(x, -2, z, Blocks.WATER.defaultBlockState());
                    set(x, -3, z, Blocks.SAND.defaultBlockState());
                } else if (e <= 1.45) {
                    set(x, 0, z, (x + z) % 3 == 0 ? Blocks.GRAVEL.defaultBlockState() : Blocks.SAND.defaultBlockState());
                    if ((x * 7 + z * 3) % 5 == 0) set(x, 1, z, Blocks.SHORT_GRASS.defaultBlockState());
                }
            }
        }
        // причал: настил на уровне земли, перила и фонари
        for (int x = 32; x <= 41; x++) {
            for (int z = -1; z <= 1; z++) set(x, 0, z, Blocks.OAK_PLANKS.defaultBlockState());
            if (x % 3 == 0) {
                set(x, 1, -1, Blocks.OAK_FENCE.defaultBlockState());
                set(x, 1, 1, Blocks.OAK_FENCE.defaultBlockState());
            }
        }
        set(41, 1, -1, Blocks.OAK_FENCE.defaultBlockState());
        set(41, 2, -1, Blocks.LANTERN.defaultBlockState());
        set(41, 1, 1, Blocks.OAK_FENCE.defaultBlockState());
        set(41, 2, 1, Blocks.LANTERN.defaultBlockState());
        set(34, 1, -2, Blocks.BARREL.defaultBlockState());
        addLabel(label(36.5, 2.2, 2.5, "§b§lКОРОЛЕВСКАЯ РЫБАЛКА"));
    }

    // =============== дома жителей ===============

    private static java.util.List<BlockPos> houses(RandomSource rnd) {
        java.util.List<BlockPos> homes = new java.util.ArrayList<>();
        int n = 0;
        for (int x : new int[]{-36, -24, -12, 12, 24, 36}) {
            homes.add(HouseBuilder.build(L, X, Y, Z, x, 37, Direction.NORTH, rnd.nextInt(HouseBuilder.STYLES), rnd));
            homes.add(HouseBuilder.build(L, X, Y, Z, x, -37, Direction.SOUTH, rnd.nextInt(HouseBuilder.STYLES), rnd));
        }
        for (int z : new int[]{-24, -14, 14, 24}) {
            homes.add(HouseBuilder.build(L, X, Y, Z, 37, z, Direction.WEST, rnd.nextInt(HouseBuilder.STYLES), rnd));
            homes.add(HouseBuilder.build(L, X, Y, Z, -37, z, Direction.EAST, rnd.nextInt(HouseBuilder.STYLES), rnd));
        }
        return homes;
    }

    // =============== свинарник священных свиней ===============

    private static void pigsty() {
        BlockState fence = Blocks.OAK_FENCE.defaultBlockState();
        fill(-6, 0, -45, 6, 0, -35, Blocks.MUD.defaultBlockState());
        for (int i = -7; i <= 7; i++) {
            set(i, 1, -46, fence);
            if (Math.abs(i) > 1) set(i, 1, -34, fence);
        }
        for (int z = -46; z <= -34; z++) { set(-7, 1, z, fence); set(7, 1, z, fence); }
        set(-1, 1, -34, Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING, Direction.SOUTH));
        set(0, 1, -34, Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING, Direction.SOUTH));
        set(1, 1, -34, Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING, Direction.SOUTH));
        // навес, корыто и сено
        fill(-5, 1, -45, -5, 3, -45, fence); fill(5, 1, -45, 5, 3, -45, fence);
        fill(-5, 4, -45, 5, 4, -42, Blocks.SPRUCE_SLAB.defaultBlockState());
        fill(-4, 1, -45, 4, 1, -45, Blocks.HAY_BLOCK.defaultBlockState());
        fill(-3, 0, -39, 3, 0, -39, Blocks.WATER.defaultBlockState());
        addLabel(label(0.5, 3.2, -33.5, "§d§l⚜ СВЯЩЕННЫЕ СВИНЬИ КОРОЛЯ ⚜"));
        for (int i = 0; i < 6; i++) {
            Pig pig = EntityType.PIG.create(L);
            if (pig == null) continue;
            pig.moveTo(X + (-4 + (i % 3) * 4) + 0.5, Y + 1, Z - 42 + (i / 3) * 4 + 0.5, L.random.nextFloat() * 360f, 0f);
            pig.setPersistenceRequired();
            pig.setCustomName(Component.literal("§dСвященная свинья"));
            pig.setCustomNameVisible(true);
            L.addFreshEntity(pig);
        }
    }

    // =============== казармы королевской гвардии ===============

    private static void barracks() {
        BlockState wall = Blocks.STONE_BRICKS.defaultBlockState();
        room(-46, -9, -34, 9, 5, wall, Blocks.POLISHED_ANDESITE.defaultBlockState());
        fill(-34, 1, -1, -34, 3, 1, AIR);                    // вход с востока
        for (int z = -8; z <= 8; z += 4) { set(-46, 3, z, Blocks.IRON_BARS.defaultBlockState()); set(-46, 2, z, Blocks.IRON_BARS.defaultBlockState()); }
        // зубцы на крыше
        for (int x = -46; x <= -34; x += 2) { set(x, 6, -9, wall); set(x, 6, 9, wall); }
        for (int z = -9; z <= 9; z += 2) { set(-46, 6, z, wall); set(-34, 6, z, wall); }
        // красные полотна на фасаде
        for (int z : new int[]{-6, 6}) {
            fill(-33, 2, z, -33, 4, z, Blocks.RED_WOOL.defaultBlockState());
            set(-33, 5, z, Blocks.GOLD_BLOCK.defaultBlockState());
        }
        // койки вдоль северной и южной стен
        Block[] beds = {Blocks.RED_BED, Blocks.RED_BED, Blocks.BLACK_BED, Blocks.BLACK_BED};
        for (int i = 0; i < 4; i++) {
            int x = -43 + i * 3;
            setBed(beds[i], x, -7, Direction.NORTH);
            setBed(beds[i], x, 7, Direction.SOUTH);
        }
        // оружейная: кузнечные блоки и бочки в центре
        set(-38, 1, 0, Blocks.ANVIL.defaultBlockState());
        set(-39, 1, 0, Blocks.GRINDSTONE.defaultBlockState());
        set(-40, 1, 0, Blocks.BARREL.defaultBlockState());
        set(-41, 1, 0, Blocks.FLETCHING_TABLE.defaultBlockState());
        set(-42, 1, 0, Blocks.SMITHING_TABLE.defaultBlockState());
        for (int lx : new int[]{-42, -38}) for (int lz : new int[]{-5, 5}) set(lx, 5, lz, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        addLabel(label(-33.0, 4.2, 0.5, "§4§lКАЗАРМЫ КОРОЛЕВСКОЙ ГВАРДИИ"));
    }

    /** Кровать: ноги в (x,z), изголовье - дальше по направлению toHead. */
    private static void setBed(Block bed, int x, int z, Direction toHead) {
        BlockState foot = bed.defaultBlockState().setValue(BedBlock.FACING, toHead).setValue(BedBlock.PART, BedPart.FOOT);
        BlockState head = bed.defaultBlockState().setValue(BedBlock.FACING, toHead).setValue(BedBlock.PART, BedPart.HEAD);
        set(x, 1, z, foot);
        set(x + toHead.getStepX(), 1, z + toHead.getStepZ(), head);
    }

    // =============== украшения ===============

    private static void decor(RandomSource rnd) {
        // фонарные столбы вдоль внутренней стороны кольцевой дороги
        for (int i = -20; i <= 20; i += 10) {
            lamp(28, i); lamp(-28, i);
            if (i != 0) { lamp(i, 28); lamp(i, -28); }
        }
        // фонари вдоль проспекта
        for (int z = 6; z <= 44; z += 8) { lamp(-3, z); lamp(3, z); }
        // деревья и клумбы на газонах внутреннего кольца
        int[][] trees = {{-25, 24}, {25, 24}, {-25, -26}, {25, -26}, {-26, 8}, {26, 10}, {-24, -6}, {24, -7}};
        for (int[] t : trees) oak(t[0], t[1], rnd);
        BlockState[] fl = {Blocks.POPPY.defaultBlockState(), Blocks.DANDELION.defaultBlockState(), Blocks.CORNFLOWER.defaultBlockState(),
                Blocks.OXEYE_DAISY.defaultBlockState(), Blocks.PINK_TULIP.defaultBlockState()};
        for (int i = 0; i < 160; i++) {
            int x = rnd.nextInt(53) - 26, z = rnd.nextInt(53) - 26;
            if (Math.abs(x) < 24 && Math.abs(z) < 24) continue;
            BlockPos bp = new BlockPos(X + x, Y + 1, Z + z);
            if (L.getBlockState(bp.below()).is(Blocks.GRASS_BLOCK) && L.getBlockState(bp).isAir())
                L.setBlock(bp, fl[rnd.nextInt(fl.length)], 3);
        }
        // фонтан на южной площади
        fill(-2, 0, 20, 2, 0, 24, Blocks.STONE_BRICKS.defaultBlockState());
        fill(-1, 0, 21, 1, 0, 23, Blocks.WATER.defaultBlockState());
        set(0, 1, 22, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
        set(0, 2, 22, Blocks.WATER.defaultBlockState());
    }

    private static void lamp(int dx, int dz) {
        set(dx, 1, dz, Blocks.SPRUCE_FENCE.defaultBlockState());
        set(dx, 2, dz, Blocks.SPRUCE_FENCE.defaultBlockState());
        set(dx, 3, dz, Blocks.LANTERN.defaultBlockState());
    }

    private static void oak(int dx, int dz, RandomSource rnd) {
        BlockState log = Blocks.OAK_LOG.defaultBlockState();
        BlockState leaf = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        int h = 4 + rnd.nextInt(2);
        for (int y = 1; y <= h; y++) set(dx, y, dz, log);
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) for (int y = h - 1; y <= h + 2; y++) {
            int r = Math.abs(x) + Math.abs(z) + Math.max(0, y - h);
            if (r > 3 || (x == 0 && z == 0 && y <= h)) continue;
            BlockPos bp = new BlockPos(X + dx + x, Y + y, Z + dz + z);
            if (L.getBlockState(bp).isAir()) L.setBlock(bp, leaf, 3);
        }
    }

    // =============== вывески ===============

    private static void labels() {
        addLabel(label(0.5, 1, 52.5, "§6§l⚜ КОРОЛЕВСТВО РАХИТА III ⚜"));
        addLabel(label(0.5, 6.4, R + 0.5, "§6§l⚜ КОРОЛЕВСТВО РАХИТА III ⚜"));
        addLabel(label(-6, 1, -10, "§4§lКОРОЛЕВСКИЕ ПОКОИ §c(ВХОД ЗАПРЕЩЁН)"));
        addLabel(label(13.5, 3, -12, "§c§lСЦЕНА ПОЗОРА"));
        addLabel(label(7, 1, 12, "§8§lТЮРЬМА §7(карцер - под полом)"));
        addLabel(label(-7, 1, 12, "§e§lКОРОЛЕВСКИЙ БАНК"));
        addLabel(label(0.5, 10.0, -17.5, "§6§lХОМЯЧЬЕ КОЛЕСО"));
        addLabel(label(0.5, 6.0, 20.5, "§b§lФОНТАН ЕГО ВЕЛИЧЕСТВА"));
    }

    // =============== население ===============

    private static void populate(RandomSource rnd, java.util.List<BlockPos> homes) {
        // Король на троне (сидит на невидимой подставке)
        Vec3 seat = Kingdom.throneSeat();
        ArmorStand stand = EntityType.ARMOR_STAND.create(L);
        if (stand != null) {
            stand.moveTo(seat.x, seat.y, seat.z, -90f, 0f);
            stand.setInvisible(true);
            stand.setNoGravity(true);
            stand.setInvulnerable(true);
            stand.addTag("rakhit_seat");
            L.addFreshEntity(stand);
            KingdomNpc king = Citizens.spawnSpecial(L, KingdomNpc.Role.KING, seat.x, seat.y, seat.z, -90f, "§6§l♛ Король Рахит III ♛");
            if (king != null) king.startRiding(stand, true);
        }
        // Борзый Хома в колесе
        Vec3 w = Kingdom.wheel();
        Citizens.spawnSpecial(L, KingdomNpc.Role.HAMA, w.x - 1.8, w.y, w.z, 180f, "§6§lБорзый Хома");
        // Вонючий житель в карцере
        Vec3 st = Kingdom.stinker();
        Citizens.spawnSpecial(L, KingdomNpc.Role.STINKER, st.x, st.y, st.z, 0f, "§2§lВонючий житель");
        // жители по домам (1-2 в каждом)
        int serial = 0;
        for (BlockPos home : homes) {
            int count = 1 + rnd.nextInt(2);
            for (int i = 0; i < count; i++) {
                Citizens.spawn(L, home.getX() + 0.5 + i, home.getY(), home.getZ() + 0.5, home, serial++);
            }
        }
        // королевская гвардия: 8 офицеров (у казарм, у ворот, на площади)
        double[][] gp = {{-31, 3}, {-31, -3}, {0, 40}, {2, 44}, {-2, 44}, {0, 8}, {10, -8}, {-10, 10}};
        for (int i = 0; i < gp.length; i++) {
            Citizens.spawnGuard(L, X + gp[i][0] + 0.5, Y + 1, Z + gp[i][1] + 0.5, "Офицер гвардии №" + (i + 1), false);
        }
    }

    // =============== помощники (координаты относительно центра) ===============

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

    private static void addLabel(ArmorStand a) { L.addFreshEntity(a); }

    private static ArmorStand label(double dx, double dy, double dz, String text) {
        ArmorStand a = EntityType.ARMOR_STAND.create(L);
        a.moveTo(X + dx + 0.5, Y + dy, Z + dz + 0.5, 0f, 0f);
        a.setInvisible(true);
        a.setNoGravity(true);
        a.setInvulnerable(true);
        a.addTag("rakhit_label");
        a.setCustomName(Component.literal(text));
        a.setCustomNameVisible(true);
        return a;
    }
}
