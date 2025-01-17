/*
 * Scaling Health
 * Copyright (C) 2018 SilentChaos512
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 3
 * of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.silentchaos512.scalinghealth.item;

import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Rarity;
import net.minecraft.stats.Stats;
import net.minecraft.util.ActionResult;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;
import net.silentchaos512.scalinghealth.ScalingHealth;
import net.silentchaos512.scalinghealth.capability.IDifficultySource;
import net.silentchaos512.scalinghealth.utils.Difficulty;
import net.silentchaos512.scalinghealth.utils.Players;

import javax.annotation.Nullable;
import java.util.List;

public class DifficultyMutatorItem extends Item {
    public enum Type {
        ENCHANTED, CURSED
    }

    private final Type type;

    public DifficultyMutatorItem(Type type) {
        super(new Item.Properties().group(ItemGroup.MISC));
        this.type = type;
    }

    public float getEffectAmount(ItemStack stack, @Nullable World world) {
        if (world == null)
            return 0;
        if (type == Type.CURSED)
            return Players.cursedHeartAffectAmount(world);
        else
            return Players.enchantedHeartAffectAmount(world);
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<ITextComponent> list, ITooltipFlag flag) {
        double amount = getEffectAmount(stack, world);
        String amountStr = (amount > 0 ? "+" : "") + String.format("%.1f", amount);
        list.add(new TranslationTextComponent("item.scalinghealth.difficulty_changer.effectDesc", amountStr));
    }

    @Override
    public Rarity getRarity(ItemStack stack) {
        return Rarity.EPIC;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getHeldItem(hand);
        IDifficultySource source = Difficulty.source(player);

        double particleX = player.posX;
        double particleY = player.posY + 0.65f * player.getHeight();
        double particleZ = player.posZ;

        if (!world.isRemote) {
            float change = getEffectAmount(stack, world);
            source.addDifficulty(change);
            stack.shrink(1);
            player.addStat(Stats.ITEM_USED.get(this));
        }

        switch (this.type) {
            // Enchanted Heart
            case ENCHANTED:
                enchantedHeartEffects(world, player, particleX, particleY, particleZ);
                return new ActionResult<>(ActionResultType.SUCCESS, stack);
            // Cursed Heart
            case CURSED:
                cursedHeartEffects(world, player, particleX, particleY, particleZ);
                return new ActionResult<>(ActionResultType.SUCCESS, stack);
            default:
                ScalingHealth.LOGGER.error("DifficultyMutatorItem invalid type: {}", this.type);
                return new ActionResult<>(ActionResultType.PASS, stack);
        }
    }

    private void cursedHeartEffects(World world, PlayerEntity player, double particleX, double particleY, double particleZ) {
//        for (int i = 0; i < 20 - 5 * ScalingHealth.proxy.getParticleSettings(); ++i) {
//            double xSpeed = 0.08 * ScalingHealth.random.nextGaussian();
//            double ySpeed = 0.05 * ScalingHealth.random.nextGaussian();
//            double zSpeed = 0.08 * ScalingHealth.random.nextGaussian();
//            ScalingHealth.proxy.spawnParticles(EnumModParticles.CURSED_HEART,
//                    new Color(0.4f, 0f, 0.6f), world, particleX, particleY, particleZ, xSpeed, ySpeed, zSpeed);
//        }
//        world.playSound(null, player.getPosition(), ModSounds.CURSED_HEART_USE,
//                SoundCategory.PLAYERS, 0.3f,
//                (float) (0.7f + 0.05f * ScalingHealth.random.nextGaussian()));
    }

    private void enchantedHeartEffects(World world, PlayerEntity player, double particleX, double particleY, double particleZ) {
//        for (int i = 0; i < 20 - 5 * ScalingHealth.proxy.getParticleSettings(); ++i) {
//            double xSpeed = 0.08 * ScalingHealth.random.nextGaussian();
//            double ySpeed = 0.05 * ScalingHealth.random.nextGaussian();
//            double zSpeed = 0.08 * ScalingHealth.random.nextGaussian();
//            ScalingHealth.proxy.spawnParticles(EnumModParticles.ENCHANTED_HEART,
//                    new Color(1f, 1f, 0.5f), world, particleX, particleY, particleZ, xSpeed, ySpeed, zSpeed);
//        }
//        world.playSound(null, player.getPosition(), ModSounds.ENCHANTED_HEART_USE,
//                SoundCategory.PLAYERS, 0.4f, 1.7f);
    }
}
