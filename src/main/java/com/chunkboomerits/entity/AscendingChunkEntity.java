package com.chunkboomerits.entity;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.chunkboomerits.ChunkBoomeritsMod;
import com.chunkboomerits.network.AscendingChunkPayload;

/**
 * One entity for a whole rising chunk — no per-block entity spam.
 */
public class AscendingChunkEntity extends Entity {
	public static final double RISE_SPEED = 0.55;

	private int[] packedPositions = new int[0];
	private int[] stateIds = new int[0];
	private double startY;
	private boolean eaten;

	public AscendingChunkEntity(EntityType<? extends AscendingChunkEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		this.setNoGravity(true);
	}

	public AscendingChunkEntity(Level level, double x, double y, double z, int[] packedPositions, int[] stateIds) {
		this(ModEntities.ASCENDING_CHUNK, level);
		this.setPos(x, y, z);
		this.packedPositions = packedPositions;
		this.stateIds = stateIds;
		this.startY = y;
	}

	public AscendingChunkPayload createPayload() {
		return new AscendingChunkPayload(this.getId(), this.packedPositions, this.stateIds);
	}

	public int[] getPackedPositions() {
		return this.packedPositions;
	}

	public int[] getStateIds() {
		return this.stateIds;
	}

	public void acceptClientMesh(int[] packedPositions, int[] stateIds) {
		this.packedPositions = packedPositions;
		this.stateIds = stateIds;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
	}

	@Override
	public void tick() {
		super.tick();

		this.setDeltaMovement(0.0, RISE_SPEED, 0.0);
		this.setPos(this.getX(), this.getY() + RISE_SPEED, this.getZ());

		if (!this.level().isClientSide() && !this.eaten && this.getY() - this.startY >= ChunkBoomeritsMod.EAT_HEIGHT) {
			this.eat();
		}
	}

	private void eat() {
		this.eaten = true;
		if (!(this.level() instanceof ServerLevel serverLevel)) {
			this.discard();
			return;
		}

		int samples = Math.min(48, Math.max(8, this.stateIds.length / 64));
		for (int i = 0; i < samples; i++) {
			BlockState state = Blocks.STONE.defaultBlockState();
			double px = this.getX() + 8.0;
			double py = this.getY() + 8.0;
			double pz = this.getZ() + 8.0;

			if (this.stateIds.length > 0) {
				int index = serverLevel.random.nextInt(this.stateIds.length);
				state = Block.stateById(this.stateIds[index]);
				int packed = this.packedPositions[index];
				px = this.getX() + AscendingChunkPayload.unpackX(packed) + 0.5;
				py = this.getY() + AscendingChunkPayload.unpackY(packed) + 0.5;
				pz = this.getZ() + AscendingChunkPayload.unpackZ(packed) + 0.5;
			}

			serverLevel.sendParticles(
					new BlockParticleOption(ParticleTypes.BLOCK, state),
					px, py, pz,
					6,
					0.25, 0.25, 0.25,
					0.08
			);
		}

		serverLevel.sendParticles(
				ParticleTypes.CLOUD,
				this.getX() + 8.0,
				this.getY() + 8.0,
				this.getZ() + 8.0,
				40,
				4.0, 4.0, 4.0,
				0.05
		);
		serverLevel.playSound(
				null,
				this.getX() + 8.0,
				this.getY() + 8.0,
				this.getZ() + 8.0,
				SoundEvents.GENERIC_EAT.value(),
				SoundSource.PLAYERS,
				1.4F,
				0.6F
		);

		this.discard();
	}

	@Override
	protected AABB makeBoundingBox(Vec3 position) {
		return new AABB(position.x, position.y, position.z, position.x + 16.0, position.y + 384.0, position.z + 16.0);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		this.startY = input.getDoubleOr("StartY", this.getY());
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		output.putDouble("StartY", this.startY);
	}

	@Override
	public boolean hurtServer(ServerLevel serverLevel, net.minecraft.world.damagesource.DamageSource damageSource, float amount) {
		return false;
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}
}
