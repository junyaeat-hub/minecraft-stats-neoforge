package com.example.statshud;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.Stats;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Collections;
import java.util.Optional;

@Mod(StatsHudMod.MODID)
@EventBusSubscriber(modid = StatsHudMod.MODID)
public class StatsHudMod {
    public static final String MODID = "statshud";
    private static final String OBJECTIVE_NAME = "p_stats";
    private static int tickCounter = 0;

    public StatsHudMod() {
    }

    // Инициализация персонального сайдбара при входе игрока
    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Objective dummyObj = new Objective(
                new Scoreboard(),
                OBJECTIVE_NAME,
                ObjectiveCriteria.DUMMY,
                Component.literal("§6§lСЕРВЕР §8| §fСтатистика"),
                ObjectiveCriteria.RenderType.INTEGER,
                true,
                null
            );

            // Создаем объектив и ставим в SIDEBAR
            player.connection.send(new ClientboundSetObjectivePacket(dummyObj, ClientboundSetObjectivePacket.METHOD_ADD));
            player.connection.send(new ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, dummyObj));
        }
    }

    // Персональное обновление данных раз в 20 тиков (1 сек)
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (++tickCounter % 20 != 0) return;

        MinecraftServer server = event.getServer();
        if (server == null) return;

        int onlineCount = server.getPlayerCount();
        long totalDays = server.overworld().getDayTime() / 24000L;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerStatsCounter stats = server.getPlayerList().getPlayerStats(player);
            int mobKills = stats.getValue(Stats.CUSTOM.get(Stats.MOB_KILLS));
            int ping = player.connection.latency();

            sendPersonalLine(player, 5, "§7Игрок: §f" + player.getName().getString(), "tm_5");
            sendPersonalLine(player, 4, "§7В сети: §a" + onlineCount, "tm_4");
            sendPersonalLine(player, 3, "§7Пинг: §e" + ping + " ms", "tm_3");
            sendPersonalLine(player, 2, "§7Убийств: §c" + mobKills, "tm_2");
            sendPersonalLine(player, 1, "§7Игровой день: §b" + totalDays, "tm_1");
        }
    }

    private static void sendPersonalLine(ServerPlayer player, int score, String text, String teamName) {
        String entry = ChatFormatting.values()[score].toString() + ChatFormatting.RESET;

        PlayerTeam team = new PlayerTeam(new Scoreboard(), teamName);
        team.setPlayerPrefix(Component.literal(text));

        // Отправляем команду и префикс (создание/обновление строки)
        player.connection.send(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team, true));
        player.connection.send(ClientboundSetPlayerTeamPacket.createPlayerPacket(team, entry, ClientboundSetPlayerTeamPacket.Action.ADD));

        // Отправляем слот и позицию строки
        player.connection.send(new ClientboundSetScorePacket(entry, OBJECTIVE_NAME, score, Optional.empty(), Optional.empty()));
    }
}
