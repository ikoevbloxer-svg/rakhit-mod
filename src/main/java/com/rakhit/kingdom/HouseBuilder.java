package com.rakhit.kingdom;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * Процедурные дома жителей: 6 архитектурных стилей, любая ориентация, двускатная крыша, окна, дверь, кровать,
 * сундук, стол, печь, фонари и палисадник. Локальные координаты: u - вдоль фасада, v - вглубь (v=+hd - фасад с дверью).
 */
final class HouseBuilder {
    static final int HW = 4, HD = 3;
    static final int STYLES = 6;

    private record Palette(Block wall, Block base, Block corner, Block roofStair, Block roofFull, Block floor, Block door, boolean beams) {}

    private static Palette palette(int style) {
        return switch (style) {
            case 0 -> new Palette(Blocks.OAK_PLANKS, Blocks.COBBLESTONE, Blocks.OAK_LOG, Blocks.COBBLESTONE_STAIRS, Blocks.COBBLESTONE, Blocks.SPRUCE_PLANKS, Blocks.OAK_DOOR, false);
            case 1 -> new Palette(Blocks.WHITE_TERRACOTTA, Blocks.STONE_BRICKS, Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_DOOR, true);
            case 2 -> new Palette(Blocks.BRICKS, Blocks.STONE_BRICKS, Blocks.STRIPPED_SPRUCE_LOG, Blocks.BRICK_STAIRS, Blocks.BRICKS, Blocks.OAK_PLANKS, Blocks.SPRUCE_DOOR, false);
            case 3 -> new Palette(Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_LOG, Blocks.STRIPPED_SPRUCE_LOG, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_PLANKS, Blocks.OAK_PLANKS, Blocks.SPRUCE_DOOR, true);
            case 4 -> new Palette(Blocks.SMOOTH_SANDSTONE, Blocks.CUT_SANDSTONE, Blocks.CHISELED_SANDSTONE, Blocks.SANDSTONE_STAIRS, Blocks.SANDSTONE, Blocks.SMOOTH_SANDSTONE, Blocks.JUNGLE_DOOR, false);
            default -> new Palette(Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS, Blocks.POLISHED_ANDESITE, Blocks.STONE_BRICK_STAIRS, Blocks.STONE_BRICKS, Blocks.SPRUCE_PLANKS, Blocks.ACACIA_DOOR, false);
        };
    }

    private final ServerLevel L;
    private final int X, Y, Z;     // центр королевства
    private final int ox, oz;      // центр дома (смещение)
    private final Direction door;  // куда смотрит фасад
    private final Palette p;
    private final int style;
    private final int H;           // высота стен

    private HouseBuilder(ServerLevel l, int x, int y, int z, int ox, int oz, Direction door, int style) {
        this.L = l; this.X = x; this.Y = y; this.Z = z; this.ox = ox; this.oz = oz; this.door = door;
        this.style = style; this.p = palette(style);
        this.H = style == 5 ? 6 : 4;
    }

    /** Строит дом; возвращает точку у кровати (для жителя) в мировых координатах. */
    static BlockPos build(ServerLevel l, int x, int y, int z, int ox, int oz, Direction door, int style, RandomSource rnd) {
        return new HouseBuilder(l, x, y, z, ox, oz, door, style).run(rnd);
    }

    // ---------- преобразование локальных координат ----------

    private int wx(int u, int v) {
        return switch (door) { case SOUTH -> u; case NORTH -> -u; case EAST -> v; default -> -v; };
    }

    private int wz(int u, int v) {
        return switch (door) { case SOUTH -> v; case NORTH -> -v; case EAST -> -u; default -> u; };
    }

