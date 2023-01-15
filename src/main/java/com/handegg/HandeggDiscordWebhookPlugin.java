package com.handegg;

import com.google.common.base.Strings;
import com.google.inject.Provides;

import javax.imageio.ImageIO;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.DrawManager;
import okhttp3.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static net.runelite.http.api.RuneLiteAPI.GSON;

@Slf4j
@PluginDescriptor(
	name = "Handegg Discord Webhook"
)
public class HandeggDiscordWebhookPlugin extends Plugin
{
	public static int HOLY_HANDEGG_THROWN_ANIMATIONID = 7994;
	public static int PEACEFUL_HANDEGG_THROWN_ANIMATIONID = 7995;
	public static int CHAOTIC_HANDEGG_THROWN_ANIMATIONID = 7996;
	public static int HANDEGG_RECEIVED_ANIMATIONID = 782;

	public static HandeggState state = HandeggState.IDLE;

	private static int currentGameTick = 0;
	private static int lastStateChanged;
	private static String lastThrower = "";
	private static String lastReciever = "";
	private static String currentPlayer;

	@Inject
	private Client client;

	@Inject
	private HandeggDiscordWebhookConfig config;

	@Inject
	private DrawManager drawManager;

	@Inject
	private OkHttpClient okHttpClient;

	@Provides
	HandeggDiscordWebhookConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(HandeggDiscordWebhookConfig.class);
	}

	private void updateState(HandeggState newState){
		state = newState;
		lastStateChanged = currentGameTick;
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged animationChanged)
	{
		int changedAnimationID = animationChanged.getActor().getAnimation();

		if(changedAnimationID == -1) return;

		if(currentPlayer == null) {
			Player currentPlayerFromClient = client.getLocalPlayer();
			if (currentPlayerFromClient == null) return;
			String currentPlayerName = currentPlayerFromClient.getName();
			if (currentPlayerName == null) return;
			currentPlayer = currentPlayerName;
		}

		String actorName = animationChanged.getActor().getName();

		if(	changedAnimationID == HOLY_HANDEGG_THROWN_ANIMATIONID     ||
				changedAnimationID == PEACEFUL_HANDEGG_THROWN_ANIMATIONID ||
				changedAnimationID == CHAOTIC_HANDEGG_THROWN_ANIMATIONID){

			// If I'm the one who threw the handegg
			if(currentPlayer.equals(actorName)){
				updateState(HandeggState.THROWN);
			} else {
				lastThrower = actorName;
			}

		} else if(changedAnimationID == HANDEGG_RECEIVED_ANIMATIONID){

			// If I'm the one who just got handegged
			if(currentPlayer.equals(actorName)){
				updateState(HandeggState.RECEIVED);
			} else {
				lastReciever = actorName;
			}

		}
	}

	// Using gametick as a mini internal clock
	@Subscribe
	public void onGameTick(GameTick event)
	{
		currentGameTick++;

		// If 3 game ticks have passed since the last state change
		if(state != HandeggState.IDLE && lastStateChanged + 3 < currentGameTick){
			if(state == HandeggState.RECEIVED){

				String message = currentPlayer +" just got handegged by "+lastThrower+"!";
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "You just got handegged by "+lastThrower+"!", null);
				log.info(message);
				if(config.uploadOnReceive()){
					sendWebhook(message);
				}
			}
			if(state == HandeggState.THROWN){
				String message = currentPlayer +" just handegged "+lastReciever+"!";
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "You just handegged "+lastReciever+"!", null);
				log.info(message);
				if(config.uploadOnThrow()) {
					sendWebhook(message);
				}
			}
			updateState(HandeggState.IDLE);
		}
	}

	private void sendWebhook(String message){
		DiscordWebhookBody discordWebhookBody = new DiscordWebhookBody();
		discordWebhookBody.setContent(message);
		sendWebhook(discordWebhookBody, config.includeScreenshot());

	}

	private void sendWebhook(DiscordWebhookBody discordWebhookBody, boolean sendScreenshot)
	{
		String configUrl = config.webhook();
		if (Strings.isNullOrEmpty(configUrl)) { return; }

		List<String> webhookUrls = Arrays.asList(configUrl.split("\n"))
				.stream()
				.filter(u -> u.length() > 0)
				.map(u -> u.trim())
				.collect(Collectors.toList());

		for (String webhookUrl : webhookUrls)
		{
			HttpUrl url = HttpUrl.parse(webhookUrl);
			MultipartBody.Builder requestBodyBuilder = new MultipartBody.Builder()
					.setType(MultipartBody.FORM)
					.addFormDataPart("payload_json", GSON.toJson(discordWebhookBody));

			if (sendScreenshot)
			{
				sendWebhookWithScreenshot(url, requestBodyBuilder);
			}
			else
			{
				buildRequestAndSend(url, requestBodyBuilder);
			}
		}
	}

	private void sendWebhookWithScreenshot(HttpUrl url, MultipartBody.Builder requestBodyBuilder)
	{
		drawManager.requestNextFrameListener(image ->
		{
			BufferedImage bufferedImage = (BufferedImage) image;
			byte[] imageBytes;
			try
			{
				imageBytes = convertImageToByteArray(bufferedImage);
			}
			catch (IOException e)
			{
				log.warn("Error converting image to byte array", e);
				return;
			}

			requestBodyBuilder.addFormDataPart("file", "image.png",
					RequestBody.create(MediaType.parse("image/png"), imageBytes));
			buildRequestAndSend(url, requestBodyBuilder);
		});
	}

	private void buildRequestAndSend(HttpUrl url, MultipartBody.Builder requestBodyBuilder)
	{
		RequestBody requestBody = requestBodyBuilder.build();
		Request request = new Request.Builder()
				.url(url)
				.post(requestBody)
				.build();
		sendRequest(request);
	}

	private void sendRequest(Request request)
	{
		okHttpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("Error submitting webhook", e);
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException
			{
				response.close();
			}
		});
	}

	private static byte[] convertImageToByteArray(BufferedImage bufferedImage) throws IOException
	{
		ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
		ImageIO.write(bufferedImage, "png", byteArrayOutputStream);
		return byteArrayOutputStream.toByteArray();
	}


}

enum HandeggState{
	IDLE,
	THROWN,
	RECEIVED
}