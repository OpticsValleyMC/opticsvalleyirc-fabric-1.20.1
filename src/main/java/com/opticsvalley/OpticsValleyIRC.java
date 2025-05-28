package com.opticsvalley;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpticsValleyIRC implements ModInitializer {
	public static final String MOD_ID = "opticsvalleyirc";
	public static final String VERSION = "1.0.3";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	private static IRCClient ircClient;

	@Override
	public void onInitialize() {
		LOGGER.info("OpticsValley IRC {} initializing...", VERSION);
	}

	/**
	 * 获取IRC客户端实例
	 * @return IRC客户端实例，如果未初始化则返回null
	 */
	public static IRCClient getIRCClient() {
		return ircClient;
	}

	/**
	 * 初始化或关闭IRC客户端
	 * @param username 玩家用户名，如果为null则断开连接
	 */
	public static void initializeClient(String username) {
		if (username == null) {
			// 断开连接情况
			if (ircClient != null) {
				LOGGER.info("正在断开IRC连接...");
				ircClient.disconnect();
				ircClient = null;
			}
		} else {
			// 连接情况
			if (ircClient != null) {
				// 如果客户端实例已存在但断开连接，尝试重连
				if (!ircClient.isConnected()) {
					LOGGER.info("IRC客户端存在但未连接，尝试重连...");
					ircClient.reconnect();
				} else {
					// 已连接，不做任何处理
					LOGGER.info("IRC客户端已连接，无需重新初始化");
				}
			} else {
				// 创建新的客户端实例
				LOGGER.info("创建新的IRC客户端实例，用户名: {}", username);
				ircClient = new IRCClient(username);
				ircClient.connect();
			}
		}
	}
	
	/**
	 * 重新连接IRC服务器
	 * @param username 玩家用户名
	 */
	public static void reconnectClient(String username) {
		if (username == null) {
			LOGGER.warn("无法重连IRC：用户名为空");
			return;
		}
		
		if (ircClient != null) {
			// 如果客户端实例存在，直接调用重连方法
			LOGGER.info("尝试重新连接到IRC服务器...");
			ircClient.reconnect();
		} else {
			// 如果客户端实例不存在，创建新实例
			LOGGER.info("IRC客户端实例不存在，创建新实例，用户名: {}", username);
			ircClient = new IRCClient(username);
			ircClient.connect();
		}
	}
}