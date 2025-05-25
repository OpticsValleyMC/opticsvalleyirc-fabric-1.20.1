package com.opticsvalley;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.text.Text;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.server.command.CommandManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpticsValleyIRC implements ModInitializer {
	public static final String MOD_ID = "opticsvalleyirc";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	private static IRCClient ircClient;

	@Override
	public void onInitialize() {
		LOGGER.info("OpticsValley IRC initializing...");
		
		// 注册IRC命令
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(CommandManager.literal("irc")
				.then(CommandManager.argument("message", StringArgumentType.greedyString())
					.executes(context -> {
						String message = StringArgumentType.getString(context, "message");
						if (ircClient != null && ircClient.isConnected()) {
							ircClient.sendMessage(message);
						} else {
							context.getSource().sendMessage(Text.literal("§c[OpticsValleyIRC] 未连接到IRC服务器"));
						}
						return 1;
					})
				)
			);
		});
	}

	public static void initializeClient(String username) {
		if (username == null) {

			if (ircClient != null) {
				ircClient.disconnect();
				ircClient = null;
			}
		} else {

			if (ircClient != null) {
				ircClient.disconnect();
			}
			ircClient = new IRCClient(username);
			ircClient.connect();
		}
	}
}