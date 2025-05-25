package com.opticsvalley;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;

public class OpticsValleyIRCClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // 当游戏启动时连接IRC
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            String username = MinecraftClient.getInstance().getSession().getUsername();
            OpticsValleyIRC.initializeClient(username);
        });

        // 当玩家离开游戏时断开IRC
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            OpticsValleyIRC.initializeClient(null);
        });
        
        // 当游戏关闭时断开IRC
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            OpticsValleyIRC.initializeClient(null);
        });
    }
} 