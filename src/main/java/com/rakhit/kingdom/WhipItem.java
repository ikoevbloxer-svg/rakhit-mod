package com.rakhit.kingdom;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Хлыст: 2 сердечка урона (4.0), дальность 7 блоков, кулдаун 0.6 сек. */
public class WhipItem extends Item {
    public static final float DAMAGE = 4.0f;   // 2 сердца
    public static final double REACH = 7.0;

    public WhipItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.fail(stack);
        player.getCooldowns().addCooldown(this, 12);
        player.swing(hand);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.WHIP_CRACK.get(),
                SoundSource.PLAYERS, 1.0f, 0.9f + level.random.nextFloat() * 0.2f);

        if (level instanceof ServerLevel sl) {
            Vec3 eye = player.getEyePosition();
            Vec3 look = player.getViewVector(1.0f);
            Vec3 end = eye.add(look.scale(REACH));

            // линия хлыста из частиц
            for (double d = 1.0; d <= REACH; d += 0.5) {
                Vec3 p = eye.add(look.scale(d));
                sl.sendParticles(ParticleTypes.CRIT, p.x, p.y - 0.2, p.z, 1, 0, 0, 0, 0);
            }

            BlockHitResult blockHit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            AABB box = player.getBoundingBox().expandTowards(look.scale(REACH)).inflate(1.0);
            EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, box,
                    e -> !e.isSpectator() && e.isPickable() && e != player, REACH * REACH);

            boolean blocked = blockHit.getType() != HitResult.Type.MISS && hit != null
                    && eye.distanceToSqr(blockHit.getLocation()) < eye.distanceToSqr(hit.getLocation());
            if (hit != null && !blocked) {
                Entity target = hit.getEntity();
                if (target.hurt(level.damageSources().playerAttack(player), DAMAGE)) {
                    if (target instanceof LivingEntity le) le.knockback(0.4, -look.x, -look.z);
                    Vec3 loc = hit.getLocation();
                    sl.sendParticles(ParticleTypes.DAMAGE_INDICATOR, loc.x, loc.y, loc.z, 4, 0.2, 0.2, 0.2, 0.1);
                    stack.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
                }
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
