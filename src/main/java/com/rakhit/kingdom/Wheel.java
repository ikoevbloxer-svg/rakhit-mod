package com.rakhit.kingdom;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Настоящее вращающееся хомячье колесо: набор block_display-сущностей, которые каждые 10 тиков
 * плавно (через interpolation) поворачиваются вокруг оси X. Пока в колесе бежит наказанный - крутится быстрее.
 */
public final class Wheel {
    private record Part(String block, double cx, double cy, double cz, double sx, double sy, double sz, double phi) {}

    private static final List<Part> PARTS = new ArrayList<>();
    private static final String TAG = "rakhit_wheel";
    private static final int PERIOD = 10;
    private static double theta;

    static {
        final double rc = 3.0;
        // боковые кольца: 16 досок-сегментов на каждое
        int n = 16;
        double seg = 2 * rc * Math.sin(Math.PI / n) + 0.12;
        for (double side : new double[]{-2.5, 2.5}) {
            for (int k = 0; k < n; k++) {
                double phi = 2 * Math.PI * k / n;
                PARTS.add(new Part("minecraft:spruce_planks", side, rc * Math.cos(phi), rc * Math.sin(phi), 0.3, 0.4, seg, phi));
            }
        }
        // перекладины, по которым бегут хомяки
        for (int k = 0; k < 12; k++) {
            double phi = 2 * Math.PI * k / 12 + Math.PI / 12;
            PARTS.add(new Part("minecraft:iron_block", 0, rc * Math.cos(phi), rc * Math.sin(phi), 5.3, 0.22, 0.22, phi));
        }
        // спицы и ступицы
        for (double side : new double[]{-2.5, 2.5}) {
            for (int k = 0; k < 6; k++) {
                double phi = Math.PI * k / 3;
                PARTS.add(new Part("minecraft:dark_oak_planks", side, rc / 2 * Math.cos(phi), rc / 2 * Math.sin(phi), 0.2, rc, 0.2, phi));
            }
            PARTS.add(new Part("minecraft:gold_block", side, 0, 0, 0.5, 0.5, 0.5, 0));
        }
    }

    private Wheel() {}

    // ---------- построение ----------

    public static void build(ServerLevel lvl) {
        removeAll(lvl);
        Vec3 c = Kingdom.wheelCenter();
        for (int i = 0; i < PARTS.size(); i++) {
            Display.BlockDisplay d = EntityType.BLOCK_DISPLAY.create(lvl);
            if (d == null) continue;
            d.setPos(c.x, c.y, c.z);
            d.addTag(TAG);
            d.addTag("wp" + i);
            Part p = PARTS.get(i);
            CompoundTag patch = patch(p, theta, 0);
            CompoundTag bs = new CompoundTag();
            bs.putString("Name", p.block());
            patch.put("block_state", bs);
            patch.putFloat("view_range", 2.0f);
            merge(d, patch);
            lvl.addFreshEntity(d);
        }
    }

    public static void removeAll(ServerLevel lvl) {
        Vec3 c = Kingdom.wheelCenter();
        AABB box = new AABB(c.x - 8, c.y - 8, c.z - 8, c.x + 8, c.y + 8, c.z + 8);
        for (Display.BlockDisplay d : lvl.getEntitiesOfClass(Display.BlockDisplay.class, box, e -> e.getTags().contains(TAG)))
            d.discard();
    }

    // ---------- анимация ----------

    public static void tick(MinecraftServer s) {
        if (!Kingdom.ready || s.getTickCount() % PERIOD != 0) return;
        ServerLevel lvl = s.overworld();
        Vec3 c = Kingdom.wheelCenter();
        boolean near = false;
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            if (p.level() == lvl && p.distanceToSqr(c) < 80 * 80) { near = true; break; }
        }
        if (!near) return;

        double step = Math.toRadians(Justice.wheelBusy() ? 35 : 15);
        theta += step;
        if (theta > 4 * Math.PI) theta -= 4 * Math.PI;

        AABB box = new AABB(c.x - 8, c.y - 8, c.z - 8, c.x + 8, c.y + 8, c.z + 8);
        List<Display.BlockDisplay> list = lvl.getEntitiesOfClass(Display.BlockDisplay.class, box, e -> e.getTags().contains(TAG));
        if (list.size() != PARTS.size()) {
            if (s.getTickCount() % 200 == 0) build(lvl);
            return;
        }
        for (Display.BlockDisplay d : list) {
            int idx = -1;
            for (String t : d.getTags()) {
                if (t.startsWith("wp")) {
                    try { idx = Integer.parseInt(t.substring(2)); } catch (NumberFormatException ignored) {}
                }
            }
            if (idx < 0 || idx >= PARTS.size()) continue;
            merge(d, patch(PARTS.get(idx), theta, PERIOD));
        }
    }

    // ---------- математика трансформации ----------

    /** Трансформация части колеса при повороте колеса на угол theta вокруг оси X. */
    private static CompoundTag patch(Part p, double th, int duration) {
        double ct = Math.cos(th), st = Math.sin(th);
        // центр элемента в мире (относительно оси колеса): R(theta) * c0
        double wx = p.cx();
        double wy = p.cy() * ct - p.cz() * st;
        double wz = p.cy() * st + p.cz() * ct;
        // собственный поворот элемента: theta + phi
        double a = th + p.phi();
        double ca = Math.cos(a), sa = Math.sin(a);
        double hx = p.sx() / 2, hy = p.sy() / 2, hz = p.sz() / 2;
        double qx = hx;
        double qy = hy * ca - hz * sa;
        double qz = hy * sa + hz * ca;
        double tx = wx - qx, ty = wy - qy, tz = wz - qz;

        CompoundTag tr = new CompoundTag();
        tr.put("left_rotation", floats((float) Math.sin(a / 2), 0f, 0f, (float) Math.cos(a / 2)));
        tr.put("right_rotation", floats(0f, 0f, 0f, 1f));
        tr.put("scale", floats((float) p.sx(), (float) p.sy(), (float) p.sz()));
        tr.put("translation", floats((float) tx, (float) ty, (float) tz));

        CompoundTag out = new CompoundTag();
        out.put("transformation", tr);
        out.putInt("start_interpolation", 0);
        out.putInt("interpolation_duration", duration);
        return out;
    }

    private static ListTag floats(float... v) {
        ListTag l = new ListTag();
        for (float f : v) l.add(FloatTag.valueOf(f));
        return l;
    }

    /** Аналог /data merge entity: накладывает patch на NBT сущности и перечитывает её. */
    private static void merge(Entity e, CompoundTag patch) {
        UUID id = e.getUUID();
        CompoundTag cur = e.saveWithoutId(new CompoundTag());
        cur.merge(patch);
        e.load(cur);
        e.setUUID(id);
    }
}
