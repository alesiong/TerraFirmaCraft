/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */

package net.dries007.tfc;

import java.util.List;
import java.util.Random;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleReloadableResourceManager;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.*;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.*;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fluids.FluidAttributes;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fmllegacy.network.PacketDistributor;
import net.minecraftforge.fmlserverevents.FMLServerAboutToStartEvent;
import net.minecraftforge.fmlserverevents.FMLServerStoppedEvent;

import net.dries007.tfc.common.TFCTags;
import net.dries007.tfc.common.blocks.CharcoalPileBlock;
import net.dries007.tfc.common.blocks.DeadWallTorchBlock;
import net.dries007.tfc.common.blocks.TFCBlocks;
import net.dries007.tfc.common.blocks.TFCWallTorchBlock;
import net.dries007.tfc.common.blocks.devices.BurningLogPileBlock;
import net.dries007.tfc.common.blocks.devices.CharcoalForgeBlock;
import net.dries007.tfc.common.blocks.devices.PitKilnBlock;
import net.dries007.tfc.common.blocks.rock.Rock;
import net.dries007.tfc.common.capabilities.food.FoodCapability;
import net.dries007.tfc.common.capabilities.food.FoodDefinition;
import net.dries007.tfc.common.capabilities.food.FoodHandler;
import net.dries007.tfc.common.capabilities.food.TFCFoodData;
import net.dries007.tfc.common.capabilities.forge.ForgingCapability;
import net.dries007.tfc.common.capabilities.forge.ForgingHandler;
import net.dries007.tfc.common.capabilities.heat.HeatCapability;
import net.dries007.tfc.common.capabilities.heat.HeatDefinition;
import net.dries007.tfc.common.capabilities.player.PlayerData;
import net.dries007.tfc.common.capabilities.player.PlayerDataCapability;
import net.dries007.tfc.common.capabilities.size.ItemSizeManager;
import net.dries007.tfc.common.command.TFCCommands;
import net.dries007.tfc.common.fluids.FluidHelpers;
import net.dries007.tfc.common.recipes.CollapseRecipe;
import net.dries007.tfc.common.tileentity.*;
import net.dries007.tfc.config.TFCConfig;
import net.dries007.tfc.mixin.accessor.ProtoChunkAccessor;
import net.dries007.tfc.mixin.accessor.SimpleReloadableResourceManagerAccessor;
import net.dries007.tfc.network.ChunkUnwatchPacket;
import net.dries007.tfc.network.PacketHandler;
import net.dries007.tfc.network.PlayerDrinkPacket;
import net.dries007.tfc.util.*;
import net.dries007.tfc.util.calendar.Calendars;
import net.dries007.tfc.util.calendar.ICalendar;
import net.dries007.tfc.util.events.StartFireEvent;
import net.dries007.tfc.util.tracker.WorldTracker;
import net.dries007.tfc.util.tracker.WorldTrackerCapability;
import net.dries007.tfc.world.biome.BiomeSourceExtension;
import net.dries007.tfc.world.chunkdata.ChunkData;
import net.dries007.tfc.world.chunkdata.ChunkDataCache;
import net.dries007.tfc.world.chunkdata.ChunkDataCapability;
import net.dries007.tfc.world.chunkdata.ChunkGeneratorExtension;
import net.dries007.tfc.world.settings.RockLayerSettings;

import static net.dries007.tfc.common.blocks.devices.CharcoalForgeBlock.HEAT;

public final class ForgeEventHandler
{
    private static final Logger LOGGER = LogManager.getLogger();
    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz";
    private static final BlockHitResult FAKE_MISS = BlockHitResult.miss(Vec3.ZERO, Direction.UP, BlockPos.ZERO);