    private Direction dir(int du, int dv) {
        int dx = wx(du, dv), dz = wz(du, dv);
        if (dx > 0) return Direction.EAST;
        if (dx < 0) return Direction.WEST;
        return dz > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    private BlockPos pos(int u, int y, int v) { return new BlockPos(X + ox + wx(u, v), Y + y, Z + oz + wz(u, v)); }

    private void set(int u, int y, int v, BlockState s) { L.setBlock(pos(u, y, v), s, 3); }

    private void set(int u, int y, int v, Block b) { set(u, y, v, b.defaultBlockState()); }

    private void fill(int u1, int y1, int v1, int u2, int y2, int v2, Block b) {
        for (int u = Math.min(u1, u2); u <= Math.max(u1, u2); u++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int v = Math.min(v1, v2); v <= Math.max(v1, v2); v++) set(u, y, v, b);
    }

    private BlockState stair(Block b, Direction ascend) {
        return b.defaultBlockState().setValue(StairBlock.FACING, ascend).setValue(StairBlock.HALF, Half.BOTTOM);
    }

    // ---------- сам дом ----------

    private BlockPos run(RandomSource rnd) {
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState glass = Blocks.GLASS_PANE.defaultBlockState();

        // пол
        fill(-HW, 0, -HD, HW, 0, HD, p.floor());
        // стены
        for (int u = -HW; u <= HW; u++) {
            for (int v = -HD; v <= HD; v++) {
                boolean edge = Math.abs(u) == HW || Math.abs(v) == HD;
                if (!edge) continue;
                boolean corner = Math.abs(u) == HW && Math.abs(v) == HD;
                for (int y = 1; y <= H; y++) {
                    Block b = corner ? p.corner() : (y == 1 ? p.base() : p.wall());
                    if (!corner && p.beams() && y == H) b = p.corner();
                    if (!corner && style == 1 && (Math.abs(u) == 2 || Math.abs(v) == 1 && Math.abs(u) == HW) && y > 1) b = p.corner();
                    set(u, y, v, b);
                }
            }
        }
        // внутренность
        for (int u = -HW + 1; u <= HW - 1; u++)
            for (int v = -HD + 1; v <= HD - 1; v++)
                for (int y = 1; y <= H; y++) set(u, y, v, air);
        // потолок и карман над стеной
        fill(-HW, H + 1, -HD, HW, H + 1, HD, p.wall() == Blocks.WHITE_TERRACOTTA ? Blocks.DARK_OAK_PLANKS : p.floor());

        // окна
        int wy = style == 5 ? 5 : 2;
        for (int v : new int[]{-1, 1}) {
            for (int s : new int[]{-1, 1}) {
                set(s * HW, 2, v, glass); set(s * HW, 3, v, glass);
                if (style == 5) set(s * HW, wy, v, glass);
            }
        }
        for (int s : new int[]{-1, 1}) {
            set(s * 2, 2, -HD, glass); set(s * 2, 3, -HD, glass);
            set(s * 2, 2, HD, glass); set(s * 2, 3, HD, glass);
        }

        // дверь на фасаде
        Direction out = dir(0, 1);
        BlockState lower = p.door().defaultBlockState().setValue(DoorBlock.FACING, out).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        BlockState upper = p.door().defaultBlockState().setValue(DoorBlock.FACING, out).setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER);
        set(0, 1, HD, lower);
        set(0, 2, HD, upper);
        set(0, 3, HD, p.corner());

        // крыша: двускатная, конёк вдоль u
        Direction towardFront = dir(0, 1), towardBack = dir(0, -1);
        for (int k = 0; k <= HD + 1; k++) {
            int y = H + 1 + k;
            int vb = -(HD + 1 - k), vf = HD + 1 - k;
            for (int u = -HW - 1; u <= HW + 1; u++) {
                if (vb == vf) {
                    set(u, y, 0, p.roofFull());
                } else {
                    set(u, y, vb, stair(p.roofStair(), towardFront));   // задний свес поднимается к центру (к фасаду)
                    set(u, y, vf, stair(p.roofStair(), towardBack));
                }
            }
            // фронтоны (треугольники стен на торцах)
            if (k >= 1 && vb < vf) {
                for (int v = vb + 1; v <= vf - 1; v++) {
                    set(-HW, y, v, p.wall());
                    set(HW, y, v, p.wall());
                }
            }
        }
        // труба у кирпичного дома
        if (style == 2) fill(2, H + 1, -1, 2, H + HD + 3, -1, Blocks.BRICKS);
        if (style == 2) set(2, H + HD + 4, -1, Blocks.CAMPFIRE);

        // интерьер
        BlockPos bedside = furnish(rnd);

        // фасад: фонари на столбиках, дорожка, цветы
        for (int s : new int[]{-1, 1}) {
            set(s * 2, 1, HD + 1, Blocks.SPRUCE_FENCE);
            set(s * 2, 2, HD + 1, Blocks.LANTERN);
        }
        Block[] flowers = {Blocks.POPPY, Blocks.DANDELION, Blocks.CORNFLOWER, Blocks.AZURE_BLUET, Blocks.OXEYE_DAISY, Blocks.ALLIUM};
        for (int v = HD + 1; v <= HD + 3; v++) set(0, 0, v, Blocks.DIRT_PATH);
        for (int i = 0; i < 14; i++) {
            int u = rnd.nextInt(2 * HW + 5) - HW - 2;
            int v = HD + 1 + rnd.nextInt(2);
            if (Math.abs(u) <= 1) continue;
            set(u, 1, v, flowers[rnd.nextInt(flowers.length)]);
        }
        // дерево и грядки на заднем дворе
        tree(HW - 1, -HD - 3 - rnd.nextInt(2));
        for (int u = -HW; u <= -1; u++) {
            set(u, 0, -HD - 2, Blocks.FARMLAND);
            set(u, 0, -HD - 3, Blocks.WATER);
            set(u, 1, -HD - 2, rnd.nextBoolean() ? Blocks.WHEAT : Blocks.CARROTS);
        }
        return bedside;
    }

