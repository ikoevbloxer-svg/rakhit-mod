package com.rakhit.kingdom;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Геометрия королевства. Все координаты считаются от центра (cx, cy, cz); cy - уровень пола.
 * Территория - квадрат со стороной 2R+1 (R = 48), обнесённый забором. Ось Z на юг, ворота на юге (z = +R).
 */
public final class Kingdom {
    /** Половина стороны территории: забор идёт по кольцу |dx|==R или |dz|==R. */
    public static final int R = 48;
    /** Версия планировки. При повышении старые миры достраиваются заново (см. KingdomEvents). */
    public static final int LAYOUT_VERSION = 2;

    public static boolean ready;
    public static int cx, cy, cz;

    private Kingdom() {}

    public static void init(KingdomData d) {
        cx = d.cx; cy = d.cy; cz = d.cz;
        ready = true;
    }

    public static void reset() { ready = false; }

    /** Вся королевская территория (с забором и подземельем): блоки здесь неразрушимы. */
    public static boolean contains(Level level, BlockPos pos) {
        return ready && level.dimension() == Level.OVERWORLD
                && Math.abs(pos.getX() - cx) <= R && Math.abs(pos.getZ() - cz) <= R
                && pos.getY() >= cy - 14 && pos.getY() <= cy + 40;
    }

    /** Королевские покои: вход строго запрещён. */
    public static boolean inRoyal(ServerPlayer p) {
        if (!ready || p.level().dimension() != Level.OVERWORLD) return false;
        BlockPos b = p.blockPosition();
        int dx = b.getX() - cx, dz = b.getZ() - cz;
        return dx >= -21 && dx <= -7 && dz >= -21 && dz <= -7 && b.getY() >= cy && b.getY() <= cy + 8;
    }

    /** Расстояние по горизонтали от центра королевства. */
    public static double distFromCenter(net.minecraft.world.entity.Entity e) {
        return Math.sqrt((e.getX() - cx - 0.5) * (e.getX() - cx - 0.5) + (e.getZ() - cz - 0.5) * (e.getZ() - cz - 0.5));
    }

    public static Vec3 at(double dx, double dy, double dz) { return new Vec3(cx + dx, cy + dy, cz + dz); }

    // ---------- точки наказаний ----------
    public static Vec3 exit()   { return at(0.5, 1, R - 3 + 0.5); }
    public static Vec3 wheel()  { return at(0.5, 1, -17.5); }
    public static Vec3 stage()  { return at(13.5, 3, -15.5); }
    /** Конец причала на пруду: смотреть на восток. */
    public static Vec3 pier()   { return at(40.5, 1, 0.5); }
    /** Подземный карцер (пол карцера на 8 блоков ниже пола королевства). */
    public static Vec3 carcer() { return at(14.5, -8, 13.5); }
    public static Vec3 stinker() { return at(14.5, -8, 11.5); }
    /** Тронный зал: зрители выстраиваются перед троном. */
    public static Vec3 throneHall(RandomSource r) { return at(-16 + r.nextInt(5) + 0.5, 1, -15 + r.nextInt(7) + 0.5); }
    public static Vec3 throneSeat() { return at(-18.5, 2.35, -9.5); }

    public static Vec3 cell(RandomSource r) {
        int[][] c = {{11, 9}, {17, 9}, {11, 15}, {17, 15}};
        int[] v = c[r.nextInt(c.length)];
        return at(v[0] + 0.5, 1, v[1] + 0.5);
    }

    // ---------- хомячье колесо ----------
    public static Vec3 wheelCenter() { return at(0.5, 4.2, -17.5); }

    // ---------- патрульные точки (смещения относительно центра) ----------
    private static final int[][] PATROL = {
            {0, 40}, {24, 31}, {31, 16}, {31, -16}, {24, -31}, {8, -29}, {-8, -29}, {-24, -31},
            {-31, -16}, {-31, 16}, {-24, 31}, {0, 31}, {0, 6}, {-4, 12}, {4, 12}, {13, -9}, {0, 20}
    };

    public static Vec3[] patrolPoints() {
        Vec3[] v = new Vec3[PATROL.length];
        for (int i = 0; i < PATROL.length; i++) v[i] = at(PATROL[i][0] + 0.5, 1, PATROL[i][1] + 0.5);
        return v;
    }
}
