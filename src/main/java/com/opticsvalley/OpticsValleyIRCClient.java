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
                // 发送消息子命令
                .then(ClientCommandManager.literal("send")
                    .then(ClientCommandManager.argument("message", StringArgumentType.greedyString())
                        .executes(context -> {
                            String message = StringArgumentType.getString(context, "message");
                            if (OpticsValleyIRC.getIRCClient() != null && OpticsValleyIRC.getIRCClient().isConnected()) {
                                OpticsValleyIRC.getIRCClient().sendMessage(message);
                            } else {
                                context.getSource().sendFeedback(Text.literal("§c[OpticsValleyIRC] 未连接到IRC服务器"));
                                context.getSource().sendFeedback(Text.literal("§e[OpticsValleyIRC] 使用 /irc connect 连接到服务器"));
                            }
                            return 1;
                        })
                    )
                )
                // 连接子命令
                .then(ClientCommandManager.literal("connect")
                    .executes(context -> {
                        context.getSource().sendFeedback(Text.literal("§e[OpticsValleyIRC] 正在连接到IRC服务器..."));
                        String username = MinecraftClient.getInstance().getSession().getUsername();
                        
                        if (OpticsValleyIRC.getIRCClient() == null) {
                            // 如果客户端未初始化，先初始化
                            OpticsValleyIRC.initializeClient(username);
                        } else {
                            // 如果客户端已初始化，调用重连方法
                            OpticsValleyIRC.getIRCClient().reconnect();
                        }
                        return 1;
                    })
                )
                // 断开连接子命令
                .then(ClientCommandManager.literal("disconnect")
                    .executes(context -> {
                        if (OpticsValleyIRC.getIRCClient() != null) {
                            context.getSource().sendFeedback(Text.literal("§7[OpticsValleyIRC] 正在断开IRC连接..."));
                            OpticsValleyIRC.getIRCClient().disconnect();
                        } else {
                            context.getSource().sendFeedback(Text.literal("§7[OpticsValleyIRC] 未连接到IRC服务器"));
                        }
                        return 1;
                    })
                )
                // 状态子命令
                .then(ClientCommandManager.literal("status")
                    .executes(context -> {
                        if (OpticsValleyIRC.getIRCClient() != null) {
                            boolean connected = OpticsValleyIRC.getIRCClient().isConnected();
                            context.getSource().sendFeedback(Text.literal(connected ? 
                                "§a[OpticsValleyIRC] 已连接到IRC服务器" : 
                                "§c[OpticsValleyIRC] 未连接到IRC服务器"));
                        } else {
                            context.getSource().sendFeedback(Text.literal("§c[OpticsValleyIRC] IRC客户端未初始化"));
                        }
                        return 1;
                    })
                )
                // 关于子命令
                .then(ClientCommandManager.literal("about")
                    .executes(context -> {
                        context.getSource().sendFeedback(Text.literal("§e=== 关于OpticsValley IRC ==="));
                        context.getSource().sendFeedback(Text.literal("§e作者：§fOpticsValley"));
                        context.getSource().sendFeedback(Text.literal("§e开源链接：§fhttps://github.com/OpticsValleyMC/opticsvalleyirc-fabric-1.20.1"));
                        return 1;
                    })
                )
                // 帮助子命令
                .then(ClientCommandManager.literal("help")
                    .executes(context -> {
                        context.getSource().sendFeedback(Text.literal("§e=== OpticsValley IRC 命令帮助 ==="));
                        context.getSource().sendFeedback(Text.literal("§7/irc send <消息> §f- 发送IRC消息"));
                        context.getSource().sendFeedback(Text.literal("§7/irc connect §f- 连接到IRC服务器"));
                        context.getSource().sendFeedback(Text.literal("§7/irc disconnect §f- 断开IRC连接"));
                        context.getSource().sendFeedback(Text.literal("§7/irc status §f- 查看IRC连接状态"));
                        context.getSource().sendFeedback(Text.literal("§7/irc about §f- 显示模组信息"));
                        context.getSource().sendFeedback(Text.literal("§7/irc help §f- 显示此帮助信息"));
                        return 1;
                    })
                )
                // 默认子命令（发送消息）
                .then(ClientCommandManager.argument("message", StringArgumentType.greedyString())
                    .executes(context -> {
                        String message = StringArgumentType.getString(context, "message");
                        if (OpticsValleyIRC.getIRCClient() != null && OpticsValleyIRC.getIRCClient().isConnected()) {
                            OpticsValleyIRC.getIRCClient().sendMessage(message);
                        } else {
                            context.getSource().sendFeedback(Text.literal("§c[OpticsValleyIRC] 未连接到IRC服务器"));
                            context.getSource().sendFeedback(Text.literal("§e[OpticsValleyIRC] 使用 /irc connect 连接到服务器"));
                        }
                        return 1;
                    })
                )
                // 没有参数时显示帮助
                .executes(context -> {
                    context.getSource().sendFeedback(Text.literal("§e=== OpticsValley IRC 命令帮助 ==="));
                    context.getSource().sendFeedback(Text.literal("§7/irc send <消息> §f- 发送IRC消息"));
                    context.getSource().sendFeedback(Text.literal("§7/irc connect §f- 连接到IRC服务器"));
                    context.getSource().sendFeedback(Text.literal("§7/irc disconnect §f- 断开IRC连接"));
                    context.getSource().sendFeedback(Text.literal("§7/irc status §f- 查看IRC连接状态"));
                    context.getSource().sendFeedback(Text.literal("§7/irc about §f- 显示模组信息"));
                    context.getSource().sendFeedback(Text.literal("§7/irc help §f- 显示此帮助信息"));
                    return 1;
                })
            );
        });
    }
} 