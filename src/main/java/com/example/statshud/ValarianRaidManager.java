public static boolean spawnRaid(ServerPlayer targetPlayer, String territoryName, String factionName) {
        ServerLevel level = (ServerLevel) targetPlayer.level();
        MinecraftServer server = level.getServer();
        BlockPos playerPos = targetPlayer.blockPosition();

        // Фильтруем строго живых мобов (отсекаем стрелы, ядра, мосты и повозки)
        List<EntityType<?>> valarianEntities = BuiltInRegistries.ENTITY_TYPE.entrySet().stream()
            .filter(entry -> {
                String ns = entry.getKey().location().getNamespace().toLowerCase();
                String path = entry.getKey().location().getPath().toLowerCase();
                boolean isValarian = ns.contains("valarian") || path.contains("valarian");
                boolean isNotBlacklisted = !path.contains("projectile") && !path.contains("ballista") && !path.contains("cannon") && !path.contains("catapult") && !path.contains("carriage") && !path.contains("drawbridge") && !path.contains("ram");
                return isValarian && isNotBlacklisted;
            })
            .map(java.util.Map.Entry::getValue)
            .toList();

        if (valarianEntities.isEmpty()) {
            BuiltInRegistries.ENTITY_TYPE.getOptional(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("valarian_conquest", "archer"))
                .ifPresent(valarianEntities::add);
        }

        if (valarianEntities.isEmpty()) {
            targetPlayer.sendSystemMessage(Component.literal("§c[Ошибка] Живые бойцы Valarian не найдены!"));
            return false;
        }

        String rawTerritory = TerritoryManager.getRawTerritoryName(targetPlayer.chunkPosition());
        Holder<SoundEvent> soundHolder = Holder.direct(SoundEvents.RAID_HORN.value());

        // Оповещаем и цель, и всех игроков внутри этого же клейма
        for (ServerPlayer onlinePlayer : server.getPlayerList().getPlayers()) {
            String playerCurrentClaim = TerritoryManager.getRawTerritoryName(onlinePlayer.chunkPosition());
            boolean isInsideUnderAttackClaim = !rawTerritory.isEmpty() && playerCurrentClaim.equals(rawTerritory);
            boolean isTarget = onlinePlayer.getUUID().equals(targetPlayer.getUUID());

            if (isTarget || isInsideUnderAttackClaim) {
                onlinePlayer.connection.send(new ClientboundSetActionBarTextPacket(
                    Component.literal("§c⚔ НАБЕГ! Войска " + factionName + " штурмуют [" + territoryName + "§c]! ⚔")
                ));

                onlinePlayer.sendSystemMessage(Component.literal("§4[Осада] §cОтряд " + factionName + " атакует ваши земли [" + territoryName + "§c]! К оружию!"));

                onlinePlayer.connection.send(new ClientboundSoundPacket(
                    soundHolder,
                    SoundSource.HOSTILE,
                    onlinePlayer.getX(), onlinePlayer.getY(), onlinePlayer.getZ(),
                    1.5f, 0.9f,
                    level.getRandom().nextLong()
                ));
            }
        }

        int squadSize = 6 + RANDOM.nextInt(5);
        int count = 0;

        for (int i = 0; i < squadSize; i++) {
            double angle = RANDOM.nextDouble() * 2 * Math.PI;
            double distance = 18 + RANDOM.nextDouble() * 10;

            int spawnX = playerPos.getX() + (int) (Math.cos(angle) * distance);
            int spawnZ = playerPos.getZ() + (int) (Math.sin(angle) * distance);
            int spawnY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawnX, spawnZ);

            BlockPos spawnPos = new BlockPos(spawnX, spawnY, spawnY <= level.getMinBuildHeight() ? playerPos.getY() : spawnY);

            EntityType<?> chosenType = valarianEntities.get(RANDOM.nextInt(valarianEntities.size()));
            Entity createdEntity = chosenType.create(level);
            if (createdEntity instanceof Mob mob) {
                mob.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
                mob.setTarget(targetPlayer);
                level.addFreshEntity(mob);
                count++;
            }
        }

        targetPlayer.sendSystemMessage(Component.literal("§a[Набег] Прибыло бойцов: §e" + count));
        return true;
    }
