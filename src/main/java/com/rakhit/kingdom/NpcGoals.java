package com.rakhit.kingdom;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/** Цели ИИ для стражи и жителей королевства. */
public final class NpcGoals {
    private NpcGoals() {}

    // ==================== арест нарушителя ====================

    public static class ArrestGoal extends Goal {
        private final KingdomNpc g;
        private LivingEntity target;
        private int repath;

        public ArrestGoal(KingdomNpc g) {
            this.g = g;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        private LivingEntity resolve() {
            if (g.arrestTarget == null || !(g.level() instanceof ServerLevel sl)) return null;
            return sl.getEntity(g.arrestTarget) instanceof LivingEntity le && le.isAlive() ? le : null;
        }

        @Override
        public boolean canUse() {
            if (g.role() != KingdomNpc.Role.GUARD || g.arrestTarget == null) return false;
            target = resolve();
            if (target == null) {
                Justice.dropCase(g.arrestTarget);
                g.arrestTarget = null;
                if (g.tempGuard) g.discard();
                return false;
            }
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return g.arrestTarget != null && target != null && target.isAlive();
        }

        @Override
        public void start() { repath = 0; }

        @Override
        public void stop() {
            g.getNavigation().stop();
            g.setSprinting(false);
        }

        @Override
        public void tick() {
            g.getLookControl().setLookAt(target, 30f, 30f);
            g.setSprinting(true);
            if (--repath <= 0) {
                repath = 6;
                g.getNavigation().moveTo(target, 1.7);
            }
            long now = g.level().getGameTime();
            if (g.distanceToSqr(target) < 6.0) {
                finish();
            } else if (now - g.arrestSince > 240) {
                // стража использует "королевскую скорость": мгновенно оказывается рядом
                g.moveTo(target.getX() + 1.5, target.getY(), target.getZ(), g.getYRot(), g.getXRot());
                g.getNavigation().stop();
                finish();
            }
        }

        private void finish() {
            LivingEntity t = target;
            g.arrestTarget = null;
            Justice.arrest(g, t);
        }
    }

    // ==================== патруль ====================

    public static class PatrolGoal extends Goal {
        private final KingdomNpc g;
        private int idx = -1;
        private int wait;

        public PatrolGoal(KingdomNpc g) {
            this.g = g;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return g.role() == KingdomNpc.Role.GUARD && !g.tempGuard && !g.punished
                    && g.arrestTarget == null && Kingdom.ready;
        }

        @Override
        public boolean canContinueToUse() { return canUse(); }

        @Override
        public void tick() {
            Vec3[] pts = Kingdom.patrolPoints();
            if (pts.length == 0) return;
            if (idx < 0) idx = g.getRandom().nextInt(pts.length);
            if (wait > 0) {
                wait--;
                return;
            }
            Vec3 p = pts[idx % pts.length];
            if (g.distanceToSqr(p.x, p.y, p.z) < 6.0 || (g.getNavigation().isDone() && g.tickCount % 40 == 0 && wait == 0 && reached(p))) {
                wait = 40 + g.getRandom().nextInt(80);
                idx = (idx + 1) % pts.length;
                g.getNavigation().stop();
                return;
            }
            if (g.getNavigation().isDone() || g.tickCount % 100 == 0) {
                g.getNavigation().moveTo(p.x, p.y, p.z, 1.0);
            }
        }

        private boolean reached(Vec3 p) { return g.distanceToSqr(p.x, p.y, p.z) < 16.0; }
    }

    // ==================== жители ====================

    /** Дома, когда указ велит сидеть по домам, или ночью (для "сонных" жителей). */
    public static class GoHomeGoal extends Goal {
        private final KingdomNpc c;

        public GoHomeGoal(KingdomNpc c) {
            this.c = c;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (c.role() != KingdomNpc.Role.CITIZEN || c.punished || c.home.equals(BlockPos.ZERO)) return false;
            if (c.arrestTarget != null) return false;
            if (Decrees.npcRule() == Decrees.CitizenRule.STAY_HOME) return true;
            boolean sleepy = (c.getUUID().hashCode() & 1) == 0;
            return sleepy && c.level().isNight();
        }

        @Override
        public boolean canContinueToUse() { return canUse(); }

        @Override
        public void tick() {
            double d2 = c.distanceToSqr(c.home.getX() + 0.5, c.home.getY(), c.home.getZ() + 0.5);
            if (d2 > 6.0) {
                if (c.getNavigation().isDone() || c.tickCount % 60 == 0)
                    c.getNavigation().moveTo(c.home.getX() + 0.5, c.home.getY(), c.home.getZ() + 0.5, 1.0);
            } else {
                c.getNavigation().stop();
            }
        }
    }

    /** Гуляние по королевству; под указом "не стоять на месте" - без остановок. */
    public static class CitizenWanderGoal extends WaterAvoidingRandomStrollGoal {
        private final KingdomNpc c;

        public CitizenWanderGoal(KingdomNpc c) {
            super(c, 0.9, 0.0f);
            this.c = c;
        }

        @Override
        public boolean canUse() {
            if (c.role() != KingdomNpc.Role.CITIZEN || c.punished || c.arrestTarget != null) return false;
            if (Decrees.npcRule() == Decrees.CitizenRule.KEEP_MOVING) trigger();
            return super.canUse();
        }
    }
}
