package arcane.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.AbstractSkeletonEntity;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

/** Арканный скелет: быстрый лучник, не горит на солнце. */
public class ArcaneSkeletonEntity extends SkeletonEntity {

	public ArcaneSkeletonEntity(EntityType<? extends SkeletonEntity> type, World world) {
		super(type, world);
		// Золотая светящаяся «корона» и нагрудник.
		this.equipStack(EquipmentSlot.HEAD, Gear.glint(Items.GOLDEN_HELMET));
		this.equipStack(EquipmentSlot.CHEST, Gear.glint(Items.CHAINMAIL_CHESTPLATE));
	}

	@Override
	public void tick() {
		super.tick();
		if (this.getWorld() instanceof ServerWorld world && this.age % 4 == 0) {
			world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, this.getX(), this.getY() + 0.1, this.getZ(), 1, 0.25, 0.05, 0.25, 0.01);
			if (this.age % 16 == 0) {
				world.spawnParticles(ParticleTypes.WITCH, this.getX(), this.getY() + 1.8, this.getZ(), 3, 0.25, 0.3, 0.25, 0.0);
			}
		}
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return AbstractSkeletonEntity.createAbstractSkeletonAttributes()
				.add(EntityAttributes.MAX_HEALTH, 30.0)
				.add(EntityAttributes.MOVEMENT_SPEED, 0.30);
	}

	@Override
	protected boolean isAffectedByDaylight() {
		return false;
	}
}
