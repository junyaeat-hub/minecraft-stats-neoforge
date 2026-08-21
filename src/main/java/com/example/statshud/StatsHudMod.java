package com.example.statshud;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
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
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Optional;

@Mod(StatsHudMod.MODID)
@EventBusSubscriber(modid = StatsHudMod.MODID)
public class StatsHudMod {
    public static final String MODID = "statshud";
    private static final String OBJECTIVE_NAME = "p_stats";
    private static int tickCounter = 0;

    public StatsHudMod() {}

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        TerritoryManager.load();
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("claim")
                .then(Commands.argument("name", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                        String name = StringArgumentType.getString(ctx, "name");
                        if (TerritoryManager.claimChunk(player, name)) {
                            player.sendSystemMessage(Component.literal("§a✔ Вы успешно заявили права на эти земли: §6" + name));
                        } else {
                            player.sendSystemMessage(Component.literal("§c✖ Этот чанк уже кем-то занят!"));
                        }
                        return 1;
                    })
                )
        );

        event.getDispatcher().register(
            Commands.literal("unclaim")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    if (TerritoryManager.unclaimChunk(player)) {
                        player.sendSystemMessage(Component.literal("§e✔ Вы освободили эту территорию."));
                    } else {
                        player.sendSystemMessage(Component.literal("§c✖ Вы не владеете этим чанком!"));
                    }
                    return 1;
                })
        );
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player) {
            if (TerritoryManager.isProtected(player, player.chunkPosition())) {
                player.sendSystemMessage(Component.literal("§c✖ Вы не можете разрушать чужие владения!"));
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Objective dummyObj = new Objective(
                new Scoreboard(),
                OBJECTIVE_NAME,
                ObjectiveCriteria.DUMMY,
                Component.literal("§6§lСЕРВЕР §8| §fИнфо"),
                ObjectiveCriteria.RenderType.INTEGER,
                true,
                null
            );

            player.connection.send(new ClientboundSetObjectivePacket(dummyObj, ClientboundSetObjectivePacket.METHOD_ADD));
            player.connection.send(new ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, dummyObj));
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (++tickCounter % 20 != 0) return;

        MinecraftServer server = event.getServer();
        if (server == null) return;

        int onlineCount = server.getPlayerCount();
        long totalDays = server.overworld().getDayTime() / 24000L;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            TerritoryManager.checkPlayerMovement(player);

            ServerStatsCounter stats = server.getPlayerList().getPlayerStats(player);
            int mobKills = stats.getValue(Stats.CUSTOM.get(Stats.MOB_KILLS));
            int ping = player.connection.latency();
            String territory = TerritoryManager.getTerritoryName(player.chunkPosition());

            sendPersonalLine(player, 6, "§7Локация: " + territory, "tm_6");
            sendPersonalLine(player, 5, "§7Игрок: §f" + player.getName().getString(), "tm_5");
            sendPersonalLine(player, 4, "§7В сети: §a" + onlineCount, "tm_4");
            sendPersonalLine(player, 3, "§7Пинг: §e" + ping + " ms", "line_ping");
            sendPersonalLine(player, 2, "§7Убийств: §c" + mobKills, "tm_2");
            sendPersonalLine(player, 1, "§7Игровой день: §b" + totalDays, "tm_1");
        }
    }

    private static void sendPersonalLine(ServerPlayer player, int score, String text, String teamName) {
        String entry = ChatFormatting.values()[score].toString() + ChatFormatting.RESET;

        PlayerTeam team = new PlayerTeam(new Scoreboard(), teamName);
        team.setPlayerPrefix(Component.literal(text));

        player.connection.send(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team, true));
        player.connection.send(ClientboundSetPlayerTeamPacket.createPlayerPacket(team, entry, ClientboundSetPlayerTeamPacket.Action.ADD));

        player.connection.send(new ClientboundSetScorePacket(entry, OBJECTIVE_NAME, score, Optional.empty(), Optional.empty()));
    }
}
