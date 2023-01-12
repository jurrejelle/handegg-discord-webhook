package com.handegg;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class HandeggDiscordWebhookPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(HandeggDiscordWebhookPlugin.class);
		RuneLite.main(args);
	}
}