    public static void init()
    {
        final IEventBus bus = MinecraftForge.EVENT_BUS;

        bus.addListener(ForgeEventHandler::onCreateWorldSpawn);
        bus.addGenericListener(LevelChunk.class, ForgeEventHandler::attachChunkCapabilities);
        bus.addGenericListener(Level.class, ForgeEventHandler::attachWorldCapabilities);
        bus.addGenericListener(ItemStack.class, ForgeEventHandler::attachItemCapabilities);
        bus.addGenericListener(Entity.class, ForgeEventHandler::attachEntityCapabilities);
        bus.addListener(ForgeEventHandler::onChunkWatch);
        bus.addListener(ForgeEventHandler::onChunkUnwatch);
        bus.addListener(ForgeEventHandler::onChunkLoad);
        bus.addListener(ForgeEventHandler::onChunkUnload);
        bus.addListener(ForgeEventHandler::onChunkDataSave);
        bus.addListener(ForgeEventHandler::onChunkDataLoad);
        bus.addListener(ForgeEventHandler::addReloadListeners);
        bus.addListener(ForgeEventHandler::beforeServerStart);
        bus.addListener(ForgeEventHandler::onServerStopped);
        bus.addListener(ForgeEventHandler::registerCommands);
        bus.addListener(ForgeEventHandler::onBlockBroken);
        bus.addListener(ForgeEventHandler::onBlockPlace);
        bus.addListener(ForgeEventHandler::onNeighborUpdate);
        bus.addListener(ForgeEventHandler::onWorldTick);
        bus.addListener(ForgeEventHandler::onExplosionDetonate);
        bus.addListener(ForgeEventHandler::onWorldLoad);
        bus.addListener(ForgeEventHandler::onCreateNetherPortal);
        bus.addListener(ForgeEventHandler::onFluidPlaceBlock);
        bus.addListener(ForgeEventHandler::onFireStart);
        bus.addListener(ForgeEventHandler::onProjectileImpact);
        bus.addListener(ForgeEventHandler::onPlayerLoggedIn);
        bus.addListener(ForgeEventHandler::onPlayerRespawn);
        bus.addListener(ForgeEventHandler::onPlayerChangeDimension);
        bus.addListener(ForgeEventHandler::onServerChat);
        bus.addListener(ForgeEventHandler::onPlayerRightClickBlock);
        bus.addListener(ForgeEventHandler::onPlayerRightClickItem);
        bus.addListener(ForgeEventHandler::onPlayerRightClickEmpty);
    }

    /**
     * Duplicates logic from {@link MinecraftServer#setInitialSpawn(ServerLevel, ServerLevelData, boolean, boolean)} as that version only asks the dimension for the sea level...
     */
    public static void onCreateWorldSpawn(WorldEvent.CreateSpawnPosition event)
    {
        if (event.getWorld() instanceof ServerLevel world && world.getChunkSource().getGenerator() instanceof ChunkGeneratorExtension extension)
        {
            final ChunkGenerator generator = extension.self();
            final ServerLevelData settings = event.getSettings();
            final BiomeSourceExtension biomeProvider = extension.getBiomeSource();
            final Random random = new Random(world.getSeed());
            final int spawnDistance = biomeProvider.getSpawnDistance();

            BlockPos pos = biomeProvider.findBiomeIgnoreClimate(biomeProvider.getSpawnCenterX(), generator.getSeaLevel(), biomeProvider.getSpawnCenterZ(), spawnDistance, spawnDistance / 256, biome -> biome.getMobSettings().playerSpawnFriendly(), random);
            ChunkPos chunkPos;
            if (pos == null)
            {
                LOGGER.warn("Unable to find spawn biome!");
                pos = new BlockPos(0, generator.getSeaLevel(), 0);
            }
            chunkPos = new ChunkPos(pos);

            settings.setSpawn(chunkPos.getWorldPosition().offset(8, generator.getSpawnHeight(world), 8), 0.0F);
            boolean foundExactSpawn = false;
            int x = 0, z = 0;
            int xStep = 0;
            int zStep = -1;

            for (int tries = 0; tries < 1024; ++tries)
            {
                if (x > -16 && x <= 16 && z > -16 && z <= 16)
                {
                    BlockPos spawnPos = Helpers.findValidSpawnLocation(world, new ChunkPos(chunkPos.x + x, chunkPos.z + z));
                    if (spawnPos != null)
                    {
                        settings.setSpawn(spawnPos, 0);
                        foundExactSpawn = true;
                        break;
                    }
                }

                if ((x == z) || (x < 0 && x == -z) || (x > 0 && x == 1 - z))
                {
                    int temp = xStep;
                    xStep = -zStep;
                    zStep = temp;
                }

                x += xStep;
                z += zStep;
            }

            if (!foundExactSpawn)
            {
                LOGGER.warn("Unable to find a suitable spawn location!");
            }

            if (world.getServer().getWorldData().worldGenSettings().generateBonusChest())
            {
                LOGGER.warn("No bonus chest for you, you cheaty cheater!");
            }

            event.setCanceled(true);
        }
    }

