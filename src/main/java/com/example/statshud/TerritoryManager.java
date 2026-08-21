package com.example.statshud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.ChatFormatting;
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

    // Хранилище территорий: "X,Z" -> ClaimData
    private static Map<String, ClaimData> CLAIMS = new HashMap<>();

    // Текущая зона игрока (UUID -> Название)
    private static final Map<UUID, String> CURRENT_ZONE = new HashMap<>();

    // Список посещённых зон игрока (UUID -> Set названий)
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

    public static String getChunkKey(ChunkPos pos) {
        return pos.x + "," + pos.z;
    }

    public static boolean claimChunk(ServerPlayer player, String name) {
        String key = getChunkKey(player.chunkPosition());
        if (CLAIMS.containsKey(key)) return false;

        CLAIMS.put(key, new ClaimData(name, player.getUUID().toString()));
        save();
        return true;
    }

    public static boolean unclaimChunk(ServerPlayer player) {
        String key = getChunkKey(player.chunkPosition());
        ClaimData data = CLAIMS.get(key);
        if (data == null || !data.ownerUuid.equals(player.getUUID().toString())) return false;

        CLAIMS.remove(key);
        save();
        return true;
    }

    public static boolean isProtected(ServerPlayer player, ChunkPos pos) {
        ClaimData data = CLAIMS.get(getChunkKey(pos));
        if (data == null) return false;
        return !data.ownerUuid.equals(player.getUUID().toString()) && !player.hasPermissions(2);
    }

    public static String getTerritoryName(ChunkPos pos) {
        ClaimData data = CLAIMS.get(getChunkKey(pos));
        return data != null ? "§6" + data.name : "§7Дикие Земли";
    }

    public static void checkPlayerMovement(ServerPlayer player) {
        String currentName = getTerritoryName(player.chunkPosition());
        String lastZone = CURRENT_ZONE.getOrDefault(player.getUUID(), "");

        if (!currentName.equals(lastZone)) {
            CURRENT_ZONE.put(player.getUUID(), currentName);

            Set<String> visited = VISITED_ZONES.computeIfAbsent(player.getUUID(), k -> new HashSet<>());
            boolean isFirstVisit = visited.add(currentName);

            sendTerritoryTitle(player, currentName, isFirstVisit);
        }
    }

    private static void sendTerritoryTitle(ServerPlayer player, String name, boolean playSound) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 30, 10));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.literal(name)));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(
            playSound ? "§e✦ Новая территория ✦" : "§8[Вход в локацию]"
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
