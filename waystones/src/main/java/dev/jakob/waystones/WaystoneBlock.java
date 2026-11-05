package dev.jakob.waystones;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Der Wegstein: zwei Blöcke hoch (untere und obere Hälfte). Stufe 0 = geschlossen, 1-3 = das Portal öffnet sich
 * (die Säule teilt sich, in der Mitte erscheint das Portal). In Stufe 3 kann man hindurchgehen wie durch ein Netherportal.
 */
public class WaystoneBlock extends Block implements Portal {
	public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
	public static final IntegerProperty STAGE = IntegerProperty.create("stage", 0, 3);

	private static final VoxelShape LOWER = Shapes.or(Block.box(1, 0, 1, 15, 5, 15), Block.box(3, 5, 3, 13, 16, 13));
	private static final VoxelShape UPPER = Shapes.or(Block.box(3, 0, 3, 13, 11, 13), Block.box(2, 11, 2, 14, 15, 14));
	private static final VoxelShape LOWER_OPEN = Block.box(1, 0, 1, 15, 3, 15);

	public WaystoneBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(HALF, DoubleBlockHalf.LOWER).setValue(STAGE, 0));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(HALF, STAGE);
	}

	public static BlockPos lower(BlockPos pos, BlockState state) {
		return state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(HALF) == DoubleBlockHalf.LOWER ? LOWER : UPPER;
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		if (state.getValue(STAGE) > 0) return state.getValue(HALF) == DoubleBlockHalf.LOWER ? LOWER_OPEN : Shapes.empty();
		return getShape(state, level, pos, context);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide() && player instanceof ServerPlayer sp) {
			Waystones.service().open(sp, (ServerLevel) level, lower(pos, state));
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		BlockPos low = lower(pos, state);
		BlockPos other = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
		if (level.getBlockState(other).getBlock() == this) level.setBlock(other, Blocks.AIR.defaultBlockState(), 35);
		if (!level.isClientSide()) Waystones.service().removed((ServerLevel) level, low);
		return super.playerWillDestroy(level, pos, state, player);
	}

	// ------------------------------------------------------------------ Portal-Animation

	/** Setzt beide Hälften auf eine Stufe. */
	static void setStage(Level level, BlockPos low, int stage) {
		BlockState a = level.getBlockState(low), b = level.getBlockState(low.above());
		if (a.getBlock() instanceof WaystoneBlock) level.setBlock(low, a.setValue(STAGE, stage), 3);
		if (b.getBlock() instanceof WaystoneBlock) level.setBlock(low.above(), b.setValue(STAGE, stage), 3);
	}

	/** Startet das Öffnen des Portals. */
	public void startOpening(ServerLevel level, BlockPos low) {
		setStage(level, low, 1);
		level.playSound(null, low, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0F, 1.3F);
		level.scheduleTick(low, this, 5);
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (state.getValue(HALF) != DoubleBlockHalf.LOWER) return;
		int stage = state.getValue(STAGE);
		boolean open = Waystones.service().portalActive(level, pos);
		if (open) {
			if (stage < 3) {
				setStage(level, pos, stage + 1);
				level.scheduleTick(pos, this, 5);
			} else {
				level.scheduleTick(pos, this, 20); // offen: regelmässig prüfen, ob es schliessen soll
			}
		} else if (stage > 0) {
			if (stage == 3) level.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.0F, 1.3F);
			setStage(level, pos, stage - 1);
			if (stage - 1 > 0) level.scheduleTick(pos, this, 4);
		}
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (state.getValue(STAGE) == 0) return;
		for (int i = 0; i < 3; i++) {
			level.addParticle(ParticleTypes.PORTAL, pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.6, pos.getY() + random.nextDouble(),
					pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.6, (random.nextDouble() - 0.5) * 0.5, -random.nextDouble() * 0.3,
					(random.nextDouble() - 0.5) * 0.5);
		}
	}

	@Override
	protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects, boolean intersects) {
		if (level.isClientSide() || state.getValue(STAGE) < 3) return;
		if (Waystones.service().portalActive((ServerLevel) level, lower(pos, state))) entity.setAsInsidePortal(this, pos);
	}

	@Override
	public int getPortalTransitionTime(ServerLevel level, Entity entity) {
		return entity instanceof Player p && p.isCreative() ? 1 : 40;
	}

	@Override
	public TeleportTransition getPortalDestination(ServerLevel level, Entity entity, BlockPos pos) {
		return Waystones.service().destination(level, entity, lower(pos, level.getBlockState(pos)));
	}

	@Override
	public Transition getLocalTransition() {
		return Transition.CONFUSION;
	}
}
