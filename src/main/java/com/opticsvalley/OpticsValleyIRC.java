package com.opticsvalley;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpticsValleyIRC implements ModInitializer {
	public static final String MOD_ID = "opticsvalleyirc";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	private static IRCClient ircClient;

	@Override
	public void onInitialize() {
		LOGGER.info("OpticsValley IRC initializing...");
	}

	public static IRCClient getIRCClient() {
		return ircClient;
	}

	public static void initializeClient(String username) {
		if (username == null) {
			// Disconnect case
			if (ircClient != null) {
				ircClient.disconnect();
				ircClient = null;
			}
		} else {
			// Connect case - always create a new connection
		if (ircClient != null) {
			ircClient.disconnect();
		}
		ircClient = new IRCClient(username);
		ircClient.connect();
		}
	}
}