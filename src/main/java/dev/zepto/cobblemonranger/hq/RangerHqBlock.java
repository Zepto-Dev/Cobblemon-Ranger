package dev.zepto.cobblemonranger.hq;

import dev.zepto.cobblemonranger.browser.BrowserManager;
import dev.zepto.cobblemonranger.browser.GuidePage;
import com.mojang.serialization.MapCodec;
import dev.zepto.cobblemonranger.mission.MissionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;

/**
 * The Ranger HQ terminal: the job site of the Ranger villager profession. Ranger villagers hand out missions, and get
 * better missions to give as they level up by having missions completed for them. Players use the terminal itself to
 * install upgrade modules, recharge their styler and check their mission.
 */
public class RangerHqBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<RangerHqBlock> CODEC = simpleCodec(RangerHqBlock::new);

    public RangerHqBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer) {
            BrowserManager.unlock(serverPlayer, GuidePage.MISSIONS, GuidePage.UPGRADES);
            List<Component> lines = MissionManager.hqLines(serverPlayer);
            ContainerLevelAccess access = ContainerLevelAccess.create(level, pos);
            serverPlayer.openMenu(new SimpleMenuProvider(
                    (id, inventory, p) -> RangerHqMenu.server(id, inventory, access, serverPlayer, lines),
                    getName()), buf -> RangerHqMenu.LINES_CODEC.encode(buf, lines));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
