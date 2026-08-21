package com.example.statshud;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.projectile.Projectile;
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
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Mod(StatsHudMod.MODID)
@EventBusSubscriber(modid = StatsHudMod.MODID)
public class StatsHudMod {
    public static final String MODID = "statshud";
    private static int tickCounter = 0;
    private static final Set<String> INITIALIZED_PLAYERS = new HashSet<>();

    public StatsHudMod() {}

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        TerritoryManager.load();
    }

    @SubscribeEvent
    public static void onExplosionStart(ExplosionEvent.Start event) {
        if (!event.getLevel().isClientSide() && event.getLevel().getServer() != null) {
            BlockPos explosionPos = BlockPos.containing(event.getExplosion().center());
            TerritoryManager.handleExplosionOrSiege(event.getLevel().getServer(), explosionPos);
        }
    }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        Projectile projectile = event.getProjectile();
        if (projectile.level().isClientSide() || projectile.level().getServer() == null) return;

        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(projectile.getType());

        if (id != null) {
            String ns = id.getNamespace().toLowerCase();
            String path = id.getPath().toLowerCase();

            if (ns.contains("siegemachines") || ns.contains("createbigcannons") || ns.contains("valarian") || path.contains("cannon") || path.contains("boulder") || path.contains("ballista") || path.contains("shot")) {
                BlockPos impactPos = BlockPos.containing(event.getRayTraceResult().getLocation());
                TerritoryManager.handleExplosionOrSiege(projectile.level().getServer(), impactPos);
            }
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("raid")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("test")
                    .executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                        ValarianRaidManager.forceRaid(player);
                        return 1;
                    })
                )
        );

        event.getDispatcher().register(
            Commands.literal("claim")
                .then(Commands.argument("radius", IntegerArgumentType.integer(0, 5))
                    .then(Commands.argument("name", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            int radius = IntegerArgumentType.getInteger(ctx, "radius");
                            String name = StringArgumentType.getString(ctx, "name");
                            int count = TerritoryManager.claimArea(player, name, radius);
                            player.sendSystemMessage(Component.literal("§a✔ Захвачено чанков: §e" + count + " §aпод именем §6" + name));
                            return count;
                        })
                    )
                )
                .then(Commands.argument("name", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                        String name = StringArgumentType.getString(ctx, "name");
                        int count = TerritoryManager.claimArea(player, name, 0);
                        if (count > 0) {
                            player.sendSystemMessage(Component.literal("§a✔ Чанк добавлен во владения: §6" + name));
                        } else {
                            player.sendSystemMessage(Component.literal("§c✖ Этот чанк занят чужим феодом!"));
                        }
                        return count;
                    })
                )
        );

        event.getDispatcher().register(
            Commands.literal("unclaim")
                .then(Commands.argument("radius", IntegerArgumentType.integer(0, 5))
                    .executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                        int radius = IntegerArgumentType.getInteger(ctx, "radius");
                        int count = TerritoryManager.unclaimArea(player, radius);
                        player.sendSystemMessage(Component.literal("§e✔ Освобождено чанков: §f" + count));
                        return count;
                    })
                )
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    int count = TerritoryManager.unclaimArea(player, 0);
                    if (count > 0) {
                        player.sendSystemMessage(Component.literal("§e✔ Земли в этом чанке освобождены."));
                    } else {
                        player.sendSystemMessage(Component.literal("§c✖ Вы не владеете этим чанком!"));
                    }
                    return count;
                })
        );
    }

    private static String getPlayerObjectiveName(ServerPlayer player) {
        return "sb_" + Math.abs(player.getGameProfile().getName().hashCode() % 100000);
    }

    private static void setupPlayerScoreboard(ServerPlayer player) {
        String objName = getPlayerObjectiveName(player);
        Objective dummyObj = new Objective(
            new Scoreboard(),
            objName,
            ObjectiveCriteria.DUMMY,
            Component.literal("§6§lСЕРВЕР §8| §fСтатистика"),
            ObjectiveCriteria.RenderType.INTEGER,
            true,
            null
        );

        player.connection.send(new ClientboundSetObjectivePacket(dummyObj, ClientboundSetObjectivePacket.METHOD_ADD));
        player.connection.send(new ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, dummyObj));
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            INITIALIZED_PLAYERS.remove(player.getGameProfile().getName());
        }
    }

    @SubscribeEvent
    public static void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        INITIALIZED_PLAYERS.remove(event.getEntity().getGameProfile().getName());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (++tickCounter % 20 != 0) return;

        MinecraftServer server = event.getServer();
        if (server == null) return;

        ValarianRaidManager.tick(server);

        int onlineCount = server.getPlayerCount();
        long totalDays = server.overworld().getDayTime() / 24000L;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            String pName = player.getGameProfile().getName();

            if (!INITIALIZED_PLAYERS.contains(pName)) {
                setupPlayerScoreboard(player);
                INITIALIZED_PLAYERS.add(pName);
            }

            TerritoryManager.checkPlayerMovement(player);
            DungeonTracker.checkPlayerDungeon(player);

            ServerStatsCounter stats = server.getPlayerList().getPlayerStats(player);
            int mobKills = stats.getValue(Stats.CUSTOM.get(Stats.MOB_KILLS));
            int ping = player.connection.latency();
            String location = TerritoryManager.getDisplayLocation(player);

            String objName = getPlayerObjectiveName(player);
            String safePrefix = "t" + Math.abs(pName.hashCode() % 1000) + "_";

            sendPersonalLine(player, objName, 6, "§7Локация: " + location, safePrefix + "6");
            sendPersonalLine(player, objName, 5, "§7Игрок: §f" + pName, safePrefix + "5");
            sendPersonalLine(player, objName, 4, "§7В сети: §a" + onlineCount, safePrefix + "4");
            sendPersonalLine(player, objName, 3, "§7Пинг: §e" + ping + " ms", safePrefix + "3");
            sendPersonalLine(player, objName, 2, "§7Убийств: §c" + mobKills, safePrefix + "2");
            sendPersonalLine(player, objName, 1, "§7Игровой день: §b" + totalDays, safePrefix + "1");
        }
    }

    private static void sendPersonalLine(ServerPlayer player, String objectiveName, int score, String text, String teamName) {
        // Уникальный цветной маркер для каждой позиции в строках
        String entry = ChatFormatting.values()[score].toString() + ChatFormatting.RESET;

        PlayerTeam team = new PlayerTeam(new Scoreboard(), teamName);
        team.setPlayerPrefix(Component.literal(text));

        player.connection.send(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team, true));
        player.connection.send(ClientboundSetPlayerTeamPacket.createPlayerPacket(team, entry, ClientboundSetPlayerTeamPacket.Action.ADD));

        ScoreHolder holder = () -> entry;
        player.connection.send(new ClientboundSetScorePacket(holder.getScoreboardName(), objectiveName, score, Optional.empty(), Optional.empty()));
    }
}
