package arcane.entity;

import arcane.ArcaneMod;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnLocationTypes;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.world.Heightmap;

public final class ModEntities {
	private ModEntities() {
	}

	private static final RegistryKey<EntityType<?>> GOLEM_KEY =
			RegistryKey.of(RegistryKeys.ENTITY_TYPE, ArcaneMod.id("crystal_golem"));
	private static final RegistryKey<EntityType<?>> WARDEN_KEY =
			RegistryKey.of(RegistryKeys.ENTITY_TYPE, ArcaneMod.id("arcane_warden"));
	private static final RegistryKey<EntityType<?>> SKELETON_KEY =
			RegistryKey.of(RegistryKeys.ENTITY_TYPE, ArcaneMod.id("arcane_skeleton"));

	public static final EntityType<CrystalGolemEntity> CRYSTAL_GOLEM = Registry.register(
			Registries.ENTITY_TYPE, GOLEM_KEY,
			EntityType.Builder.create(CrystalGolemEntity::new, SpawnGroup.MONSTER)
					.dimensions(0.6f, 1.95f)
					.eyeHeight(1.74f)
					.makeFireImmune()
					.maxTrackingRange(8)
					.build(GOLEM_KEY));

	public static final EntityType<ArcaneSkeletonEntity> ARCANE_SKELETON = Registry.register(
			Registries.ENTITY_TYPE, SKELETON_KEY,
			EntityType.Builder.create(ArcaneSkeletonEntity::new, SpawnGroup.MONSTER)
					.dimensions(0.6f, 1.99f)
					.eyeHeight(1.74f)
					.makeFireImmune()
					.maxTrackingRange(8)
					.build(SKELETON_KEY));

	public static final EntityType<ArcaneWardenEntity> ARCANE_WARDEN = Registry.register(
			Registries.ENTITY_TYPE, WARDEN_KEY,
			EntityType.Builder.create(ArcaneWardenEntity::new, SpawnGroup.MONSTER)
					.dimensions(0.6f, 1.95f)
					.eyeHeight(1.74f)
					.makeFireImmune()
					.maxTrackingRange(10)
					.build(WARDEN_KEY));

	public static void register() {
		FabricDefaultAttributeRegistry.register(CRYSTAL_GOLEM, CrystalGolemEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(ARCANE_SKELETON, ArcaneSkeletonEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(ARCANE_WARDEN, ArcaneWardenEntity.createAttributes());

		SpawnRestriction.register(CRYSTAL_GOLEM, SpawnLocationTypes.ON_GROUND,
				Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, HostileEntity::canSpawnInDark);
		SpawnRestriction.register(ARCANE_SKELETON, SpawnLocationTypes.ON_GROUND,
				Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, HostileEntity::canSpawnInDark);

		// Естественный спавн в обычном мире (редко, чтобы не заполонили мир).
		BiomeModifications.addSpawn(BiomeSelectors.foundInOverworld(), SpawnGroup.MONSTER, CRYSTAL_GOLEM, 8, 1, 2);
		BiomeModifications.addSpawn(BiomeSelectors.foundInOverworld(), SpawnGroup.MONSTER, ARCANE_SKELETON, 8, 1, 2);
	}
}
