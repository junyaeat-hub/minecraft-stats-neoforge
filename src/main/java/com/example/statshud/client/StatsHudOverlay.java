package com.example.statshud.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.stats.Stats;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

@EventBusSubscriber(modid = "statshud", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class StatsHudOverlay {

    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CHAT, ResourceLocation.fromNamespaceAndPath("statshud", "hud"), (guiGraphics, deltaTracker) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null || mc.options.hideGui) return;

            String nickname = mc.player.getName().getString();

            int ping = 0;
            int onlineCount = 0;
            if (mc.getConnection() != null) {
                onlineCount = mc.getConnection().getOnlinePlayers().size();
                PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
                if (info != null) {
                    ping = info.getLatency();
                }
            }

            int mobKills = mc.player.getStats().getValue(Stats.CUSTOM.get(Stats.MOB_KILLS));
            long totalDays = mc.level.getDayTime() / 24000L;

            int startX = 8;
            int startY = 45;
            int lineHeight = 11;

            guiGraphics.fill(startX - 4, startY - 4, startX + 130, startY + (lineHeight * 6) + 2, 0x66000000);

            guiGraphics.drawString(mc.font, "§6§lСЕРВЕР §8| §fСтатистика", startX, startY, 0xFFFFFF, true);
            guiGraphics.drawString(mc.font, "§7Игрок: §f" + nickname, startX, startY + lineHeight, 0xFFFFFF, true);
            guiGraphics.drawString(mc.font, "§7В сети: §a" + onlineCount, startX, startY + lineHeight * 2, 0xFFFFFF, true);
            guiGraphics.drawString(mc.font, "§7Пинг: §e" + ping + " ms", startX, startY + lineHeight * 3, 0xFFFFFF, true);
            guiGraphics.drawString(mc.font, "§7Убийств: §c" + mobKills, startX, startY + lineHeight * 4, 0xFFFFFF, true);
            guiGraphics.drawString(mc.font, "§7Игровой день: §b" + totalDays, startX, startY + lineHeight * 5, 0xFFFFFF, true);
        });
    }
}
