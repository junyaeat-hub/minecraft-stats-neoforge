package com.example.statshud;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DungeonTracker {
    private static final Map<UUID, String> PLAYER_DUNGEON = new HashMap<>();

    public static void checkPlayerDungeon(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        BlockPos pos = player.blockPosition();

        String dungeonName = "";

        if (isInsideStructure(level, pos, "stronghold")) {
            dungeonName = "§4Древняя Крепость (Stronghold)";
        } else if (isInsideStructure(level, pos, "fortress")) {
            dungeonName = "§cАдская Крепость (Nether Fortress)";
        } else if (isInsideStructure(level, pos, "mineshaft")) {
            dungeonName = "§8Заброшенные Шахты";
        } else if (isInsideStructure(level, pos, "monument")) {
            dungeonName = "§9Подводный Монумент";
        } else if (isInsideStructure(level, pos, "desert_pyramid") || isInsideStructure(level, pos, "jungle_temple")) {
            dungeonName = "§eДревний Храм";
        }

        String lastDungeon = PLAYER_DUNGEON.getOrDefault(player.getUUID(), "");

        if (!dungeonName.isEmpty() && !dungeonName.equals(lastDungeon)) {
            PLAYER_DUNGEON.put(player.getUUID(), dungeonName);
            sendDungeonAlert(player, dungeonName);
        } else if (dungeonName.isEmpty() && !lastDungeon.isEmpty()) {
            PLAYER_DUNGEON.put(player.getUUID(), "");
            player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 20, 10));
            player.connection.send(new ClientboundSetTitleTextPacket(Component.literal("§2Поверхность")));
            player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("§8[Дикие Земли]")));
        }
    }

    private static boolean isInsideStructure(ServerLevel level, BlockPos pos, String structureKey) {
        try {
            var structureManager = level.structureManager();
            var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
            for (var entry : registry.entrySet()) {
                if (entry.getKey().location().getPath().contains(structureKey)) {
                    Structure structure = entry.getValue();
                    if (structureManager.getStructureAt(pos, structure).isValid()) {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static void sendDungeonAlert(ServerPlayer player, String dungeonName) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(15, 40, 15));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.literal(dungeonName)));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("§4⚠ Зона повышенной опасности ⚠")));

        Holder<SoundEvent> soundHolder = Holder.direct(SoundEvents.WARDEN_HEARTBEAT);
        player.connection.send(new ClientboundSoundPacket(
            soundHolder,
            SoundSource.HOSTILE,
            player.getX(), player.getY(), player.getZ(),
            1.0f, 0.5f,
            player.level().getRandom().nextLong()
        ));
    }
}
