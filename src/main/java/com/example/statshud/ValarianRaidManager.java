package com.example.statshud;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ValarianRaidManager {
    private static final Random RANDOM = new Random();

    private static long lastRaidDay = 0;
    private static int nextRaidInterval = 10; 
    private static boolean raidTriggeredToday = false;

    public static void tick(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        if (overworld == null) return;

        long currentDay = overworld.getDayTime() / 24000L;
        long timeOfDay = overworld.getDayTime() % 24000L;

        if (timeOfDay < 1000L) {
            raidTriggeredToday = false;
        }

        if (!raidTriggeredToday && (currentDay - lastRaidDay >= nextRaidInterval) && timeOfDay >= 13000L) {
            raidTriggeredToday = true;
            lastRaidDay = currentDay;
            nextRaidInterval = 10 + RANDOM.nextInt(6);

            triggerRaidEvent(server);
        }
    }

    private static void triggerRaidEvent(MinecraftServer server) {
        List<ServerPlayer> onlinePlayers = server.getPlayerList().getPlayers();
        if (onlinePlayers.isEmpty()) return;

        List<ServerPlayer> validTargets = new ArrayList<>();
        for (ServerPlayer player : onlinePlayers) {
            String rawTerritory = TerritoryManager.getRawTerritoryName(player.chunkPosition());
            boolean isInClaim = !rawTerritory.isEmpty();

            if (isInClaim && !isCitizenOfFaction(player, "valarian")) {
                validTargets.add(player);
            }
        }

        if (validTargets.isEmpty()) return;

        ServerPlayer target = validTargets.get(RANDOM.nextInt(validTargets.size()));
        String claimName = TerritoryManager.getRawTerritoryName(target.chunkPosition());
        spawnRaid(target, claimName, "Valarian");
    }

    public static boolean forceRaid(ServerPlayer player) {
        String claimName = TerritoryManager.getRawTerritoryName(player.chunkPosition());
        if (claimName.isEmpty()) {
            claimName = "Тестовая Зона";
        }
        return spawnRaid(player, claimName, "Valarian");
    }

    public static boolean isCitizenOfFaction(ServerPlayer player, String factionName) {
        String lowerFaction = factionName.toLowerCase();

        for (String tag : player.getTags()) {
            if (tag.toLowerCase().contains(lowerFaction)) {
                return true;
            }
        }

        CompoundTag persistentData = player.getPersistentData();
        if (persistentData.contains("PlayerPersisted")) {
            CompoundTag persisted = persistentData.getCompound("PlayerPersisted");
            if (persisted.contains("Faction") && persisted.getString("Faction").toLowerCase().contains(lowerFaction)) {
                return true;
            }
            if (persisted.contains("Citizenship") && persisted.getString("Citizenship").toLowerCase().contains(lowerFaction)) {
                return true;
            }
        }

        if (player.getTeam() != null && player.getTeam().getName().toLowerCase().contains(lowerFaction)) {
            return true;
        }

        return false;
    }

    public static boolean spawnRaid(ServerPlayer player, String territoryName, String factionName) {
        ServerLevel level = (ServerLevel) player.level();
        BlockPos playerPos = player.blockPosition();

        // Фильтруем строго живых бойцов (исключаем снаряды, повозки, мосты)
        List<EntityType<?>> valarianEntities = BuiltInRegistries.ENTITY_TYPE.entrySet().stream()
            .filter(entry -> {
                String ns = entry.getKey().location().getNamespace().toLowerCase();
                String path = entry.getKey().location().getPath().toLowerCase();
                boolean isValarian = ns.contains("valarian") || path.contains("valarian");
                boolean isNotBlacklisted = !path.contains("projectile") && !path.contains("ballista") && !path.contains("cannon") && !path.contains("catapult") && !path.contains("carriage") && !path.contains("drawbridge") && !path.contains("ram");
                return isValarian && isNotBlacklisted;
            })
            .map(java.util.Map.Entry::getValue)
            .toList();

        // Запасной fallback на лучника или первого моба, если фильтр отсёк всё
        if (valarianEntities.isEmpty()) {
            BuiltInRegistries.ENTITY_TYPE.getOptional(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("valarian_conquest", "archer"))
                .ifPresent(valarianEntities::add);
        }

        if (valarianEntities.isEmpty()) {
            player.sendSystemMessage(Component.literal("§c[Ошибка] Живые бойцы Valarian не найдены!"));
            return false;
        }

        player.connection.send(new ClientboundSetActionBarTextPacket(
            Component.literal("§c⚔ НАБЕГ! Войска " + factionName + " штурмуют [" + territoryName + "§c]! ⚔")
        ));

        player.sendSystemMessage(Component.literal("§4[Осада] §cОтряд " + factionName + " подошел к границам ваших владений! Приготовьтесь к бою!"));

        Holder<SoundEvent> soundHolder = Holder.direct(SoundEvents.RAID_HORN.value());
        player.connection.send(new ClientboundSoundPacket(
            soundHolder,
            SoundSource.HOSTILE,
            player.getX(), player.getY(), player.getZ(),
            1.5f, 0.9f,
            level.getRandom().nextLong()
        ));

        int squadSize = 6 + RANDOM.nextInt(5);
        int count = 0;

        for (int i = 0; i < squadSize; i++) {
            double angle = RANDOM.nextDouble() * 2 * Math.PI;
            double distance = 18 + RANDOM.nextDouble() * 10;

            int spawnX = playerPos.getX() + (int) (Math.cos(angle) * distance);
            int spawnZ = playerPos.getZ() + (int) (Math.sin(angle) * distance);
            int spawnY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawnX, spawnZ);

            BlockPos spawnPos = new BlockPos(spawnX, spawnY, spawnY <= level.getMinBuildHeight() ? playerPos.getY() : spawnY);

            EntityType<?> chosenType = valarianEntities.get(RANDOM.nextInt(valarianEntities.size()));
            Entity createdEntity = chosenType.create(level);
            if (createdEntity instanceof Mob mob) {
                mob.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
                mob.setTarget(player);
                level.addFreshEntity(mob);
                count++;
            }
        }

        player.sendSystemMessage(Component.literal("§a[Набег] Прибыло бойцов: §e" + count));
        return true;
    }
}