    public static void attachChunkCapabilities(AttachCapabilitiesEvent<LevelChunk> event)
    {
        final LevelChunk chunk = event.getObject();
        if (!chunk.isEmpty())
        {
            final Level world = event.getObject().getLevel();
            final ChunkPos chunkPos = event.getObject().getPos();

            ChunkData data;
            if (Helpers.isClientSide(world))
            {
                // This may happen before or after the chunk is watched and synced to client
                // Default to using the cache. If later the sync packet arrives it will update the same instance in the chunk capability and cache
                data = ChunkDataCache.CLIENT.getOrCreate(chunkPos, RockLayerSettings.EMPTY);
            }
            else
            {
                // Chunk was created on server thread.
                // 1. If this was due to world gen, it won't have any cap data. This is where we clear the world gen cache and attach it to the chunk
                // 2. If this was due to chunk loading, the caps will be deserialized from NBT after this event is posted. Attach empty data here
                data = ChunkDataCache.WORLD_GEN.remove(chunkPos);
                if (data == null)
                {
                    final RockLayerSettings layerSettings = world instanceof ServerLevel serverWorld && serverWorld.getChunkSource().getGenerator() instanceof ChunkGeneratorExtension generator ? generator.getRockLayerSettings() : RockLayerSettings.EMPTY;
                    data = new ChunkData(chunkPos, layerSettings);
                }

            }
            event.addCapability(ChunkDataCapability.KEY, data);
        }
    }

    public static void attachWorldCapabilities(AttachCapabilitiesEvent<Level> event)
    {
        event.addCapability(WorldTrackerCapability.KEY, new WorldTracker());
    }

    public static void attachItemCapabilities(AttachCapabilitiesEvent<ItemStack> event)
    {
        ItemStack stack = event.getObject();
        if (!stack.isEmpty())
        {
            // Attach mandatory capabilities
            event.addCapability(ForgingCapability.KEY, new ForgingHandler(stack));

            // Optional capabilities
            HeatDefinition def = HeatCapability.get(stack);
            if (def != null)
            {
                event.addCapability(HeatCapability.KEY, def.create());
            }

            FoodDefinition food = FoodCapability.get(stack);
            if (food != null)
            {
                event.addCapability(FoodCapability.KEY, new FoodHandler(food.getData()));
            }
        }
    }

    public static void attachEntityCapabilities(AttachCapabilitiesEvent<Entity> event)
    {
        if (event.getObject() instanceof Player player)
        {
            event.addCapability(PlayerDataCapability.KEY, new PlayerData(player));
        }
    }

    public static void onChunkWatch(ChunkWatchEvent.Watch event)
    {
        // Send an update packet to the client when watching the chunk
        ChunkPos pos = event.getPos();
        ChunkData chunkData = ChunkData.get(event.getWorld(), pos);
        if (chunkData.getStatus() != ChunkData.Status.EMPTY)
        {
            PacketHandler.send(PacketDistributor.PLAYER.with(event::getPlayer), chunkData.getUpdatePacket());
        }
        else
        {
            // Chunk does not exist yet but it's queue'd for watch. Queue an update packet to be sent on chunk load
            ChunkDataCache.WATCH_QUEUE.enqueueUnloadedChunk(pos, event.getPlayer());
        }
    }

    public static void onChunkUnwatch(ChunkWatchEvent.UnWatch event)
    {
        // Send an update packet to the client when un-watching the chunk
        ChunkPos pos = event.getPos();
        PacketHandler.send(PacketDistributor.PLAYER.with(event::getPlayer), new ChunkUnwatchPacket(pos));
        ChunkDataCache.WATCH_QUEUE.dequeueChunk(pos, event.getPlayer());
    }

