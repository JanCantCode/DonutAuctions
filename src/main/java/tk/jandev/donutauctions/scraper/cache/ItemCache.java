package tk.jandev.donutauctions.scraper.cache;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import tk.jandev.donutauctions.DonutAuctions;
import tk.jandev.donutauctions.scraper.ratelimit.RateLimiter;
import tk.jandev.donutauctions.scraper.scraper.AuctionScraper;
import tk.jandev.donutauctions.util.FormattingUtil;
import tk.jandev.donutauctions.util.ItemUtil;

import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ItemCache {
    private static ItemCache instance;
    private AuctionScraper scraper;
    private final RateLimiter rateLimiter = new RateLimiter(220, 60); // Slightly below donut-imposed rate limits in order to account for imprecision

    private final Map<DonutItem, CacheResult> priceCache = new ConcurrentHashMap<>();
    private final Set<DonutItem> currentlyRequesting = new ConcurrentSkipListSet<>(Comparator.comparing(DonutItem::id)); // comparison order is irrelevant for us, we just need thread safety!

    private final ExecutorService threadPool = Executors.newFixedThreadPool(25); // 25 threads to query items *should* be enough!

    public CacheResult getPrice(ItemStack itemStack) {
        if (this.scraper == null) return CacheResult.NO_API_KEY; // make it clear to the client that they need to set their API-key
        if (ItemUtil.isShulkerBox(itemStack.getItem())) return handleShulkerBox(itemStack);
        DonutItem key = DonutItem.ofItemStack(itemStack);

        if (!priceCache.containsKey(key)) {
            queryAndCacheAsync(key);

            return CacheResult.LOADING;
        }

        CacheResult result = priceCache.get(key);
        if (result.shouldBeRenewed(System.currentTimeMillis())) queryAndCacheAsync(key);

        return result;
    }

    private void queryAndCacheAsync(DonutItem key) {
        if (currentlyRequesting.contains(key)) return;
        currentlyRequesting.add(key);

        threadPool.submit(() -> {
            try {
                System.out.println("trying to aquire api limit ");
                rateLimiter.acquire(); // in case we have currently maxed out our requests, wait until we have not maxed our requests!
                System.out.println("succesfully aquired for " + key.id);

                Long foundPrice = this.scraper.findCheapestMatchingPrice(key.id, key.enchants, Map.of(), false);

                CacheResult result;
                if (foundPrice == null) {
                    result = CacheResult.NO_RESULTS;
                } else {
                    result = CacheResult.data(foundPrice);
                }

                this.priceCache.put(key, result);
            } catch (IOException | InterruptedException e) {
                System.out.println("threw exception");
            }
            currentlyRequesting.remove(key);
        });
    }

    private CacheResult handleShulkerBox(ItemStack shulker) {
        List<ItemStack> stacks = getShulkerBoxContents(shulker);
        if (stacks == null) return new CacheResult(true, 0, System.currentTimeMillis());

        long sum = 0;
        for (ItemStack stack : stacks) {
            CacheResult subResult = getPrice(stack);

            if (subResult.hasData) {
                sum += subResult.priceData * stack.getCount();
            }
        }

        return CacheResult.data(sum);
    }

    public List<ItemStack> getShulkerBoxContents(ItemStack shulkerStack) {
        NbtCompound nbt = shulkerStack.getNbt();
        if (nbt == null || !nbt.contains("BlockEntityTag")) {
            return null;
        }
        List<ItemStack> stacks = new ArrayList<>();

        NbtCompound blockEntityTag = nbt.getCompound("BlockEntityTag");
        NbtList itemsList = blockEntityTag.getList("Items", NbtElement.COMPOUND_TYPE);

        for (int i = 0; i < itemsList.size(); i++) {
            NbtCompound itemTag = itemsList.getCompound(i);
            int slot = itemTag.getByte("Slot") & 255;
            stacks.add(slot, ItemStack.fromNbt(itemTag));
        }

        return stacks;
    }

    public void supplyAPIKey(String key) {
        this.scraper = new AuctionScraper(key);
    }

    static {
        instance = new ItemCache();
    }

    public static ItemCache getInstance() {
        return instance;
    }

    private record DonutItem(String id, Map<String, Integer> enchants) {
        public static DonutItem ofItemStack(ItemStack stack) {
            String id = Registries.ITEM.getId(stack.getItem()).getPath();
            Map<String, Integer> enchants = new HashMap<>();


            Map<Enchantment, Integer> mcEnchants = EnchantmentHelper.get(stack);
            for (Map.Entry<Enchantment, Integer> mcEnchant : mcEnchants.entrySet()) {
                Enchantment enchantment = mcEnchant.getKey();

                String name = Registries.ENCHANTMENT.getId(enchantment).toString();
                System.out.println("found enchantment " + name);
                enchants.put(name, mcEnchant.getValue());
            }

            return new DonutItem(id, enchants);
        }

        @Override
        public String toString() {
            return id;
        }
    }

    public record CacheResult(boolean hasData, long priceData, long acquireTime) {
        public static CacheResult NO_RESULTS = new CacheResult(false, -2, 0);
        public static CacheResult NO_API_KEY = new CacheResult(false, -1, 0);
        public static CacheResult LOADING = new CacheResult(false, 0, 0);

        private final static int MONEY_COLOR = new Color(1, 252, 0, 255).getRGB();


        public static CacheResult data(long priceData) {
            return new CacheResult(true, priceData, System.currentTimeMillis());
        }

        public boolean shouldBeRenewed(long currentTime) {
            return (currentTime - acquireTime > DonutAuctions.getInstance().getCacheExpiration());
        }

        public Text getMessage(int count) {
            if (hasData) return Text.literal("§7Auction-Value: ")
                    .append(Text.literal("$" + FormattingUtil.formatCurrency(this.priceData * count)).styled(style -> style.withColor(MONEY_COLOR)));
            if (priceData == 0) return Text.literal("§7Loading..");
            if (priceData == -1) return Text.literal("§cType /api to set your API-Key");
            return Text.literal("§7No Auctions Found");
        }
    }
}
