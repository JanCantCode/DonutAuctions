package tk.jandev.donutauctions.mixin;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tk.jandev.donutauctions.scraper.cache.ItemCache;
import tk.jandev.donutauctions.scraper.cache.ItemCache.CacheResult;
import tk.jandev.donutauctions.util.FormattingUtil;

@Mixin(HandledScreen.class)
public class HandledScreenMixin {

    private static final Pattern WORTH_PATTERN = Pattern.compile("Worth: \\$(\\d+(?:\\.\\d+)?)([kKmMbB]?)");

    @Inject(method = "drawForeground(Lnet/minecraft/client/gui/DrawContext;II)V", at = @At("TAIL"))
    private void drawOverlay(DrawContext context, int mouseX, int mouseY, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!(client.currentScreen instanceof GenericContainerScreen || client.currentScreen instanceof ShulkerBoxScreen)) return;
        if (client.player == null) return;

        HandledScreen<?> screen = (HandledScreen<?>) client.currentScreen;
        ClientPlayerEntity player = client.player;
        Item.TooltipContext tooltipContext = Item.TooltipContext.DEFAULT;

        double totalSellWorth = 0;
        double totalAuctionWorth = 0;
        Map<String, double[]> itemMap = new HashMap<>(); // name -> [count, sellWorth, auctionWorth]

        int containerSlots = (screen instanceof ShulkerBoxScreen) ? 27 : screen.getScreenHandler().slots.size() - 36;

        for (int i = 0; i < containerSlots; i++) {
            ItemStack stack = screen.getScreenHandler().slots.get(i).getStack();
            if (stack.isEmpty()) continue;

            int count = stack.getCount();
            String itemName = stack.getName().getString();

            double itemSellWorth = 0;
            double itemAuctionWorth = 0;

            // Parse lore for /sell price
            List<Text> tooltips = stack.getTooltip(tooltipContext, player, TooltipType.BASIC);
            for (Text line : tooltips) {
                String raw = line.getString();
                Matcher m = WORTH_PATTERN.matcher(raw);
                if (m.find()) {
                    itemSellWorth += parseWorth(m.group(1), m.group(2)) * count;
                }
            }

            // Get auction price from ItemCache
            CacheResult result = ItemCache.getInstance().getPrice(stack);
            if (result.hasData()) {
                itemAuctionWorth = result.priceData() * count;
            }

            totalSellWorth += itemSellWorth;
            totalAuctionWorth += itemAuctionWorth;

            itemMap.merge(itemName, new double[]{count, itemSellWorth, itemAuctionWorth}, (oldVal, newVal) -> {
                oldVal[0] += newVal[0];
                oldVal[1] += newVal[1];
                oldVal[2] += newVal[2];
                return oldVal;
            });
        }

        // Sort by max(sell, auction)
        List<Map.Entry<String, double[]>> sortedItems = new ArrayList<>(itemMap.entrySet());
        sortedItems.sort((a, b) -> Double.compare(
                Math.max(b.getValue()[1], b.getValue()[2]),
                Math.max(a.getValue()[1], a.getValue()[2])
        ));

        int lineHeight = 10;
        int totalX = -150;
        int totalY = -80;

        context.drawText(client.textRenderer, Text.literal("Total Worth:"), totalX, totalY, 0xFFFFFF, true);
        totalY += lineHeight;

        context.drawText(
                client.textRenderer,
                Text.literal("/Sell: $" + FormattingUtil.formatCurrency((long) totalSellWorth)),
                totalX,
                totalY,
                0xFFFFFF,
                true
        );
        totalY += lineHeight;

        context.drawText(
                client.textRenderer,
                Text.literal("Auction: $" + FormattingUtil.formatCurrency((long) totalAuctionWorth)),
                totalX,
                totalY,
                0xFFFFFF,
                true
        );
        totalY += lineHeight;

        // Breakdown
        int breakdownX = totalX;
        int breakdownY = totalY + 10;

        context.drawText(client.textRenderer, Text.literal("Item Breakdown:"), breakdownX, breakdownY, 0xFFFFFF, true);
        int currentY = breakdownY + lineHeight;

        int maxItems = Math.min(sortedItems.size(), 6);
        for (int i = 0; i < maxItems; i++) {
            String name = sortedItems.get(i).getKey();
            double[] data = sortedItems.get(i).getValue();
            int count = (int) data[0];
            double sell = data[1];
            double auction = data[2];

            double max = Math.max(sell, auction);
            String displayName = name.length() > 15 ? name.substring(0, 12) + "..." : name;
            String itemLine = count > 1
                    ? displayName + " x" + count + " [$" + FormattingUtil.formatCurrency((long) max) + "]"
                    : displayName + " [$" + FormattingUtil.formatCurrency((long) max) + "]";

            context.drawText(client.textRenderer, Text.literal(itemLine), breakdownX, currentY, 0xFFFFFF, true);
            currentY += lineHeight;
        }

        if (sortedItems.size() > maxItems) {
            context.drawText(
                    client.textRenderer,
                    Text.literal("... and " + (sortedItems.size() - maxItems) + " more items"),
                    breakdownX,
                    currentY,
                    0xAAAAAA,
                    true
            );
        }
    }

    private double parseWorth(String number, String suffix) {
        double base = Double.parseDouble(number);
        return switch (suffix.toLowerCase()) {
            case "k" -> base * 1_000;
            case "m" -> base * 1_000_000;
            case "b" -> base * 1_000_000_000;
            default -> base;
        };
    }
}