    public static void onChunkLoad(ChunkEvent.Load event)
    {
        if (!Helpers.isClientSide(event.getWorld()) && !(event.getChunk() instanceof EmptyLevelChunk))
        {
            ChunkPos pos = event.getChunk().getPos();
            ChunkData.getCapability(event.getChunk()).ifPresent(data -> {
                ChunkDataCache.SERVER.update(pos, data);
                ChunkDataCache.WATCH_QUEUE.dequeueLoadedChunk(pos, data);
            });
        }
    }

    public static void onChunkUnload(ChunkEvent.Unload event)
    {
        // Clear server side chunk data cache
        if (!Helpers.isClientSide(event.getWorld()) && !(event.getChunk() instanceof EmptyLevelChunk))
        {
            ChunkDataCache.SERVER.remove(event.getChunk().getPos());
        }
    }

    /**
     * Serialize chunk data on chunk primers, before the chunk data capability is present.
     * - This saves the effort of re-generating the same data for proto chunks
     * - And, due to the late setting of part of chunk data ({@link net.dries007.tfc.world.chunkdata.RockData#setSurfaceHeight(int[])}, avoids that being nullified when saving and reloading during the noise phase of generation
     */
    public static void onChunkDataSave(ChunkDataEvent.Save event)
    {
        if (event.getChunk().getStatus().getChunkType() == ChunkStatus.ChunkType.PROTOCHUNK)
        {
            final ChunkPos pos = event.getChunk().getPos();
            final ChunkData data = ChunkDataCache.WORLD_GEN.get(pos);
            if (data != null && data.getStatus() != ChunkData.Status.EMPTY)
            {
                event.getData().put("tfc_protochunk_data", data.serializeNBT());
            }
        }
    }

    /**
     * @see #onChunkDataSave(ChunkDataEvent.Save)
     */
    public static void onChunkDataLoad(ChunkDataEvent.Load event)
    {
        if (event.getChunk().getStatus().getChunkType() == ChunkStatus.ChunkType.PROTOCHUNK && event.getData().contains("tfc_protochunk_data", Constants.NBT.TAG_COMPOUND) && event.getChunk() instanceof ProtoChunk chunk && ((ProtoChunkAccessor) chunk).accessor$getLevelHeightAccessor() instanceof ServerLevel level && level.getChunkSource().getGenerator() instanceof ChunkGeneratorExtension generator)
        {
            final ChunkPos pos = event.getChunk().getPos();
            final ChunkData data = ChunkDataCache.WORLD_GEN.getOrCreate(pos, generator.getRockLayerSettings());
            data.deserializeNBT(event.getData().getCompound("tfc_protochunk_data"));
        }
    }

    public static void addReloadListeners(AddReloadListenerEvent event)
    {
        // Alloy recipes are loaded as part of recipes, but have a hard dependency on metals.
        // So, we hack internal resource lists in order to stick metals before recipes.
        final ResourceManager resourceManager = event.getDataPackRegistries().getResourceManager();
        if (resourceManager instanceof SimpleReloadableResourceManager resources)
        {
            final List<PreparableReloadListener> listeners = ((SimpleReloadableResourceManagerAccessor) resources).accessor$getListeners();
            final RecipeManager recipes = event.getDataPackRegistries().getRecipeManager();
            Helpers.insertBefore(listeners, Metal.MANAGER, recipes);
        }

        // All other resource reload listeners can be inserted after recipes.
        event.addListener(MetalItem.MANAGER);
        event.addListener(Fuel.MANAGER);
        event.addListener(Drinkable.MANAGER);

        event.addListener(Support.MANAGER);
        event.addListener(ItemSizeManager.MANAGER);

        event.addListener(HeatCapability.MANAGER);
        event.addListener(FoodCapability.MANAGER);

        // Last
        event.addListener(CacheInvalidationListener.INSTANCE);
    }

    public static void beforeServerStart(FMLServerAboutToStartEvent event)
    {
        CacheInvalidationListener.INSTANCE.reloadSync();
    }

