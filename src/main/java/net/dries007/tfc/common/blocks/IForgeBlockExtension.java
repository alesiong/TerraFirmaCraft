/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */

package net.dries007.tfc.common.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.extensions.IForgeBlock;

/**
 * This implements some of the more annoying methods in {@link IForgeBlock} which would otherwise require implementing across all manner of vanilla subclasses.
 */
public interface IForgeBlockExtension extends IForgeBlock
{
    ExtendedProperties getExtendedProperties();

    @Override
    default int getFlammability(BlockState state, BlockGetter world, BlockPos pos, Direction face)
    {
        return getExtendedProperties().getFlammability();
    }

    @Override
    default int getFireSpreadSpeed(BlockState state, BlockGetter world, BlockPos pos, Direction face)
    {
        return getExtendedProperties().getFireSpreadSpeed();
    }
}