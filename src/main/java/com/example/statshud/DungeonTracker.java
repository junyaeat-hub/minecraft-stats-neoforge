package com.example.statshud;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceLocation;
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
    private static final Map<UUID, String> CURRENT_DUNGEON = new HashMap<>();

    public static String getDungeonAt(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        BlockPos pos = player.blockPosition();

        try {
            var structureManager = level.structureManager();
            var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);

            // Получаем все структуры, внутри которых физически находится игрок
            Map<Structure, ?> structures = structureManager.getAllStructuresAt(pos);

            for (Structure structure : structures.keySet()) {
                ResourceLocation id = registry.getKey(structure);
                if (id == null) continue;

                String path = id.getPath().toLowerCase();
                String namespace = id.getNamespace().toLowerCase();

                // 1. Cataclysm bosses & dungeons
                if (namespace.contains("cataclysm")) {
                    if (path.contains("monstrosity") || path.contains("soul_black_smith")) return "§4Кузница Душ (Monstrosity)";
                    if (path.contains("sunken_city")) return "§1Затонувший Город (Leviathan)";
                    if (path.contains("burning_arena") || path.contains("ignis")) return "§cПылающая Арена (Ignis)";
                    if (path.contains("ancient_factory")) return "§6Древняя Фабрика (Harbinger)";
                    if (path.contains("cursed_pyramid")) return "§eПроклятая Пирамида";
                    return "§4Подземелье Cataclysm";
                }

                // 2. YUNG's & Vanilla Strongholds
                if (path.contains("stronghold")) return "§4Древняя Крепость (Stronghold)";
                if (path.contains("fortress")) return "§cАдская Цитадель (Nether Fortress)";
                if (path.contains("bastion")) return "§6Бастион Пиглинов";
                if (path.contains("mineshaft")) return "§8Заброшенные Шахты";
                if (path.contains("monument") || path.contains("ocean_monument")) return "§9Подводный Монумент";
                if (path.contains("ancient_city")) return "§3Древний Город (Ancient City)";
                if (path.contains("trial_chamber")) return "§6Камеры Испытаний (Trial Chambers)";
                if (path.contains("witch_hut")) return "§2Ведьмино Логово";
                if (path.contains("desert_temple") || path.contains("desert_pyramid")) return "§eДревняя Пирамида";
                if (path.contains("jungle_temple")) return "§2Храм в Джунглях";

                // 3. Deeper and Darker / Alex's Caves
                if (namespace.contains("deeperdarker") || path.contains("otherside")) return "§1Темные Глубины (Otherside)";
                if (namespace.contains("alexscaves")) return "§dТайные Пещеры";
            }
        } catch (Exception ignored) {}

        return "";
    }

    public static void checkPlayerDungeon(ServerPlayer player) {
        String dungeon = getDungeonAt(player);
        String lastDungeon = CURRENT_DUNGEON.getOrDefault(player.getUUID(), "");

        // Игрок вошел в подземелье
        if (!dungeon.isEmpty() && !dungeon.equals(lastDungeon)) {
            CURRENT_DUNGEON.put(player.getUUID(), dungeon);
            sendDungeonAlert(player, dungeon);
        } 
        // Игрок вышел из подземелья
        else if (dungeon.isEmpty() && !lastDungeon.isEmpty()) {
            CURRENT_DUNGEON.put(player.getUUID(), "");
        }
    }

    private static void sendDungeonAlert(ServerPlayer player, String dungeonName) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 45, 10));
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
