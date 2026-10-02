package com.example.autology;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;

public class AutoLogYClient implements ClientModInitializer {
    private static final double Y_LEVEL = -5;
    private static final int RECONNECT_DELAY_TICKS = 5; // 0.25 seconds
    private static final int REARM_TICKS = 40; // must stay above Y_LEVEL for 2 seconds to re-arm

    private boolean armed = true;
    private ServerInfo lastServer;
    private int reconnectTicks = -1;
    private int aboveTicks = 0;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);

        // "Reconnect now" button on the disconnect screen
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof DisconnectedScreen && lastServer != null) {
                Screens.getButtons(screen).add(
                    ButtonWidget.builder(Text.literal("Reconnect now"), b -> reconnect(client))
                        .dimensions(scaledWidth / 2 - 100, 10, 200, 20)
                        .build()
                );
            }
        });
    }

    private void reconnect(MinecraftClient mc) {
        if (lastServer == null || mc.world != null) return;
        reconnectTicks = -1; // cancel the auto countdown
        ConnectScreen.connect(
            new MultiplayerScreen(new TitleScreen()),
            mc,
            ServerAddress.parse(lastServer.address),
            lastServer,
            false,
            null
        );
    }

    private void onTick(MinecraftClient mc) {
        // Remember the server you're on, so you can rejoin after any disconnect
        ServerInfo current = mc.getCurrentServerEntry();
        if (current != null) lastServer = current;

        // Waiting to reconnect after an AutoLogY disconnect
        if (reconnectTicks >= 0) {
            reconnectTicks--;
            if (reconnectTicks < 0) reconnect(mc);
            return;
        }

        if (mc.player == null || mc.getNetworkHandler() == null) {
            aboveTicks = 0;
            return;
        }

        double y = mc.player.getY();

        // Re-arm only after staying above the trigger level for a while.
        // (Right after joining, the player can briefly appear at a wrong height.)
        if (y > Y_LEVEL) {
            if (!armed && ++aboveTicks >= REARM_TICKS) armed = true;
            return;
        }
        aboveTicks = 0;

        if (!armed) return;
        if (current == null) return; // singleplayer: do nothing

        armed = false;
        reconnectTicks = RECONNECT_DELAY_TICKS;
        mc.getNetworkHandler().getConnection().disconnect(
            Text.literal("[AutoLogY] Reached Y = " + (int) y)
        );
    }
}
