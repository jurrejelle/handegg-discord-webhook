package com.handegg;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("handeggdiscordwebhook")
public interface HandeggDiscordWebhookConfig extends Config
{

	@ConfigSection(
		name = "Webhook Settings",
		description = "The config for webhook content notifications",
		position = 0,
		closedByDefault = true
	)
	String webhookConfig = "webhookConfig";

	@ConfigItem(
			keyName = "webhook",
			name = "Webhook URL(s)",
			description = "The Discord Webhook URL(s) to send messages to, separated by a newline.",
			section = webhookConfig,
			position = 0
	)
	String webhook();


	@ConfigSection(
			name = "Message Settings",
			description = "The config for when and how to send messages.",
			position = 1,
			closedByDefault = false
	)
	String messageConfig = "messageConfig";

	@ConfigItem(
			keyName = "includeScreenshot",
			name = "Include Screenshot",
			description = "Add a screenshot to the message.",
			section = messageConfig,
			position = 0
	)
	default boolean includeScreenshot() {
		return true;
	}
	@ConfigItem(
			keyName = "uploadOnReceive",
			name = "Send Message When Handegged",
			description = "Send a message when you get handegged by another player.",
			section = messageConfig,
			position = 1
	)
	default boolean uploadOnReceive() {
		return true;
	}

	@ConfigItem(
			keyName = "uploadOnThrow",
			name = "Send Message When You Handegg",
			description = "Send a message when you handegg another player.",
			section = messageConfig,
			position = 2
	)
	default boolean uploadOnThrow() {
		return false;
	}

}