    public static void onServerStopped(FMLServerStoppedEvent event)
    {
        CacheInvalidationListener.INSTANCE.reloadSync();
    }

    public static void registerCommands(RegisterCommandsEvent event)
    {
        LOGGER.debug("Registering TFC Commands");
        TFCCommands.register(event.getDispatcher());
    }

    public static void onBlockBroken(BlockEvent.BreakEvent event)
    {
        // Trigger a collapse
        final LevelAccessor world = event.getWorld();
        final BlockPos pos = event.getPos();
        final BlockState state = world.getBlockState(pos);

        if (TFCTags.Blocks.CAN_TRIGGER_COLLAPSE.contains(state.getBlock()) && world instanceof Level level)
        {
            CollapseRecipe.tryTriggerCollapse(level, pos);
            return;
        }

        // Chop down a tree
        final ItemStack stack = event.getPlayer().getMainHandItem();
        if (AxeLoggingHelper.isLoggingAxe(stack) && AxeLoggingHelper.isLoggingBlock(state))
        {
            event.setCanceled(true); // Cancel regardless of outcome of logging
            AxeLoggingHelper.doLogging(world, pos, event.getPlayer(), stack);
        }
    }

    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event)
    {
        if (event.getWorld() instanceof final ServerLevel world)
        {
            final BlockPos pos = event.getPos();
            final BlockState state = event.getState();

            if (TFCTags.Blocks.CAN_LANDSLIDE.contains(state.getBlock()))
            {
                world.getCapability(WorldTrackerCapability.CAPABILITY).ifPresent(cap -> cap.addLandslidePos(pos));
            }

            if (TFCTags.Blocks.BREAKS_WHEN_ISOLATED.contains(state.getBlock()))
            {
                world.getCapability(WorldTrackerCapability.CAPABILITY).ifPresent(cap -> cap.addIsolatedPos(pos));
            }
        }
    }

    public static void onNeighborUpdate(BlockEvent.NeighborNotifyEvent event)
    {
        if (event.getWorld() instanceof final ServerLevel world)
        {
            for (Direction direction : event.getNotifiedSides())
            {
                // Check each notified block for a potential gravity block
                final BlockPos pos = event.getPos().relative(direction);
                final BlockState state = world.getBlockState(pos);

                if (TFCTags.Blocks.CAN_LANDSLIDE.contains(state.getBlock()))
                {
                    world.getCapability(WorldTrackerCapability.CAPABILITY).ifPresent(cap -> cap.addLandslidePos(pos));
                }

                if (TFCTags.Blocks.BREAKS_WHEN_ISOLATED.contains(state.getBlock()))
                {
                    world.getCapability(WorldTrackerCapability.CAPABILITY).ifPresent(cap -> cap.addIsolatedPos(pos));
                }
            }
        }
    }

    public static void onWorldTick(TickEvent.WorldTickEvent event)
    {
        if (event.phase == TickEvent.Phase.START)
        {
            event.world.getCapability(WorldTrackerCapability.CAPABILITY).ifPresent(cap -> cap.tick(event.world));
        }
    }

    public static void onExplosionDetonate(ExplosionEvent.Detonate event)
    {
        if (!event.getWorld().isClientSide)
        {
            event.getWorld().getCapability(WorldTrackerCapability.CAPABILITY).ifPresent(cap -> cap.addCollapsePositions(new BlockPos(event.getExplosion().getPosition()), event.getAffectedBlocks()));
        }
    }

    public static void onWorldLoad(WorldEvent.Load event)
    {
        if (event.getWorld() instanceof final ServerLevel world)
        {
            if (TFCConfig.SERVER.enableForcedTFCGameRules.get())
            {
                final GameRules rules = world.getGameRules();
                final MinecraftServer server = world.getServer();

                rules.getRule(GameRules.RULE_NATURAL_REGENERATION).set(false, server);
                rules.getRule(GameRules.RULE_DOINSOMNIA).set(false, server);
                rules.getRule(GameRules.RULE_DO_PATROL_SPAWNING).set(false, server);
                rules.getRule(GameRules.RULE_DO_TRADER_SPAWNING).set(false, server);

                LOGGER.info("Updating TFC Relevant Game Rules for level {}.", world.dimension());
            }
        }
    }

    public static void onCreateNetherPortal(BlockEvent.PortalSpawnEvent event)
    {
        if (!TFCConfig.SERVER.enableNetherPortals.get())
        {
            event.setCanceled(true);
        }
    }

    public static void onFluidPlaceBlock(BlockEvent.FluidPlaceBlockEvent event)
    {
        Block originalBlock = event.getOriginalState().getBlock();
        if (originalBlock == Blocks.STONE)
        {
            event.setNewState(TFCBlocks.ROCK_BLOCKS.get(net.dries007.tfc.common.blocks.rock.Rock.GABBRO).get(net.dries007.tfc.common.blocks.rock.Rock.BlockType.HARDENED).get().defaultBlockState());
        }
        else if (originalBlock == Blocks.COBBLESTONE)
        {
            event.setNewState(TFCBlocks.ROCK_BLOCKS.get(net.dries007.tfc.common.blocks.rock.Rock.RHYOLITE).get(net.dries007.tfc.common.blocks.rock.Rock.BlockType.HARDENED).get().defaultBlockState());
        }
        else if (originalBlock == Blocks.BASALT)
        {
            event.setNewState(TFCBlocks.ROCK_BLOCKS.get(net.dries007.tfc.common.blocks.rock.Rock.BASALT).get(Rock.BlockType.HARDENED).get().defaultBlockState());
        }
    }

    public static void onFireStart(StartFireEvent event)
    {
        Level world = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = event.getState();
        Block block = state.getBlock();

        if (block == (TFCBlocks.FIREPIT.get()) || block == (TFCBlocks.POT.get()) || block == (TFCBlocks.GRILL.get()))
        {
            final BlockEntity entity = world.getBlockEntity(pos);
            if (entity instanceof AbstractFirepitTileEntity<?> firepit)
            {
                firepit.light(state);
            }
            event.setCanceled(true);
        }
        else if (block == TFCBlocks.TORCH.get() || block == TFCBlocks.WALL_TORCH.get())
        {
            world.getBlockEntity(pos, TFCTileEntities.TICK_COUNTER.get()).ifPresent(TickCounterTileEntity::resetCounter);
            event.setCanceled(true);
        }
        else if (block == (TFCBlocks.DEAD_TORCH.get()))
        {
            world.setBlockAndUpdate(pos, TFCBlocks.TORCH.get().defaultBlockState());
            event.setCanceled(true);
        }
        else if (block == (TFCBlocks.DEAD_WALL_TORCH.get()))
        {
            Direction direction = state.getValue(DeadWallTorchBlock.FACING);
            world.setBlockAndUpdate(pos, TFCBlocks.WALL_TORCH.get().defaultBlockState().setValue(TFCWallTorchBlock.FACING, direction));
            event.setCanceled(true);
        }
        else if (block == (TFCBlocks.LOG_PILE.get()))
        {
            BurningLogPileBlock.tryLightLogPile(world, pos);
            event.setCanceled(true);
        }
        else if (block == (TFCBlocks.PIT_KILN.get()) && state.getValue(PitKilnBlock.STAGE) == 15)
        {
            world.getBlockEntity(pos, TFCTileEntities.PIT_KILN.get()).ifPresent(PitKilnTileEntity::tryLight);
        }
        else if (block == (TFCBlocks.CHARCOAL_PILE.get()) && state.getValue(CharcoalPileBlock.LAYERS) >= 7 && CharcoalForgeBlock.isValid(world, pos))
        {
            world.setBlockAndUpdate(pos, TFCBlocks.CHARCOAL_FORGE.get().defaultBlockState().setValue(HEAT, 2));
            world.getBlockEntity(pos, TFCTileEntities.CHARCOAL_FORGE.get()).ifPresent(CharcoalForgeTileEntity::onCreate);
        }
        else if (block == (TFCBlocks.CHARCOAL_FORGE.get()) && CharcoalForgeBlock.isValid(world, pos))
        {
            world.setBlockAndUpdate(pos, state.setValue(HEAT, 2));
        }
    }

    public static void onProjectileImpact(ProjectileImpactEvent event)
    {
        // todo: find a way to use this event generically for both arrows and fireballs
        /*
        if (!TFCConfig.SERVER.enableFireArrowSpreading.get()) return;
        AbstractArrow arrow = event.getArrow();
        HitResult result = event.getRayTraceResult();
        if (result.getType() == HitResult.Type.BLOCK && arrow.isOnFire())
        {
            BlockHitResult blockResult = (BlockHitResult) result;
            BlockPos pos = blockResult.getBlockPos();
            Level world = arrow.level;
            StartFireEvent.startFire(world, pos, world.getBlockState(pos), blockResult.getDirection(), null, ItemStack.EMPTY);
        }

        if (!TFCConfig.SERVER.enableFireArrowSpreading.get()) return;
        AbstractHurtingProjectile fireball = event.getFireball();
        HitResult result = event.getRayTraceResult();
        if (result.getType() == HitResult.Type.BLOCK)
        {
            BlockHitResult blockResult = (BlockHitResult) result;
            BlockPos pos = blockResult.getBlockPos();
            Level world = fireball.level;
            StartFireEvent.startFire(world, pos, world.getBlockState(pos), blockResult.getDirection(), null, ItemStack.EMPTY);
        }
         */
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event)
    {
        if (event.getPlayer() instanceof ServerPlayer)
        {
            TFCFoodData.replaceFoodStats(event.getPlayer());
        }
    }

    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event)
    {
        if (event.getPlayer() instanceof ServerPlayer)
        {
            TFCFoodData.replaceFoodStats(event.getPlayer());
        }
    }

    public static void onPlayerChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event)
    {
        if (event.getPlayer() instanceof ServerPlayer)
        {
            TFCFoodData.replaceFoodStats(event.getPlayer());
        }
    }

    public static void onServerChat(ServerChatEvent event)
    {
        // Apply intoxication after six hours
        final long intoxicatedTicks = event.getPlayer().getCapability(PlayerDataCapability.CAPABILITY).map(p -> p.getIntoxicatedTicks() - 6 * ICalendar.TICKS_IN_HOUR).orElse(0L);
        if (intoxicatedTicks > 0)
        {
            final float intoxicationChance = Mth.clamp((float) (intoxicatedTicks - 6 * ICalendar.TICKS_IN_HOUR) / PlayerData.MAX_INTOXICATED_TICKS, 0, 0.7f);
            final Random random = event.getPlayer().getRandom();
            final String originalMessage = event.getMessage();
            final String[] words = originalMessage.split(" ");
            for (int i = 0; i < words.length; i++)
            {
                String word = words[i];
                if (word.length() == 0)
                {
                    continue;
                }

                // Swap two letters
                if (random.nextFloat() < intoxicationChance && word.length() >= 2)
                {
                    int pos = random.nextInt(word.length() - 1);
                    word = word.substring(0, pos) + word.charAt(pos + 1) + word.charAt(pos) + word.substring(pos + 2);
                }

                // Repeat / slur letters
                if (random.nextFloat() < intoxicationChance)
                {
                    int pos = random.nextInt(word.length());
                    char repeat = word.charAt(pos);
                    int amount = 1 + random.nextInt(3);
                    word = word.substring(0, pos) + new String(new char[amount]).replace('\0', repeat) + (pos + 1 < word.length() ? word.substring(pos + 1) : "");
                }

                // Add additional letters
                if (random.nextFloat() < intoxicationChance)
                {
                    int pos = random.nextInt(word.length());
                    char replacement = ALPHABET.charAt(random.nextInt(ALPHABET.length()));
                    if (Character.isUpperCase(word.charAt(random.nextInt(word.length()))))
                    {
                        replacement = Character.toUpperCase(replacement);
                    }
                    word = word.substring(0, pos) + replacement + (pos + 1 < word.length() ? word.substring(pos + 1) : "");
                }

                words[i] = word;
            }
            event.setComponent(new TranslatableComponent("<" + event.getUsername() + "> " + String.join(" ", words)));
        }
    }

    public static void onPlayerRightClickBlock(PlayerInteractEvent.RightClickBlock event)
    {
        if (event.getHand() == InteractionHand.MAIN_HAND && event.getItemStack().isEmpty())
        {
            final InteractionResult result = attemptDrink(event.getWorld(), event.getPlayer(), true);
            if (result != InteractionResult.PASS)
            {
                event.setCanceled(true);
                event.setCancellationResult(result);
            }
        }
    }

    public static void onPlayerRightClickItem(PlayerInteractEvent.RightClickItem event)
    {
        final UseOnContext context = new UseOnContext(event.getPlayer(), event.getHand(), FAKE_MISS);
        InteractionManager.onItemUse(event.getItemStack(), context, true).ifPresent(result -> {
            event.setCanceled(true);
            event.setCancellationResult(result);
        });
    }

    public static void onPlayerRightClickEmpty(PlayerInteractEvent.RightClickEmpty event)
    {
        if (event.getHand() == InteractionHand.MAIN_HAND && event.getItemStack().isEmpty())
        {
            // Cannot be cancelled, only fired on client.
            InteractionResult result = attemptDrink(event.getWorld(), event.getPlayer(), false);
            if (result == InteractionResult.SUCCESS)
            {
                PacketHandler.send(PacketDistributor.SERVER.noArg(), new PlayerDrinkPacket());
            }
        }
    }

    public static InteractionResult attemptDrink(Level level, Player player, boolean doDrink)
    {
        final BlockHitResult hit = Helpers.rayTracePlayer(level, player, ClipContext.Fluid.SOURCE_ONLY);
        if (hit.getType() == HitResult.Type.BLOCK)
        {
            final BlockPos pos = hit.getBlockPos();
            final BlockState state = level.getBlockState(pos);
            final FluidStack fluid = new FluidStack(state.getFluidState().getType(), FluidAttributes.BUCKET_VOLUME);
            final float thirst = player.getFoodData() instanceof TFCFoodData data ? data.getThirst() : TFCFoodData.MAX_THIRST;
            final LazyOptional<PlayerData> playerData = player.getCapability(PlayerDataCapability.CAPABILITY);
            if (!fluid.isEmpty() && playerData.map(p -> p.getLastDrinkTick() + 10 < Calendars.get(level).getTicks()).orElse(false))
            {
                final Drinkable drinkable = Drinkable.get(fluid);
                if (drinkable != null && (thirst < TFCFoodData.MAX_THIRST || drinkable.getThirst() == 0))
                {
                    if (!level.isClientSide && doDrink)
                    {
                        doDrink(level, player, state, pos, playerData, drinkable);
                    }
                    return InteractionResult.SUCCESS;
                }
            }
        }
        return InteractionResult.PASS;
    }

    private static void doDrink(Level level, Player player, BlockState state, BlockPos pos, LazyOptional<PlayerData> playerData, Drinkable drinkable)
    {
        playerData.ifPresent(p -> p.setLastDrinkTick(Calendars.SERVER.getTicks()));
        level.playSound(null, pos, SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 1.0f, 1.0f);

        // Drink fluid, applying all stated effects
        final Random random = player.getRandom();
        if (drinkable.getThirst() > 0 && player.getFoodData() instanceof TFCFoodData foodData)
        {
            foodData.addThirst(drinkable.getThirst());
        }
        if (drinkable.getIntoxication() > 0)
        {
            playerData.ifPresent(p -> p.addIntoxicatedTicks(drinkable.getIntoxication()));
        }
        if (drinkable.hasEffects())
        {
            for (Drinkable.Effect effect : drinkable.getEffects())
            {
                if (effect.chance() > random.nextFloat())
                {
                    player.addEffect(new MobEffectInstance(effect.type(), effect.duration(), effect.amplifier(), false, false, true));
                }
            }
        }

        if (drinkable.getConsumeChance() > 0 && drinkable.getConsumeChance() > random.nextFloat())
        {
            final BlockState emptyState = FluidHelpers.isAirOrEmptyFluid(state) ? Blocks.AIR.defaultBlockState() : FluidHelpers.fillWithFluid(state, Fluids.EMPTY);
            if (emptyState != null)
            {
                level.setBlock(pos, emptyState, 3);
            }
        }
    }
}