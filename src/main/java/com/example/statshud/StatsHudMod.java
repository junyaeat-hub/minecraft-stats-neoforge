@SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (++tickCounter % 20 != 0) return;

        MinecraftServer server = event.getServer();
        if (server == null) return;

        ValarianRaidManager.tick(server);

        int onlineCount = server.getPlayerCount();
        long totalDays = server.overworld().getDayTime() / 24000L;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            TerritoryManager.checkPlayerMovement(player);
            DungeonTracker.checkPlayerDungeon(player);

            ServerStatsCounter stats = server.getPlayerList().getPlayerStats(player);
            int mobKills = stats.getValue(Stats.CUSTOM.get(Stats.MOB_KILLS));
            int ping = player.connection.latency();
            String location = TerritoryManager.getDisplayLocation(player);
            
            // Уникальный префикс для каждого игрока по его UUID
            String pKey = player.getStringUUID().substring(0, 8);

            sendPersonalLine(player, 6, "§7Локация: " + location, "loc_" + pKey);
            sendPersonalLine(player, 5, "§7Игрок: §f" + player.getName().getString(), "name_" + pKey);
            sendPersonalLine(player, 4, "§7В сети: §a" + onlineCount, "onl_" + pKey);
            sendPersonalLine(player, 3, "§7Пинг: §e" + ping + " ms", "png_" + pKey);
            sendPersonalLine(player, 2, "§7Убийств: §c" + mobKills, "kll_" + pKey);
            sendPersonalLine(player, 1, "§7Игровой день: §b" + totalDays, "day_" + pKey);
        }
    }

    private static void sendPersonalLine(ServerPlayer player, int score, String text, String teamName) {
        // Уникальный невидимый маркер для каждой строки
        String entry = ChatFormatting.values()[score].toString() + ChatFormatting.RESET;

        PlayerTeam team = new PlayerTeam(new Scoreboard(), teamName);
        team.setPlayerPrefix(Component.literal(text));

        player.connection.send(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team, true));
        player.connection.send(ClientboundSetPlayerTeamPacket.createPlayerPacket(team, entry, ClientboundSetPlayerTeamPacket.Action.ADD));

        ScoreHolder holder = () -> entry;
        player.connection.send(new ClientboundSetScorePacket(holder.getScoreboardName(), OBJECTIVE_NAME, score, Optional.empty(), Optional.empty()));
    }
