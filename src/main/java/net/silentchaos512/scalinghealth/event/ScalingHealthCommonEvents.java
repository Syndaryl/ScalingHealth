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

package net.silentchaos512.scalinghealth.event;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.storage.loot.LootContext;
import net.minecraft.world.storage.loot.LootParameterSets;
import net.minecraft.world.storage.loot.LootParameters;
import net.minecraft.world.storage.loot.LootTable;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerSleepInBedEvent;
import net.minecraftforge.event.entity.player.PlayerWakeUpEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.network.NetworkDirection;
import net.silentchaos512.scalinghealth.ScalingHealth;
import net.silentchaos512.scalinghealth.capability.IDifficultySource;
import net.silentchaos512.scalinghealth.config.Config;
import net.silentchaos512.scalinghealth.config.DimensionConfig;
import net.silentchaos512.scalinghealth.config.EvalVars;
import net.silentchaos512.scalinghealth.lib.EntityGroup;
import net.silentchaos512.scalinghealth.lib.module.ModuleAprilTricks;
import net.silentchaos512.scalinghealth.network.ClientLoginMessage;
import net.silentchaos512.scalinghealth.network.Network;
import net.silentchaos512.scalinghealth.utils.Difficulty;
import net.silentchaos512.utils.MathUtils;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

