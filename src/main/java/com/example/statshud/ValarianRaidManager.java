package com.example.statshud;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ValarianRaidManager {
    private static final Random RANDOM = new Random();
    private static int raidTimer = 0;
    
    // Интервал проверки набегов (~20 минут)
    private static final int RAID_INTERVAL_TICKS = 20 * 60 * 20;

    public static void tick(MinecraftServer server) {
        if (++raidTimer < RAID_INTERVAL_TICKS) return;
        raidTimer = 0;

        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) return;

        // Выбираем только игроков, не являющихся подданными Valarian
        List<ServerPlayer> validTargets = new ArrayList<>();
        for (ServerPlayer player : players) {
            if (!isCitizenOfFaction(player, "valarian")) {
                validTargets.add(player);
            }
        }

        if (validTargets.isEmpty()) return;

        ServerPlayer target = validTargets.get(RANDOM.nextInt(validTargets.size()));
        spawnRaid(target, "Valarian");
    }

    // Автоматическая проверка подданства через данные мода, Scoreboard-теги или NBT
    public static boolean isCitizenOfFaction(ServerPlayer player, String factionName) {
        String lowerFaction = factionName.toLowerCase();

        // 1. Проверка системных тегов (выданных квестами, админами или модом)
        for (String tag : player.getTags()) {
            if (tag.toLowerCase().contains(lowerFaction)) {
                return true;
            }
        }

        // 2. Чтение внутреннего NBT мода Valarian Conquest / фракций
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

        // 3. Проверка принадлежности к команде фракции
        if (player.getTeam() != null && player.getTeam().getName().toLowerCase().contains(lowerFaction)) {
            return true;
        }

        return false;
    }

    public static void spawnRaid(ServerPlayer player, String factionName) {
        ServerLevel level = (ServerLevel) player.level();
        BlockPos playerPos = player.blockPosition();

        player.connection.send(new ClientboundSetTitlesAnimationPacket(15, 60, 15));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.literal("§4⚔ ВРАЖЕСКИЙ НАБЕГ ⚔")));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("§cАрмия " + factionName + " штурмует ваши земли!")));

        Holder<SoundEvent> soundHolder = Holder.direct(SoundEvents.RAID_HORN.value());
        player.connection.send(new ClientboundSoundPacket(
            soundHolder,
            SoundSource.HOSTILE,
            player.getX(), player.getY(), player.getZ(),
            1.5f, 0.9f,
            level.getRandom().nextLong()
        ));

        // Ищем зарегистрированных рыцарей и мобов из Valarian Conquest
        List<EntityType<?>> valarianEntities = BuiltInRegistries.ENTITY_TYPE.entrySet().stream()
            .filter(entry -> entry.getKey().location().getNamespace().equals("valarian_conquest"))
            .map(java.util.Map.Entry::getValue)
            .toList();

        if (valarianEntities.isEmpty()) return;

        int squadSize = 6 + RANDOM.nextInt(5);
        for (int i = 0; i < squadSize; i++) {
            double angle = RANDOM.nextDouble() * 2 * Math.PI;
            double distance = 22 + RANDOM.nextDouble() * 15;

            int spawnX = playerPos.getX() + (int) (Math.cos(angle) * distance);
            int spawnZ = playerPos.getZ() + (int) (Math.sin(angle) * distance);
            int spawnY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawnX, spawnZ);

            BlockPos spawnPos = new BlockPos(spawnX, spawnY, spawnY <= level.getMinBuildHeight() ? playerPos.getY() : spawnY);

            EntityType<?> chosenType = valarianEntities.get(RANDOM.nextInt(valarianEntities.size()));
            var entity = chosenType.create(level, MobSpawnType.EVENT);
            if (entity instanceof Mob mob) {
                mob.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
                mob.setTarget(player);
                level.addFreshEntity(mob);
            }
        }
    }
}
