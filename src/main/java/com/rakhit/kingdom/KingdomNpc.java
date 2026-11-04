package com.rakhit.kingdom;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * Единая "человекоподобная" сущность королевства. Роль определяет поведение и скин:
 * KING - сидит на троне; GUARD - офицер королевской гвардии (патруль и аресты);
 * CITIZEN - живой житель (ходит, выполняет указы, может нарушать); HAMA - Борзый Хома в колесе;
 * STINKER - вонючий житель в карцере.
 */
public class KingdomNpc extends PathfinderMob {
    public enum Role { CITIZEN, GUARD, KING, HAMA, STINKER }

    private static final EntityDataAccessor<Integer> ROLE = SynchedEntityData.defineId(KingdomNpc.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(KingdomNpc.class, EntityDataSerializers.INT);

    /** Куда идти домой (кровать). */
    public BlockPos home = BlockPos.ZERO;
    /** Кого сейчас арестовывает (для стражи). */
    public UUID arrestTarget;
    public long arrestSince;
    public boolean tempGuard;
    /** Пока наказан - не гуляет (его перемещает Justice). */
    public boolean punished;
    /** "Мятежник": нарушит текущий указ в этот тик сервера. */
    public long rebelAt = -1;
    private int speechTicks;
    private Component savedName;

    public KingdomNpc(EntityType<? extends KingdomNpc> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
        if (this.getNavigation() instanceof GroundPathNavigation g) {
            g.setCanOpenDoors(true);
            g.setCanPassDoors(true);
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.ATTACK_DAMAGE, 2.0);
    }

    // ---------- данные ----------

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ROLE, 0);
        builder.define(VARIANT, 0);
    }

    public Role role() {
        int i = this.entityData.get(ROLE);
        Role[] v = Role.values();
        return v[Math.max(0, Math.min(v.length - 1, i))];
    }

    public int variant() { return this.entityData.get(VARIANT); }

    public void setVariant(int v) { this.entityData.set(VARIANT, v); }

    public void setRole(Role r) {
        this.entityData.set(ROLE, r.ordinal());
        this.setInvulnerable(true);
        this.setPersistenceRequired();
        switch (r) {
            case KING, STINKER, HAMA -> this.setNoAi(true);
            default -> this.setNoAi(false);
        }
        if (r == Role.HAMA) {
            var a = this.getAttribute(Attributes.SCALE);
            if (a != null) a.setBaseValue(0.6);
        }
        if (r == Role.GUARD) {
            var s = this.getAttribute(Attributes.MOVEMENT_SPEED);
            if (s != null) s.setBaseValue(0.34);
        }
        this.addTag("rakhit_npc");
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Role", this.entityData.get(ROLE));
        tag.putInt("Variant", this.entityData.get(VARIANT));
        tag.putInt("HomeX", home.getX());
        tag.putInt("HomeY", home.getY());
        tag.putInt("HomeZ", home.getZ());
        tag.putBoolean("Temp", tempGuard);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(ROLE, tag.getInt("Role"));
        this.entityData.set(VARIANT, tag.getInt("Variant"));
        this.home = new BlockPos(tag.getInt("HomeX"), tag.getInt("HomeY"), tag.getInt("HomeZ"));
        this.tempGuard = tag.getBoolean("Temp");
        this.punished = false;
        this.arrestTarget = null;
        if (role() == Role.HAMA) {
            var a = this.getAttribute(Attributes.SCALE);
            if (a != null) a.setBaseValue(0.6);
        }
        this.setInvulnerable(true);
        this.addTag("rakhit_npc");
    }

    // ---------- ИИ ----------

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new NpcGoals.ArrestGoal(this));
        this.goalSelector.addGoal(2, new OpenDoorGoal(this, true));
        this.goalSelector.addGoal(3, new NpcGoals.GoHomeGoal(this));
        this.goalSelector.addGoal(4, new NpcGoals.PatrolGoal(this));
        this.goalSelector.addGoal(5, new NpcGoals.CitizenWanderGoal(this));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0f));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    /** Борзый Хома вечно бежит на месте: подменяем анимацию ходьбы на клиенте. */
    @Override
    public void calculateEntityAnimation(boolean flying) {
        if (role() == Role.HAMA) {
            this.walkAnimation.update(1.0f, 0.6f);
            return;
        }
        super.calculateEntityAnimation(flying);
    }

    @Override
    public boolean removeWhenFarAway(double distance) { return false; }

    @Override
    public boolean isPushable() { return role() != Role.KING && role() != Role.HAMA && role() != Role.STINKER; }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // Подданные неуязвимы: наказание за нападение выдаёт KingdomEvents. Пустота убивает.
        if (source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
        return false;
    }

    @Override
    public boolean canBeLeashed() { return false; }

    /** Реплика над головой (подменяем имя на 3 секунды). */
    public void say(String text) {
        if (role() == Role.KING || role() == Role.HAMA || role() == Role.STINKER) return;
        if (speechTicks <= 0) savedName = this.getCustomName();
        this.setCustomName(Component.literal(text));
        this.setCustomNameVisible(true);
        speechTicks = 60;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (speechTicks > 0 && --speechTicks == 0) {
            this.setCustomName(savedName);
        }
    }

    public ItemStack defaultHand() { return this.getItemBySlot(EquipmentSlot.MAINHAND); }

    public boolean isGuard() { return role() == Role.GUARD; }

    /** Удобный доступ: нарушитель-житель определяется по роли. */
    public static boolean isCitizen(LivingEntity e) {
        return e instanceof KingdomNpc n && n.role() == Role.CITIZEN;
    }

    public ServerLevel serverLevel2() { return (ServerLevel) this.level(); }
}
