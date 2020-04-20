/*
 * Work under Copyright. Licensed under the EUPL.
 * See the project README.md and LICENSE.txt for more information.
 */

package net.dries007.tfc.objects.entity.animal;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

import net.dries007.tfc.ConfigTFC;
import net.dries007.tfc.Constants;
import net.dries007.tfc.api.types.IPredator;
import net.dries007.tfc.client.TFCSounds;
import net.dries007.tfc.objects.LootTablesTFC;
import net.dries007.tfc.objects.entity.ai.EntityAIAttackMeleeTFC;
import net.dries007.tfc.objects.entity.ai.EntityAIWanderHuntArea;
import net.dries007.tfc.util.climate.BiomeHelper;
import net.dries007.tfc.world.classic.biomes.BiomesTFC;

@ParametersAreNonnullByDefault
public class EntityHyenaTFC extends EntityAnimalMammal implements IPredator
{
    private static final int DAYS_TO_ADULTHOOD = 1800;

    @SuppressWarnings("unused")
    public EntityHyenaTFC(World worldIn)
    {
        this(worldIn, Gender.valueOf(Constants.RNG.nextBoolean()),
            getRandomGrowth(DAYS_TO_ADULTHOOD));
    }

    public EntityHyenaTFC(World worldIn, Gender gender, int birthDay)
    {
        super(worldIn, gender, birthDay);
        this.setSize(1.5F, 1.2F);
    }

    @Override
    public int getSpawnWeight(Biome biome, float temperature, float rainfall, float floraDensity, float floraDiversity)
    {
        BiomeHelper.BiomeType biomeType = BiomeHelper.getBiomeType(temperature, rainfall, floraDensity);
        if (!BiomesTFC.isOceanicBiome(biome) && !BiomesTFC.isBeachBiome(biome) &&
            (biomeType == BiomeHelper.BiomeType.TROPICAL_FOREST || biomeType == BiomeHelper.BiomeType.TEMPERATE_FOREST))
        {
            return ConfigTFC.WORLD.predatorSpawnRarity;
        }
        return 0;
    }

    @Override
    public int getDaysToAdulthood()
    {
        return DAYS_TO_ADULTHOOD;
    }

    @Override
    public boolean isFood(@Nonnull ItemStack stack)
    {
        // Since there's no way to get fish in default TFC, let's consider meats as also valid food items for cats
        return (stack.getItem() == Items.FISH) || (stack.getItem() instanceof ItemFood && ((ItemFood) stack.getItem()).isWolfsFavoriteMeat());
    }

    @Override
    public void birthChildren()
    {
        // Not farmable
    }

    @Override
    public long gestationDays()
    {
        return 0; // not farmable
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSourceIn) { return TFCSounds.ANIMAL_PANTHER_HURT; }

    @Override
    protected SoundEvent getDeathSound() { return TFCSounds.ANIMAL_PANTHER_DEATH; }

    @Override
    public boolean attackEntityAsMob(Entity entityIn)
    {
        double attackDamage = this.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).getAttributeValue();
        if (this.isChild())
        {
            attackDamage /= 2;
        }
        boolean flag = entityIn.attackEntityFrom(DamageSource.causeMobDamage(this), (float) attackDamage);
        if (flag)
        {
            this.applyEnchantments(this, entityIn);
        }
        return flag;
    }

    @Override
    protected void initEntityAI()
    {
        EntityAIWander wander = new EntityAIWanderHuntArea(this, 1.0D);
        this.tasks.addTask(0, new EntityAISwimming(this));
        this.tasks.addTask(3, new EntityAIAttackMeleeTFC<>(this, 1.2D, 1.25D, EntityAIAttackMeleeTFC.AttackBehavior.NIGHTTIME_ONLY).setWanderAI(wander));
        this.tasks.addTask(4, new EntityAIFollowParent(this, 1.1D));
        this.tasks.addTask(5, wander); // Move within hunt area
        this.tasks.addTask(7, new EntityAILookIdle(this));
        this.targetTasks.addTask(3, new EntityAINearestAttackableTarget<>(this, EntityPlayer.class, true));
        // Avoid players at daytime
        this.tasks.addTask(4, new EntityAIAvoidEntity<>(this, EntityPlayer.class, 16.0F, 1.0D, 1.25D));
    }

    @Override
    protected void applyEntityAttributes()
    {
        super.applyEntityAttributes();
        this.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(20.0D);
        this.getEntityAttribute(SharedMonsterAttributes.FOLLOW_RANGE).setBaseValue(20.0D);
        this.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.38D);
        this.getAttributeMap().registerAttribute(SharedMonsterAttributes.ATTACK_DAMAGE);
        this.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(3.0D);
    }

    @Override
    protected void updateAITasks()
    {
        super.updateAITasks();
        if (!this.hasHome())
        {
            this.setHomePosAndDistance(this.getPosition(), 80);
        }
    }

    @Override
    protected SoundEvent getAmbientSound()
    {
        return Constants.RNG.nextInt(100) < 5 ? TFCSounds.ANIMAL_PANTHER_CRY : TFCSounds.ANIMAL_PANTHER_SAY;
    }

    @Nullable
    @Override
    protected ResourceLocation getLootTable()
    {
        return LootTablesTFC.ANIMALS_GRAN_FELINE;
    }

    @Override
    protected void playStepSound(BlockPos pos, Block blockIn)
    {
        this.playSound(SoundEvents.ENTITY_POLAR_BEAR_STEP, 0.15F, 1.0F); // Close enough
    }
}
