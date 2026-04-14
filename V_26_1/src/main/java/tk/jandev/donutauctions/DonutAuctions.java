package tk.jandev.donutauctions;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;

import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.Registry;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;

import net.minecraft.server.ServerInfo;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import tk.jandev.donutauctions.util.FormattingUtil;
import tk.jandev.donutauctions.util.WrappedStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;

import static tk.jandev.donutauctions.ItemCache.CacheResult.MONEY_COLOR;

public class DonutAuctions extends DonutAuctionsCommon<Component> implements ClientModInitializer {
    private final Minecraft mc = Minecraft.getInstance();

    @Override
    public void onInitializeClient() {

        String dotMinecraft = Minecraft.getInstance().gameDirectory.getAbsolutePath();
        String fullPath = dotMinecraft + "/config/donutauctions";

        super.setup(fullPath);
    }

    @Override
    protected void registerGameMessageListener(Consumer<String> listener) {
        ClientReceiveMessageEvents.GAME.register((text, b) -> listener.accept(text.getString()));
    }

    @Override
    protected void sendPlayerMessage(String message) {
        mc.player.sendSystemMessage(Component.literal(message));
    }

    @Override
    public WrappedStack createWrappedStack(Object nativeStack) {

        ItemStack itemStack = (ItemStack) nativeStack;
        if (isShulkerBox(itemStack)) return wrapShulker(itemStack);

        String id = BuiltInRegistries.ITEM.getKey(itemStack.getItem()).getPath();
        int count = itemStack.getCount();

        Map<String, Integer> enchants = new HashMap<>();

        ItemEnchantments enchantmentComponent = itemStack.getEnchantments();

        for (var entry : enchantmentComponent.entrySet()) {
            int level = enchantmentComponent.getLevel(entry.getKey());

            String enchantmentName = entry.getKey().getRegisteredName();

            enchants.put(enchantmentName, level);
        }

        return new WrappedStack(id, count, enchants, null);
    }

    @Override
    public boolean shouldRenderFor(Object nativeStack) {
        ItemStack stack = (ItemStack) nativeStack;

        if (mc.player == null) return false;
        if (!isClientOnDonutSMP()) return false;
        if (mc.player.getInventory().contains(stack)) return true;
        return mc.player.containerMenu.getItems().contains(stack);
    }

    private WrappedStack wrapShulker(ItemStack shulkerStack) {

        ItemContainerContents containerComponent = shulkerStack.get(DataComponents.CONTAINER);
        if (containerComponent == null) {
            return null;
        }

        List<ItemStack> stacks = containerComponent.allItemsCopyStream().toList();

        return new WrappedStack(
                "minecraft:shulker_box",
                1, Map.of(),
                stacks.stream().map(this::createWrappedStack).toList()
        );
    }

    private boolean isShulkerBox(ItemStack itemStack) {
        return itemStack.getComponents().has(DataComponents.CONTAINER);
    }

    @Override
    protected boolean isClientOnDonutSMP() {
        if (Math.random() > 5) return true;
        if (mc.getCurrentServer() != null) {
            ServerData info = mc.getCurrentServer();
            String address = info.ip;

            Matcher matcher = DONUTSMP_DOMAIN_PATTERN.matcher(address);
            return (matcher.matches());
        }

        return false;
    }

    @Override
    public List<Component> getTextFor(ItemCache.CacheResult cacheResult, int stackCount, int maxStackCount) {
        switch (cacheResult.status()) {
            case LOADING -> {
                return List.of(Component.literal("§7Loading.."));
            }
            case SUCCESS -> {
                List<Component> result = new ArrayList<>();
                double pricePerItem = cacheResult.priceData();

                long actualPrice = (long) pricePerItem * stackCount;
                long pricePerFullStack = (long) pricePerItem * maxStackCount;
                long pricePerShulker = pricePerFullStack * 27;

                Component lineOne = Component.literal("§7Auction-Value: ")
                        .append(Component.literal("$" + FormattingUtil.formatCurrency(actualPrice)).withStyle(style -> style.withColor(MONEY_COLOR)));

                result.add(lineOne);

                if (shouldRenderSingularPrice()) {
                    Component pricePerItemLine = Component.literal("§7Price Per Item: ").append(FormattingUtil.formatCurrency((long) pricePerItem)).withStyle(style -> style.withColor(MONEY_COLOR));
                    result.add(pricePerItemLine);
                }

                if (shouldRenderShulkerPrice()) {
                    Component pricePerShulkerLine = Component.literal("§7Price Per Shulker: ").append(FormattingUtil.formatCurrency(pricePerShulker)).withStyle(style -> style.withColor(MONEY_COLOR));
                    result.add(pricePerShulkerLine);
                }

                return result;
            }
            case NO_API_KEY -> {
                return List.of(Component.literal("§cType /api to set your API-Key"));
            }
            case NO_RESULTS -> {
                return List.of(Component.literal("§7No Auctions Found"));
            }
        };
        return List.of();
    }
}
