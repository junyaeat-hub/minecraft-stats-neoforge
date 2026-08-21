package com.example.statshud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
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

    // Хранилище: "X,Z" -> ClaimData
    private static Map<String, ClaimData> CLAIMS = new HashMap<>();

    // Отслеживание текущей зоны (UUID -> Имя зоны)
    private static final Map<UUID, String> CURRENT_ZONE = new HashMap<>();

    // Список открытых зон (UUID -> Set имён)
    private static final Map<UUID, Set<String>> VISITED_ZONES = new HashMap<>();

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
                // Можно занять свободный чанк или переименовать свой
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

    public static String getTerritoryName(ChunkPos pos) {
        ClaimData data = CLAIMS.get(getChunkKey(pos.x, pos.z));
        return data != null ? "§6" + data.name : "§7Дикие Земли";
    }

    public static void checkPlayerMovement(ServerPlayer player) {
        String currentName = getTerritoryName(player.chunkPosition());
        String lastZone = CURRENT_ZONE.getOrDefault(player.getUUID(), "");

        // Срабатывает только при смене названия территории
        if (!currentName.equals(lastZone)) {
            CURRENT_ZONE.put(player.getUUID(), currentName);

            if (!currentName.equals("§7Дикие Земли")) {
                Set<String> visited = VISITED_ZONES.computeIfAbsent(player.getUUID(), k -> new HashSet<>());
                boolean isFirstVisit = visited.add(currentName);
                sendTerritoryTitle(player, currentName, isFirstVisit);
            } else {
                sendTerritoryTitle(player, currentName, false);
            }
        }
    }

    private static void sendTerritoryTitle(ServerPlayer player, String name, boolean playSound) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 35, 10));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.literal(name)));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(
            playSound ? "§e✦ Новые владения открыты ✦" : "§8[Вход на территорию]"
        )));

        if (playSound) {
            Holder<SoundEvent> soundHolder = Holder.direct(SoundEvents.RAID_HORN.value());
            player.connection.send(new ClientboundSoundPacket(
                soundHolder,
                SoundSource.RECORDS,
                player.getX(), player.getY(), player.getZ(),
                1.0f, 1.0f,
                player.level().getRandom().nextLong()
            ));
        }
    }
}
