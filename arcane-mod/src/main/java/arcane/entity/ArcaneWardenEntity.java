package arcane.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.world.World;

/**
 * Арканный Страж — босс.
 * Большой и очень крепкий, показывает полосу здоровья, бьёт по площади
 * и призывает Кристальных големов, когда теряет здоровье.
 */
public class ArcaneWardenEntity extends ZombieEntity {

	private final ServerBossBar bossBar = new ServerBossBar(
			Text.translatable("entity.arcane.arcane_warden"), BossBar.Color.PURPLE, BossBar.Style.NOTCHED_10);

	private int stage = 0;

	public ArcaneWardenEntity(EntityType<? extends ZombieEntity> type, World world) {
		super(type, world);
		this.setPersistent();
		// Золотая корона и светящаяся незеритовая броня.
		this.equipStack(EquipmentSlot.HEAD, Gear.glint(Items.GOLDEN_HELMET));
		this.equipStack(EquipmentSlot.CHEST, Gear.glint(Items.NETHERITE_CHESTPLATE));
		this.equipStack(EquipmentSlot.LEGS, Gear.glint(Items.NETHERITE_LEGGINGS));
		this.equipStack(EquipmentSlot.FEET, Gear.glint(Items.NETHERITE_BOOTS));
		this.equipStack(EquipmentSlot.MAINHAND, Gear.glint(Items.NETHERITE_SWORD));
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return ZombieEntity.createZombieAttributes()
				.add(EntityAttributes.MAX_HEALTH, 300.0)
				.add(EntityAttributes.ATTACK_DAMAGE, 12.0)
				.add(EntityAttributes.MOVEMENT_SPEED, 0.27)
				.add(EntityAttributes.ARMOR, 10.0)
				.add(EntityAttributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(EntityAttributes.FOLLOW_RANGE, 40.0)
				.add(EntityAttributes.SCALE, 1.8);
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.getWorld() instanceof ServerWorld world)) {
			return;
		}
		bossBar.setPercent(this.getHealth() / this.getMaxHealth());
		aura(world);

		LivingEntity target = this.getTarget();
		if (target == null || !this.isAlive()) {
			return;
		}
		// Удар по площади каждые 6 секунд.
		if (this.age % 120 == 0) {
			slam(world);
		}
		// Призыв помощников на 2/3 и 1/3 здоровья.
		float ratio = this.getHealth() / this.getMaxHealth();
		if (ratio < 0.66f && stage < 1) {
			stage = 1;
			summonGolems(world, 2);
		}
		if (ratio < 0.33f && stage < 2) {
			stage = 2;
			summonGolems(world, 3);
		}
	}

	/** Постоянная аура: три вращающиеся спирали света и фиолетовый дым. */
	private void aura(ServerWorld world) {
		if (this.age % 2 == 0) {
			double base = this.age * 0.2;
			double rise = (this.age % 40) / 40.0 * 3.6;
			for (int i = 0; i < 3; i++) {
				double a = base + i * (Math.PI * 2.0 / 3.0);
				world.spawnParticles(ParticleTypes.END_ROD, this.getX() + Math.cos(a) * 2.0, this.getY() + 0.2 + rise,
						this.getZ() + Math.sin(a) * 2.0, 1, 0.0, 0.0, 0.0, 0.0);
			}
		}
		if (this.age % 6 == 0) {
			world.spawnParticles(ParticleTypes.PORTAL, this.getX(), this.getY() + 2.0, this.getZ(), 8, 0.7, 1.3, 0.7, 0.4);
		}
	}

	private void slam(ServerWorld world) {
		for (PlayerEntity p : world.getEntitiesByClass(PlayerEntity.class, this.getBoundingBox().expand(7.0),
				pl -> pl.isAlive() && !pl.isSpectator())) {
			p.damage(world, world.getDamageSources().mobAttack(this), 10.0f);
			p.takeKnockback(1.4, this.getX() - p.getX(), this.getZ() - p.getZ());
		}
		world.spawnParticles(ParticleTypes.PORTAL, this.getX(), this.getY() + 1.0, this.getZ(), 80, 3.0, 1.0, 3.0, 0.5);
		for (int i = 0; i < 36; i++) {
			double a = i * Math.PI / 18.0;
			world.spawnParticles(ParticleTypes.EXPLOSION, this.getX() + Math.cos(a) * 5.0, this.getY() + 0.3,
					this.getZ() + Math.sin(a) * 5.0, 1, 0.0, 0.0, 0.0, 0.0);
		}
		world.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_IRON_GOLEM_ATTACK, SoundCategory.HOSTILE, 1.5f, 0.6f);
	}

	private void summonGolems(ServerWorld world, int count) {
		for (int i = 0; i < count; i++) {
			CrystalGolemEntity golem = ModEntities.CRYSTAL_GOLEM.create(world, SpawnReason.MOB_SUMMONED);
			if (golem != null) {
				double angle = this.random.nextDouble() * Math.PI * 2.0;
				golem.setPosition(this.getX() + Math.cos(angle) * 3.0, this.getY(), this.getZ() + Math.sin(angle) * 3.0);
				world.spawnEntity(golem);
			}
		}
		world.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 1.0f, 1.2f);
	}

	@Override
	public void onStartedTrackingBy(ServerPlayerEntity player) {
		super.onStartedTrackingBy(player);
		bossBar.addPlayer(player);
	}

	@Override
	public void onStoppedTrackingBy(ServerPlayerEntity player) {
		super.onStoppedTrackingBy(player);
		bossBar.removePlayer(player);
	}

	@Override
	public void remove(Entity.RemovalReason reason) {
		super.remove(reason);
		bossBar.clearPlayers();
	}

	@Override
	public boolean isBaby() {
		return false;
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
