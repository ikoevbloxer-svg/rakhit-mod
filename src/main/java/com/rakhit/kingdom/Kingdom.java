package com.rakhit.kingdom;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Геометрия королевства. Все координаты считаются от центра (cx, cy, cz); cy - уровень пола. */
public final class Kingdom {
    /** Половина стороны территории: забор идёт по кольцу |dx|==R или |dz|==R. */
    public static final int R = 24;

    public static boolean ready;
    public static int cx, cy, cz;

    private Kingdom() {}

    public static void init(KingdomData d) {
        cx = d.cx; cy = d.cy; cz = d.cz;
        ready = true;
    }

    public static void reset() { ready = false; }

    /** Вся королевская территория (с забором): блоки здесь неразрушимы. */
    public static boolean contains(Level level, BlockPos pos) {
        return ready && level.dimension() == Level.OVERWORLD
                && Math.abs(pos.getX() - cx) <= R && Math.abs(pos.getZ() - cz) <= R
                && pos.getY() >= cy - 3 && pos.getY() <= cy + 40;
    }

    /** Королевские покои: вход строго запрещён. */
    public static boolean inRoyal(ServerPlayer p) {
        if (!ready || p.level().dimension() != Level.OVERWORLD) return false;
        BlockPos b = p.blockPosition();
        int dx = b.getX() - cx, dz = b.getZ() - cz;
        return dx >= -21 && dx <= -7 && dz >= -21 && dz <= -7 && b.getY() >= cy && b.getY() <= cy + 8;
    }

    public static Vec3 exit()  { return new Vec3(cx + 0.5, cy + 1, cz + R - 3 + 0.5); }
    public static Vec3 wheel() { return new Vec3(cx + 0.5, cy + 1, cz - 18 + 0.5); }
    public static Vec3 stage() { return new Vec3(cx + 13.5, cy + 3, cz - 16 + 0.5); }

    public static Vec3 cell(RandomSource r) {
        int[][] c = {{11, 9}, {17, 9}, {11, 15}, {17, 15}};
        int[] v = c[r.nextInt(c.length)];
        return new Vec3(cx + v[0] + 0.5, cy + 1, cz + v[1] + 0.5);
    }
}