@Mod.EventBusSubscriber(modid = ScalingHealth.MOD_ID)
public final class ScalingHealthCommonEvents {
    private ScalingHealthCommonEvents() {}

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayerEntity)) return;
        ServerPlayerEntity player = (ServerPlayerEntity) event.getPlayer();
        World world = player.world;
        ScalingHealth.LOGGER.info("Sending login packet to player {}", player);
        ClientLoginMessage msg = new ClientLoginMessage(Difficulty.areaMode(world), (float) Difficulty.maxValue(world));
        Network.channel.sendTo(msg, player.connection.netManager, NetworkDirection.PLAY_TO_CLIENT);
    }

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof LivingEntity)) return;

        LivingEntity entity = (LivingEntity) event.getEntity();
        World world = entity.world;
        if (world.isRemote) return;
        MinecraftServer server = world.getServer();
        if (server == null) return;

        // Mob loot disabled?
        if (!world.getGameRules().getBoolean(GameRules.DO_MOB_LOOT)) return;

        PlayerEntity player = getPlayerThatCausedDeath(event.getSource());

        // Get the bonus drops loot table for this mob type
        Optional<ResourceLocation> tableName = EntityGroup.from(entity, true).getBonusDropsLootTable();
        if (!tableName.isPresent()) return;

        LootTable lootTable = server.getLootTableManager().getLootTableFromLocation(tableName.get());
        LootContext.Builder contextBuilder = new LootContext.Builder((ServerWorld) world)
                .withParameter(LootParameters.THIS_ENTITY, entity)
                .withParameter(LootParameters.POSITION, entity.getPosition())
                .withParameter(LootParameters.DAMAGE_SOURCE, event.getSource())
                .withNullableParameter(LootParameters.KILLER_ENTITY, player)
                .withNullableParameter(LootParameters.DIRECT_KILLER_ENTITY, player)
                .withNullableParameter(LootParameters.LAST_DAMAGE_PLAYER, player);
        if (player != null) contextBuilder.withLuck(player.getLuck());
        List<ItemStack> list = lootTable.generate(contextBuilder.build(LootParameterSets.ENTITY));
        list.forEach(stack -> event.getDrops().add(dropItem(entity, world, stack)));
    }

    private static ItemEntity dropItem(LivingEntity entity, World world, ItemStack stack) {
        return new ItemEntity(world, entity.posX, entity.posY + entity.getHeight() / 2, entity.posZ, stack);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onXPDropped(LivingExperienceDropEvent event) {
        /*
        LivingEntity entityLiving = event.getEntityLiving();

        // Additional XP from all mobs.
        short difficulty = entityLiving.getEntityData()
                .getShort(DifficultyHandler.NBT_ENTITY_DIFFICULTY);
        float multi = 1.0f + Config.Mob.xpBoost * difficulty;

        float amount = event.getDroppedExperience();
        amount *= multi;

        // Additional XP from blights.
        if (BlightHandler.isBlight(entityLiving)) {
            amount *= Config.Mob.Blight.xpMultiplier;
        }

        event.setDroppedExperience(Math.round(amount));
        */
    }

    /**
     * Get the player that caused a mob's death. Could be a FakePlayer or null.
     *
     * @return The player that caused the damage, or the owner of the tamed animal that caused the
     * damage.
     */
    @Nullable
    private static PlayerEntity getPlayerThatCausedDeath(DamageSource source) {
        if (source == null) {
            return null;
        }

        // Player is true source.
        Entity entitySource = source.getTrueSource();
        if (entitySource instanceof PlayerEntity) {
            return (PlayerEntity) entitySource;
        }

        // Player's pet is true source.
        boolean isTamedAnimal = entitySource instanceof TameableEntity
                && ((TameableEntity) entitySource).isTamed();
        if (entitySource instanceof TameableEntity) {
            TameableEntity tamed = (TameableEntity) entitySource;
            if (tamed.isTamed() && tamed.getOwner() instanceof PlayerEntity) {
                return (PlayerEntity) tamed.getOwner();
            }
        }

        // No player responsible.
        return null;
    }

    @SubscribeEvent
    public static void onPlayerDied(LivingDeathEvent event) {
        if (event.getEntity() == null || !(event.getEntity() instanceof PlayerEntity)) {
            return;
        }

        PlayerEntity player = (PlayerEntity) event.getEntity();

        if (ModuleAprilTricks.instance.isEnabled() && ModuleAprilTricks.instance.isRightDay()) {
//            ScalingHealth.proxy.playSoundOnClient(player, ModSounds.PLAYER_DIED, 0.6f, 1f);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        /*
        // Set player health correctly after respawn.
        if (event.player instanceof ServerPlayerEntity) {
            ServerPlayerEntity player = (ServerPlayerEntity) event.player;
            PlayerData data = SHPlayerDataHandler.get(player);
            if (data == null) return;

            // Lose health on death?
            if (Config.Player.Health.lostOnDeath > 0 && !event.isEndConquered()) {
                float newHealth = data.getMaxHealth() - Config.Player.Health.lostOnDeath;
                float startHealth = Config.Player.Health.startingHealth;
                data.setMaxHealth(newHealth < startHealth ? startHealth : newHealth);
            }

            // Lose difficulty on death?
            if (!event.isEndConquered()) {
                double currentDifficulty = data.getDifficulty();
                double newDifficulty = MathHelper.clamp(
                        currentDifficulty - Config.Difficulty.lostOnDeath,
                        Config.Difficulty.minValue, Config.Difficulty.maxValue);
                data.setDifficulty(newDifficulty);
            }

            // Apply health modifier
            if (Config.Player.Health.allowModify) {
                float health = player.getHealth();
                float maxHealth = data.getMaxHealth();
                ModifierHandler.setMaxHealth(player, maxHealth, 0);
                if (health != maxHealth && maxHealth > 0) {
                    player.setHealth(player.getMaxHealth());
                }
            }
        }
        */
    }

    @SubscribeEvent
    public static void onPlayerJoinedServer(PlayerEvent.PlayerLoggedInEvent event) {
        /*
        // Sync player data and set health.
        if (event.player instanceof ServerPlayerEntity) {
            ServerPlayerEntity player = (ServerPlayerEntity) event.player;
            PlayerData data = SHPlayerDataHandler.get(player);

            // Resets, based on config?
            Calendar today = Calendar.getInstance();
            Calendar lastTimePlayed = data.getLastTimePlayed();

            if (Config.Difficulty.DIFFFICULTY_RESET_TIME.shouldReset(today, lastTimePlayed)) {
                ScalingHealth.logHelper.info("Reset player {}'s difficulty to {}", player.getName(), (int) Config.Difficulty.startValue);
                ChatHelper.sendMessage(player, "[Scaling Health] Your difficulty has been reset.");
                data.setDifficulty(Config.Difficulty.startValue);
            }
            if (Config.Player.Health.resetTime.shouldReset(today, lastTimePlayed)) {
                data.setMaxHealth(Config.Player.Health.startingHealth);
                ScalingHealth.logHelper.info("Reset player {}'s health to {}", player.getName(), Config.Player.Health.startingHealth);
                ChatHelper.sendMessage(player, "[Scaling Health] Your health has been reset.");
            }

            data.getLastTimePlayed().setTime(today.getTime());

            // Apply health modifier
            if (Config.Player.Health.allowModify) {
                float maxHealth = data.getMaxHealth();
                ModifierHandler.setMaxHealth(player, maxHealth, 0);
            }
        }

        if (ModuleAprilTricks.instance.isEnabled() && ModuleAprilTricks.instance.isRightDay()) {
            ChatHelper.sendMessage(event.player,
                    TextFormatting.RED + "[Scaling Health] It's April Fool's time... hehehe.");
        }
        */
    }

    @SubscribeEvent
    public static void onPlayerSleepInBed(PlayerSleepInBedEvent event) {
        PlayerEntity player = event.getEntityPlayer();
        if (!player.world.isRemote && Config.CLIENT.warnWhenSleeping.get()) {
            DimensionConfig config = Config.get(player);
            double newDifficulty = EvalVars.apply(config, player, config.difficulty.onPlayerSleep.get());

            if (!MathUtils.doublesEqual(Difficulty.ofEntity(player), newDifficulty, 0.1)) {
                ScalingHealth.LOGGER.debug("old={}, new={}", Difficulty.ofEntity(player), newDifficulty);
                // Difficulty would change (doesn't change until onPlayerWakeUp)
                String configMsg = config.difficulty.sleepWarningMessage.get();
                ITextComponent text = configMsg.isEmpty()
                        ? new TranslationTextComponent("misc.scalinghealth.sleepWarning")
                        : new StringTextComponent(configMsg);
                player.sendMessage(text);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerWakeUp(PlayerWakeUpEvent event) {
        PlayerEntity player = event.getEntityPlayer();
        if (!player.world.isRemote && !event.updateWorld()) {
            DimensionConfig config = Config.get(player);
            IDifficultySource source = Difficulty.source(player);
            double newDifficulty = EvalVars.apply(config, player, config.difficulty.onPlayerSleep.get());

            if (!MathUtils.doublesEqual(source.getDifficulty(), newDifficulty)) {
                // Update difficulty after sleeping
                source.setDifficulty((float) newDifficulty);
            }

            // TODO: World difficulty increase?
        }
    }

    /*
    @SubscribeEvent
    public void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (event.getModID().equals(ScalingHealth.MOD_ID)) {
            Config.INSTANCE.load();
            Config.INSTANCE.save();
        }
    }
    */
}
