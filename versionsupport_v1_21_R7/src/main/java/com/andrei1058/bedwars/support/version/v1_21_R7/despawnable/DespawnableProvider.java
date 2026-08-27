package com.andrei1058.bedwars.support.version.v1_21_R7.despawnable;

import com.andrei1058.bedwars.api.arena.team.ITeam;
import com.andrei1058.bedwars.api.server.VersionSupport;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityCreature;
import net.minecraft.world.entity.EntityInsentient;
import net.minecraft.world.entity.EntityLiving;
import net.minecraft.world.entity.ai.goal.PathfinderGoal;
import net.minecraft.world.entity.ai.goal.PathfinderGoalSelector;
import net.minecraft.world.entity.ai.goal.target.PathfinderGoalNearestAttackableTarget;
import net.minecraft.world.entity.player.EntityHuman;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public abstract class DespawnableProvider<T> {

    abstract DespawnableType getType();

    abstract String getDisplayName(DespawnableAttributes attr, ITeam team);

    abstract T spawn(@NotNull DespawnableAttributes attr, @NotNull Location location, @NotNull ITeam team, VersionSupport api);

    protected boolean notSameTeam(@NotNull Entity entity, ITeam team, @NotNull VersionSupport api) {
        var despawnable = api.getDespawnablesList().getOrDefault(entity.getBukkitEntity().getUniqueId(), null);
        return null == despawnable || despawnable.getTeam() != team;
    }

    // goalSelector / targetSelector moved from bO/bP (1.20.4) to cs/ct on 1.21.11
    protected PathfinderGoalSelector getTargetSelector(@NotNull EntityCreature entityLiving) {
        return entityLiving.ct;
    }

    protected PathfinderGoalSelector getGoalSelector(@NotNull EntityCreature entityLiving) {
        return entityLiving.cs;
    }

    protected void clearSelectors(@NotNull EntityCreature entityLiving) {
        entityLiving.cs.b().clear();
        entityLiving.ct.b().clear();
    }

    protected PathfinderGoal getTargetGoal(EntityInsentient entity, ITeam team, VersionSupport api) {
        // since 1.21 the target predicate is a PathfinderTargetCondition.a (EntityLiving, WorldServer)
        return new PathfinderGoalNearestAttackableTarget<>(entity, EntityLiving.class, 20, true, false,
                (entityLiving, world) -> {
                    if (entityLiving instanceof EntityHuman) {
                        return !((EntityHuman) entityLiving).getBukkitEntity().isDead() &&
                                !team.wasMember(((EntityHuman) entityLiving).getBukkitEntity().getUniqueId()) &&
                                !team.getArena().isReSpawning(((EntityHuman) entityLiving).getBukkitEntity().getUniqueId())
                                && !team.getArena().isSpectator(((EntityHuman) entityLiving).getBukkitEntity().getUniqueId());
                    }
                    return notSameTeam(entityLiving, team, api);
                });
    }

    protected void applyDefaultSettings(org.bukkit.entity.@NotNull LivingEntity bukkitEntity, DespawnableAttributes attr,
                                        ITeam team) {
        bukkitEntity.setRemoveWhenFarAway(false);
        bukkitEntity.setPersistent(true);
        bukkitEntity.setCustomNameVisible(true);
        bukkitEntity.setCustomName(getDisplayName(attr, team));

        // the NMS attribute holders are obfuscated and shift every release, the Bukkit
        // registry is stable and does the same thing
        setBaseValue(bukkitEntity, Attribute.MAX_HEALTH, attr.health());
        setBaseValue(bukkitEntity, Attribute.MOVEMENT_SPEED, attr.speed());
        setBaseValue(bukkitEntity, Attribute.ATTACK_DAMAGE, attr.damage());
    }

    private void setBaseValue(org.bukkit.entity.@NotNull LivingEntity entity, Attribute attribute, double value) {
        AttributeInstance instance = Objects.requireNonNull(entity.getAttribute(attribute),
                () -> "Entity is missing the " + attribute.getKey() + " attribute");
        instance.setBaseValue(value);
    }
}