    private BlockPos furnish(RandomSource rnd) {
        Direction front = dir(0, 1);
        // кровать у задней стены (изголовье к стене)
        Direction toHead = dir(0, -1);
        Block[] beds = {Blocks.RED_BED, Blocks.BLUE_BED, Blocks.GREEN_BED, Blocks.YELLOW_BED, Blocks.WHITE_BED, Blocks.CYAN_BED};
        Block bed = beds[rnd.nextInt(beds.length)];
        set(-HW + 1, 1, -1, bed.defaultBlockState().setValue(BedBlock.FACING, toHead).setValue(BedBlock.PART, BedPart.FOOT));
        set(-HW + 1, 1, -2, bed.defaultBlockState().setValue(BedBlock.FACING, toHead).setValue(BedBlock.PART, BedPart.HEAD));
        // сундук, верстак, печь у задней стены
        set(HW - 1, 1, -2, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, front));
        set(HW - 2, 1, -2, Blocks.CRAFTING_TABLE);
        set(HW - 3, 1, -2, Blocks.FURNACE.defaultBlockState().setValue(AbstractFurnaceBlock.FACING, front));
        // стол: забор + плита, рядом стулья-ступени
        set(2, 1, 1, Blocks.OAK_FENCE);
        set(2, 2, 1, Blocks.OAK_PRESSURE_PLATE);
        set(1, 1, 1, stair(Blocks.OAK_STAIRS, dir(1, 0)).setValue(StairBlock.FACING, dir(1, 0)));
        // ковёр, книжная полка и фонарь под потолком
        fill(-1, 1, 0, 1, 1, 0, Blocks.RED_CARPET);
        set(-HW + 1, 1, HD - 1, Blocks.BOOKSHELF);
        set(-HW + 1, 2, HD - 1, Blocks.BOOKSHELF);
        set(0, H, -1, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        set(HW - 1, 3, 1, Blocks.POTTED_FERN);
        return pos(-HW + 2, 1, -1);
    }

    private void tree(int u, int v) {
        BlockState log = Blocks.OAK_LOG.defaultBlockState();
        BlockState leaf = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        set(u, 0, v, Blocks.DIRT);
        for (int y = 1; y <= 4; y++) set(u, y, v, log);
        for (int du = -2; du <= 2; du++)
            for (int dv = -2; dv <= 2; dv++)
                for (int y = 3; y <= 6; y++) {
                    int r = Math.abs(du) + Math.abs(dv) + (y - 4 > 0 ? (y - 4) : 0);
                    if (r > 3 || (du == 0 && dv == 0 && y <= 4)) continue;
                    BlockPos bp = pos(u + du, y, v + dv);
                    if (L.getBlockState(bp).isAir()) L.setBlock(bp, leaf, 3);
                }
    }
}
