package arcane.data;

import arcane.ArcaneMod;
import arcane.item.ModItems;
import arcane.item.Spells;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Способности Мечника и Лучника.
 * Клавиши (с оружием в руке):
 *  - Меч:  R — Вихрь клинков, Z — Рывок клинка;
 *  - Лук:  R — Град стрел,    Z — Ливень стрел;
 *  - Посох: R или Z — переключить заклинание.
 */
public final class ClassAbilities {
	private ClassAbilities() {
	}

	private static final Map<String, Long> COOLDOWNS = new HashMap<>();
	private static final List<PersistentProjectileEntity> STRAIGHT_ARROWS = new ArrayList<>();

	private static final class Delayed {
		int ticks;
		final Runnable action;

		Delayed(int ticks, Runnable action) {
			this.ticks = ticks;
			this.action = action;
		}
	}

	private static final List<Delayed> DELAYED = new ArrayList<>();

	/** Выполнить действие через указанное число тиков. */
	public static void later(int ticks, Runnable action) {
		DELAYED.add(new Delayed(ticks, action));
	}

	public static void register() {
		// Стрелы игрока: меткость, огонь, прямой полёт.
		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (entity instanceof PersistentProjectileEntity arrow && !(entity instanceof TridentEntity)
					&& entity.age == 0 && arrow.getOwner() instanceof ServerPlayerEntity player) {
				ArcaneData d = ArcaneData.get(player);
				int sharp = d.rank(Skills.SHARP);
				if (sharp > 0) {
					arrow.setDamage(arrow.getDamage() + 0.5 * sharp);
				}
				if (d.rank(Skills.FIRE) > 0) {
					arrow.setOnFireForTicks(100);
				}
				if (d.rank(Skills.STRAIGHT) > 0) {
					arrow.setNoGravity(true);
					STRAIGHT_ARROWS.add(arrow);
				}
			}
		});

		// Грозовой клинок: шанс удара молнии при ударе мечом.
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (world.isClient || !(player instanceof ServerPlayerEntity sp) || !(world instanceof ServerWorld sw)
					|| !(entity instanceof LivingEntity target)) {
				return ActionResult.PASS;
			}
			if (!sp.getMainHandStack().isIn(ItemTags.SWORDS)) {
				return ActionResult.PASS;
			}
			int storm = ArcaneData.get(sp).rank(Skills.STORM);
			if (storm > 0 && sw.getRandom().nextFloat() < 0.15f * storm) {
				later(2, () -> {
					if (!target.isAlive()) {
						return;
					}
					LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(sw, SpawnReason.TRIGGERED);
					if (bolt != null) {
						bolt.setPosition(target.getPos());
						bolt.setCosmetic(true);
						sw.spawnEntity(bolt);
					}
					target.damage(sw, sw.getDamageSources().indirectMagic(sp, sp), 6.0f + 2.0f * storm);
					target.setOnFireForTicks(60);
				});
			}
			return ActionResult.PASS;
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			tickDelayed();
			tickArrows();
		});

		// После возрождения заново применяем бонусы навыков.
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> ArcaneLogic.sync(newPlayer));
	}

	private static void tickDelayed() {
		if (DELAYED.isEmpty()) {
			return;
		}
		List<Delayed> snapshot = new ArrayList<>(DELAYED);
		DELAYED.clear();
		for (Delayed d : snapshot) {
			d.ticks--;
			if (d.ticks <= 0) {
				d.action.run();
			} else {
				DELAYED.add(d);
			}
		}
	}

	private static void tickArrows() {
		STRAIGHT_ARROWS.removeIf(arrow -> {
			if (arrow.isRemoved()) {
				return true;
			}
			if (arrow.getWorld() instanceof ServerWorld sw) {
				sw.spawnParticles(ParticleTypes.END_ROD, arrow.getX(), arrow.getY(), arrow.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
			}
			if (arrow.age > 60) {
				arrow.discard();
				return true;
			}
			return false;
		});
	}

	// ------------------------------------------------------------------
	// Клавиши способностей
	// ------------------------------------------------------------------

	/** key: 1 = R, 2 = Z. Что произойдёт, зависит от предмета в руке. */
	public static void handleKey(ServerPlayerEntity player, int key) {
		if (!(player.getWorld() instanceof ServerWorld world)) {
			return;
		}
		ItemStack stack = player.getMainHandStack();
		ArcaneData d = ArcaneData.get(player);

		if (stack.isOf(ModItems.ARCANE_STAFF)) {
			ArcaneLogic.cycleSpell(player);
		} else if (stack.isIn(ItemTags.SWORDS)) {
			if (key == 1) {
				whirlwind(player, world, d);
			} else {
				dash(player, world, d);
			}
		} else if (stack.isOf(Items.BOW)) {
			if (key == 1) {
				hail(player, world, d);
			} else {
				rain(player, world, d);
			}
		}
	}

	private static boolean learned(ServerPlayerEntity player, ArcaneData d, int skill) {
		if (d.rank(skill) > 0) {
			return true;
		}
		player.sendMessage(Text.translatable("message.arcane.not_learned"), true);
		return false;
	}

	private static boolean ready(ServerPlayerEntity player, ServerWorld world, int skill, int cooldown) {
		String key = player.getUuid() + ":" + skill;
		long now = world.getTime();
		long until = COOLDOWNS.getOrDefault(key, 0L);
		if (now < until) {
			player.sendMessage(Text.translatable("message.arcane.cooldown", (until - now + 19) / 20), true);
			return false;
		}
		COOLDOWNS.put(key, now + cooldown);
		return true;
	}

	// ------------------------------------------------------------------
	// Мечник
	// ------------------------------------------------------------------

	/** R с мечом: вихрь клинков — урон по кругу, враги подбрасываются вверх. */
	private static void whirlwind(ServerPlayerEntity player, ServerWorld world, ArcaneData d) {
		if (!learned(player, d, Skills.WHIRL) || !ready(player, world, Skills.WHIRL, 120)) {
			return;
		}
		player.sendMessage(Text.translatable("skill.arcane." + Skills.WHIRL), true);
		float damage = 6.0f + 2.0f * d.rank(Skills.BLADE);
		int hits = 0;
		Box area = player.getBoundingBox().expand(4.0, 1.0, 4.0);
		for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, area,
				x -> x != player && x.isAlive() && x instanceof Monster)) {
			e.damage(world, world.getDamageSources().playerAttack(player), damage);
			e.takeKnockback(1.0, player.getX() - e.getX(), player.getZ() - e.getZ());
			e.addVelocity(0.0, 0.5, 0.0);
			hits++;
		}
		for (int i = 0; i < 28; i++) {
			double a = i * Math.PI / 14.0;
			double r = 1.0 + (i % 4);
			world.spawnParticles(ParticleTypes.CRIT, player.getX() + Math.cos(a) * r, player.getY() + 0.9,
					player.getZ() + Math.sin(a) * r, 3, 0.1, 0.3, 0.1, 0.1);
		}
		for (int i = 0; i < 8; i++) {
			double a = i * Math.PI / 4.0;
			world.spawnParticles(ParticleTypes.SWEEP_ATTACK, player.getX() + Math.cos(a) * 2.5, player.getY() + 1.0,
					player.getZ() + Math.sin(a) * 2.5, 1, 0.0, 0.0, 0.0, 0.0);
		}
		world.spawnParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.2, player.getZ(), 25, 2.0, 0.1, 2.0, 0.05);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.2f, 0.7f);
		if (hits >= 3) {
			Achievements.grant(player, "blade_dancer");
		}
	}

	/** Z с мечом: рывок вперёд, режущий всех монстров на пути. */
	private static void dash(ServerPlayerEntity player, ServerWorld world, ArcaneData d) {
		if (!learned(player, d, Skills.DASH)) {
			return;
		}
		Vec3d start = player.getPos();
		Vec3d look = player.getRotationVec(1.0f).multiply(1.0, 0.0, 1.0).normalize();
		Vec3d end = start;
		for (double t = 1.0; t <= 9.0; t += 1.0) {
			Vec3d p = start.add(look.multiply(t));
			BlockPos bp = BlockPos.ofFloored(p);
			if (!passable(world, bp) || !passable(world, bp.up())) {
				break;
			}
			end = p;
		}
		if (end.squaredDistanceTo(start) < 1.0) {
			player.sendMessage(Text.translatable("message.arcane.no_room"), true);
			return;
		}
		if (!ready(player, world, Skills.DASH, 80)) {
			return;
		}
		float damage = 8.0f + 2.0f * d.rank(Skills.BLADE);
		Box path = new Box(start, end).expand(1.5, 1.0, 1.5);
		for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, path,
				x -> x != player && x.isAlive() && x instanceof Monster)) {
			e.damage(world, world.getDamageSources().playerAttack(player), damage);
			e.takeKnockback(1.2, start.x - e.getX(), start.z - e.getZ());
		}
		Vec3d delta = end.subtract(start);
		int steps = (int) (delta.length() * 3.0);
		for (int i = 0; i <= steps; i++) {
			Vec3d p = start.add(delta.multiply(i / (double) Math.max(1, steps)));
			world.spawnParticles(ParticleTypes.SWEEP_ATTACK, p.x, p.y + 1.0, p.z, 1, 0.0, 0.0, 0.0, 0.0);
			world.spawnParticles(ParticleTypes.CRIT, p.x, p.y + 1.0, p.z, 3, 0.2, 0.4, 0.2, 0.05);
			world.spawnParticles(ParticleTypes.CLOUD, p.x, p.y + 0.2, p.z, 1, 0.1, 0.0, 0.1, 0.0);
		}
		player.requestTeleport(end.x, end.y, end.z);
		player.fallDistance = 0.0f;
		world.playSound(null, BlockPos.ofFloored(end), SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.PLAYERS, 1.0f, 1.3f);
	}

	private static boolean passable(ServerWorld world, BlockPos pos) {
		return world.getBlockState(pos).getCollisionShape(world, pos).isEmpty();
	}

	// ------------------------------------------------------------------
	// Лучник
	// ------------------------------------------------------------------

	private static ArrowEntity newArrow(ServerPlayerEntity player, ServerWorld world) {
		ArrowEntity arrow = new ArrowEntity(world, player, new ItemStack(Items.ARROW), null);
		arrow.pickupType = PersistentProjectileEntity.PickupPermission.DISALLOWED;
		return arrow;
	}

	/** R с луком: град стрел — веер из 15 стрел одновременно. */
	private static void hail(ServerPlayerEntity player, ServerWorld world, ArcaneData d) {
		if (!learned(player, d, Skills.HAIL) || !ready(player, world, Skills.HAIL, 160)) {
			return;
		}
		player.sendMessage(Text.translatable("skill.arcane." + Skills.HAIL), true);
		for (int row = -1; row <= 1; row++) {
			for (int col = -2; col <= 2; col++) {
				ArrowEntity arrow = newArrow(player, world);
				arrow.setVelocity(player, player.getPitch() + row * 6.0f, player.getYaw() + col * 7.0f, 0.0f, 3.0f, 1.0f);
				world.spawnEntity(arrow);
			}
		}
		world.spawnParticles(ParticleTypes.CRIT, player.getX(), player.getEyeY(), player.getZ(), 20, 0.6, 0.3, 0.6, 0.2);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ARROW_SHOOT, SoundCategory.PLAYERS, 1.5f, 0.6f);
		Achievements.grant(player, "arrow_storm");
	}

	/** Z с луком: ливень стрел — 36 стрел падают с неба в точку прицеливания. */
	private static void rain(ServerPlayerEntity player, ServerWorld world, ArcaneData d) {
		if (!learned(player, d, Skills.RAIN) || !ready(player, world, Skills.RAIN, 300)) {
			return;
		}
		player.sendMessage(Text.translatable("skill.arcane." + Skills.RAIN), true);
		Vec3d target = Spells.aimPoint(player, world, 45.0);
		for (int i = 0; i < 36; i++) {
			double ox = (world.getRandom().nextDouble() - 0.5) * 8.0;
			double oz = (world.getRandom().nextDouble() - 0.5) * 8.0;
			double oy = 14.0 + world.getRandom().nextDouble() * 8.0;
			ArrowEntity arrow = newArrow(player, world);
			arrow.setPosition(target.x + ox, target.y + oy, target.z + oz);
			arrow.setVelocity(0.0, -2.5, 0.0);
			world.spawnEntity(arrow);
		}
		world.spawnParticles(ParticleTypes.ENCHANT, target.x, target.y + 1.0, target.z, 60, 2.5, 1.0, 2.5, 0.5);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ARROW_SHOOT, SoundCategory.PLAYERS, 1.5f, 0.4f);
	}

	// ------------------------------------------------------------------
	// Пассивные бонусы
	// ------------------------------------------------------------------

	/** Вызывается, когда игрок убил монстра. */
	public static void onKill(ServerPlayerEntity player, DamageSource source) {
		ArcaneData d = ArcaneData.get(player);
		int blood = d.rank(Skills.BLOOD);
		if (blood > 0) {
			player.heal(2.0f * blood);
		}
		int rush = d.rank(Skills.RUSH);
		if (rush > 0 && source.getSource() instanceof PersistentProjectileEntity) {
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 60 * rush, 1));
		}
	}

	/** Применяет пассивные бонусы навыков к характеристикам игрока. */
	public static void applyAttributes(ServerPlayerEntity player) {
		ArcaneData d = ArcaneData.get(player);
		setModifier(player, EntityAttributes.ATTACK_DAMAGE, "skill_blade",
				1.0 * d.rank(Skills.BLADE), EntityAttributeModifier.Operation.ADD_VALUE);
		setModifier(player, EntityAttributes.ARMOR, "skill_iron",
				2.0 * d.rank(Skills.IRON), EntityAttributeModifier.Operation.ADD_VALUE);
		setModifier(player, EntityAttributes.MAX_HEALTH, "skill_vitality",
				2.0 * d.rank(Skills.VITALITY), EntityAttributeModifier.Operation.ADD_VALUE);
		setModifier(player, EntityAttributes.MOVEMENT_SPEED, "skill_swift",
				0.10 * d.rank(Skills.SWIFT), EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		setModifier(player, EntityAttributes.SAFE_FALL_DISTANCE, "skill_light",
				2.0 * d.rank(Skills.LIGHT), EntityAttributeModifier.Operation.ADD_VALUE);
	}

	private static void setModifier(ServerPlayerEntity player, RegistryEntry<EntityAttribute> attribute, String name,
									double value, EntityAttributeModifier.Operation operation) {
		EntityAttributeInstance instance = player.getAttributeInstance(attribute);
		if (instance == null) {
			return;
		}
		Identifier id = ArcaneMod.id(name);
		instance.removeModifier(id);
		if (value != 0.0) {
			instance.addTemporaryModifier(new EntityAttributeModifier(id, value, operation));
		}
	}
}
