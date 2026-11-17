package arcane.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

/** Кристальный голем: крепкий и сильный враждебный моб, не горит на солнце. */
public class CrystalGolemEntity extends ZombieEntity {

	public CrystalGolemEntity(EntityType<? extends ZombieEntity> type, World world) {
		super(type, world);
		// Светящаяся (зачарованная) броня.
		this.equipStack(EquipmentSlot.HEAD, Gear.glint(Items.IRON_HELMET));
		this.equipStack(EquipmentSlot.CHEST, Gear.glint(Items.IRON_CHESTPLATE));
	}

	@Override
	public void tick() {
		super.tick();
		if (this.getWorld() instanceof ServerWorld world && this.age % 6 == 0) {
			world.spawnParticles(ParticleTypes.END_ROD, this.getX(), this.getY() + 1.0, this.getZ(), 2, 0.35, 0.7, 0.35, 0.01);
			if (this.age % 24 == 0) {
				world.spawnParticles(ParticleTypes.ENCHANT, this.getX(), this.getY() + 1.6, this.getZ(), 6, 0.4, 0.4, 0.4, 0.3);
			}
		}
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return ZombieEntity.createZombieAttributes()
				.add(EntityAttributes.MAX_HEALTH, 40.0)
				.add(EntityAttributes.ATTACK_DAMAGE, 6.0)
				.add(EntityAttributes.MOVEMENT_SPEED, 0.25)
				.add(EntityAttributes.KNOCKBACK_RESISTANCE, 0.5);
	}

	@Override
	protected boolean burnsInDaylight() {
		return false;
	}

	@Override
	protected boolean canConvertInWater() {
		return false;
	}
}
