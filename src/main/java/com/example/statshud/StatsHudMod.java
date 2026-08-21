package com.example.statshud;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.Stats;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@Mod(StatsHudMod.MODID)
@EventBusSubscriber(modid = StatsHudMod.MODID)
public class StatsHudMod {
    public static final String MODID = "statshud";
    private static int tickCounter = 0;

    public StatsHudMod() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (++tickCounter % 20 != 0) return;

        MinecraftServer server = event.getServer();
        if (server == null) return;

        Scoreboard scoreboard = server.getScoreboard();
        Objective objective = scoreboard.getObjective("server_stats");

        if (objective == null) {
            objective = scoreboard.addObjective(
                "server_stats",
                ObjectiveCriteria.DUMMY,
                Component.literal("§6§lСЕРВЕР §8| §fСтатистика"),
                ObjectiveCriteria.RenderType.INTEGER,
                true,
                null
            );
            scoreboard.setDisplayObjective(DisplaySlot.SIDEBAR, objective);
        }

        int onlineCount = server.getPlayerCount();
        long totalDays = server.overworld().getDayTime() / 24000L;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerStatsCounter stats = server.getPlayerList().getPlayerStats(player);
            int mobKills = stats.getValue(Stats.CUSTOM.get(Stats.MOB_KILLS));
            int ping = player.connection.latency();

            updateSidebarLine(scoreboard, objective, 5, "§7Игрок: §f" + player.getName().getString(), "line_player");
            updateSidebarLine(scoreboard, objective, 4, "§7В сети: §a" + onlineCount, "line_online");
            updateSidebarLine(scoreboard, objective, 3, "§7Пинг: §e" + ping + " ms", "line_ping");
            updateSidebarLine(scoreboard, objective, 2, "§7Убийств: §c" + mobKills, "line_kills");
            updateSidebarLine(scoreboard, objective, 1, "§7Игровой день: §b" + totalDays, "line_day");
        }
    }

    private static void updateSidebarLine(Scoreboard scoreboard, Objective objective, int score, String text, String teamName) {
        String entry = ChatFormatting.values()[score].toString() + ChatFormatting.RESET;
        PlayerTeam team = scoreboard.getPlayerTeam(teamName);
        if (team == null) {
            team = scoreboard.addPlayerTeam(teamName);
            scoreboard.addPlayerToTeam(entry, team);
        }
        team.setPlayerPrefix(Component.literal(text));
        
        // Исправленная строка для версии 1.21.1:
        scoreboard.getOrCreatePlayerScore(ScoreHolder.forName(entry), objective).set(score);
    }
}
