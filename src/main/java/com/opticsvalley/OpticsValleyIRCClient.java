package com.opticsvalley;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import com.mojang.brigadier.arguments.StringArgumentType;

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
        
        // 注册客户端IRC命令
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("irc")
                .then(ClientCommandManager.argument("message", StringArgumentType.greedyString())
                    .executes(context -> {
                        String message = StringArgumentType.getString(context, "message");
                        if (OpticsValleyIRC.getIRCClient() != null && OpticsValleyIRC.getIRCClient().isConnected()) {
                            OpticsValleyIRC.getIRCClient().sendMessage(message);
                        } else {
                            context.getSource().sendFeedback(Text.literal("§c[OpticsValleyIRC] 未连接到IRC服务器"));
                        }
                        return 1;
                    })
                )
            );
        });
    }
} 