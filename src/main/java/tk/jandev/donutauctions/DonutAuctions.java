package tk.jandev.donutauctions;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import tk.jandev.donutauctions.config.SingleValueFile;
import tk.jandev.donutauctions.scraper.cache.ItemCache;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DonutAuctions implements ClientModInitializer {
    private static DonutAuctions donutAuctions;
    private SingleValueFile apiKeyConfig;
    private final Pattern API_TOKEN_PATTERN = Pattern.compile("Your API Token is: (\\w{32})");
    private MinecraftClient mc;

    private ItemCache itemCache;
    @Override
    public void onInitializeClient() {
        donutAuctions = this;
        mc = MinecraftClient.getInstance();
        this.itemCache = ItemCache.getInstance();

        String dotMinecraft = MinecraftClient.getInstance().runDirectory.getAbsolutePath();
        this.apiKeyConfig = new SingleValueFile(dotMinecraft + File.pathSeparator + "config" + File.pathSeparator + "donutauctions");

        tryReadAPI();

        ClientReceiveMessageEvents.GAME.register((text, b) -> {
            Matcher matcher = API_TOKEN_PATTERN.matcher(text.getString());

            if (matcher.find()) {
                String apiKey = matcher.group(1);
                MinecraftClient.getInstance().player.sendMessage(Text.literal("§2Successfully Obtained API-Key for DonutAuctions!"), true);
                this.itemCache.supplyAPIKey(apiKey);

                try {
                    apiKeyConfig.setAndWrite(apiKey);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    public long getCacheExpiration() { // refresh item prices after 60 seconds!
        return TimeUnit.SECONDS.toMicros(60);
    }

    private void tryReadAPI() {
        try {
            if (this.apiKeyConfig.read()) {
                String potentialAPIKey = this.apiKeyConfig.get();
                if (potentialAPIKey.length() != 32) {
                    System.out.println("API-Key in config file was corrupted, resetting..");
                    this.apiKeyConfig.writeEmpty();
                    return;
                }

                this.itemCache.supplyAPIKey(potentialAPIKey);
            }
        } catch (IOException e) {

        }
    }

    public boolean shouldShowPrice(ItemStack stack) {
        if (mc.player == null) return false;
        if (mc.player.getInventory().contains(stack)) return true;
        ScreenHandler handler = mc.player.currentScreenHandler;
        if (handler == null) return false;
        if (handler.getStacks() == null) return false;
        return (handler.getStacks().contains(stack));
    }

    public static DonutAuctions getInstance() {
        return donutAuctions;
    }
}
