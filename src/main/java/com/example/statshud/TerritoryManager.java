package com.example.statshud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ChunkPos;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.*;

public class TerritoryManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = new File("config/statshud_territories.json");

    public static class ClaimData {
        public String name;
        public String ownerUuid;

        public ClaimData(String name, String ownerUuid) {
            this.name = name;
            this.ownerUuid = ownerUuid;
        }
    }

    private static Map<String, ClaimData> CLAIMS = new HashMap<>();
    private static final Map<UUID, String> CURRENT_ZONE = new HashMap<>();
    private static final Map<UUID, Long> SIEGE_COOLDOWNS = new HashMap<>();

    private static final List<SoundEvent> DISCOVERY_SOUNDS = List.of(
        SoundEvents.RAID_HORN.value(),
        SoundEvents.BELL_BLOCK,
        SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
        SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(0).value(),
        SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(2).value(),
        SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(4).value()
    );

    private static final Random RANDOM = new Random();

    public static void load() {
        if (!CONFIG_FILE.exists()) return;
        try (FileReader reader = new FileReader(CONFIG_FILE)) {
            Type type = new TypeToken<Map<String, ClaimData>>() {}.getType();
            Map<String, ClaimData> data = GSON.fromJson(reader, type);
            if (data != null) CLAIMS = data;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void save() {
        try {
            CONFIG_FILE.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
                GSON.toJson(CLAIMS, writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String getChunkKey(int x, int z) {
        return x + "," + z;
    }

    public static int claimArea(ServerPlayer player, String name, int radius) {
        ChunkPos center = player.chunkPosition();
        String uuid = player.getUUID().toString();
        int claimedCount = 0;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int cx = center.x + dx;
                int cz = center.z + dz;
                String key = getChunkKey(cx, cz);

                ClaimData existing = CLAIMS.get(key);
                if (existing == null || existing.ownerUuid.equals(uuid)) {
                    CLAIMS.put(key, new ClaimData(name, uuid));
                    claimedCount++;
                }
            }
        }

        if (claimedCount > 0) save();
        return claimedCount;
    }

    public static int unclaimArea(ServerPlayer player, int radius) {
        ChunkPos center = player.chunkPosition();
        String uuid = player.getUUID().toString();
        int unclaimedCount = 0;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int cx = center.x + dx;
                int cz = center.z + dz;
                String key = getChunkKey(cx, cz);

                ClaimData existing = CLAIMS.get(key);
                if (existing != null && (existing.ownerUuid.equals(uuid) || player.hasPermissions(2))) {
                    CLAIMS.remove(key);
                    unclaimedCount++;
                }
            }
        }

        if (unclaimedCount > 0) save();
        return unclaimedCount;
    }

    public static boolean isProtected(ServerPlayer player, ChunkPos pos) {
        ClaimData data = CLAIMS.get(getChunkKey(pos.x, pos.z));
        if (data == null) return false;
        return !data.ownerUuid.equals(player.getUUID().toString()) && !player.hasPermissions(2);
    }

    // Возвращает отображаемое имя локации для сайдбара: клейм > данж > дикие земли
    public static String getDisplayLocation(ServerPlayer player) {
        ClaimData data = CLAIMS.get(getChunkKey(player.chunkPosition().x, player.chunkPosition().z));
        if (data != null) {
            return "§6" + data.name;
        }

        String dungeon = DungeonTracker.getDungeonAt(player);
        if (!dungeon.isEmpty()) {
            return dungeon;
        }

        return "§7Дикие Земли";
    }

    public static String getRawTerritoryName(ChunkPos pos) {
        ClaimData data = CLAIMS.get(getChunkKey(pos.x, pos.z));
        return data != null ? data.name : "";
    }

    public static void checkPlayerMovement(ServerPlayer player) {
        ClaimData data = CLAIMS.get(getChunkKey(player.chunkPosition().x, player.chunkPosition().z));
        String currentClaim = data != null ? data.name : "";
        String lastZone = CURRENT_ZONE.getOrDefault(player.getUUID(), "");

        // Реагируем только на вход в именованные феодальные владения игроков
        if (!currentClaim.isEmpty() && !currentClaim.equals(lastZone)) {
            CURRENT_ZONE.put(player.getUUID(), currentClaim);
            sendTerritoryTitle(player, "§6" + currentClaim);
        } else if (currentClaim.isEmpty() && !lastZone.isEmpty()) {
            CURRENT_ZONE.put(player.getUUID(), "");
        }
    }

    private static void sendTerritoryTitle(ServerPlayer player, String name) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 35, 10));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.literal(name)));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("§8[Вход на территорию]")));

        SoundEvent randomSound = DISCOVERY_SOUNDS.get(RANDOM.nextInt(DISCOVERY_SOUNDS.size()));
        Holder<SoundEvent> soundHolder = Holder.direct(randomSound);
        player.connection.send(new ClientboundSoundPacket(
            soundHolder,
            SoundSource.RECORDS,
            player.getX(), player.getY(), player.getZ(),
            1.0f, 1.0f,
            player.level().getRandom().nextLong()
        ));
    }

    public static void handleExplosionOrSiege(MinecraftServer server, BlockPos pos) {
        ChunkPos center = new ChunkPos(pos);
        long now = System.currentTimeMillis();

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                String key = getChunkKey(center.x + dx, center.z + dz);
                ClaimData data = CLAIMS.get(key);

                if (data != null) {
                    try {
                        UUID ownerUuid = UUID.fromString(data.ownerUuid);
                        long lastAlert = SIEGE_COOLDOWNS.getOrDefault(ownerUuid, 0L);

                        if (now - lastAlert > 6000) {
                            SIEGE_COOLDOWNS.put(ownerUuid, now);
                            ServerPlayer owner = server.getPlayerList().getPlayer(ownerUuid);

                            if (owner != null) {
                                owner.connection.send(new ClientboundSetActionBarTextPacket(
                                    Component.literal("§c⚔ ТРЕВОГА! Владения [§6" + data.name + "§c] под артиллерийским обстрелом!")
                                ));

                                owner.connection.send(new ClientboundSoundPacket(
                                    Holder.direct(SoundEvents.BELL_BLOCK),
                                    SoundSource.PLAYERS,
                                    owner.getX(), owner.getY(), owner.getZ(),
                                    1.0f, 1.4f,
                                    owner.level().getRandom().nextLong()
                                ));
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
    }
}
