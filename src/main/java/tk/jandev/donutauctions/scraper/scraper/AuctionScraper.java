package tk.jandev.donutauctions.scraper.scraper;

import com.google.gson.*;
import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

public class AuctionScraper {
    private static final String API_URL = "https://api.donutsmp.net/v1/auction/list/";

    private final String apiKey;
    private final HttpClient client;

    public AuctionScraper(String apiKey) {
        this.apiKey = apiKey;

        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    public Double findCheapestMatchingPrice(String searchQuery, Map<String, Integer> targetEnchantments, Map<String, String> targetTrim, boolean useTrims) throws IOException, InterruptedException {
        int page = 1;

        while (true) {
            JsonObject response = fetchAuctionPage(page, searchQuery);
            JsonArray results = response.getAsJsonArray("result");
            if (results == null) break; // i have no idea how results.isEmpty() doesnt throw an error, but just freezes the thrad - this check is important
            if (results.isEmpty()) {
                break;
            }

            for (JsonElement element : results) {
                if (element.isJsonNull()) {
                    continue;
                }
                JsonObject auction = element.getAsJsonObject();
                JsonObject item = auction.getAsJsonObject("item");

                if (!matchesEnchantments(item.getAsJsonObject("enchants"), targetEnchantments)) {
                    continue;
                }

                if (useTrims && !matchesTrim(item.getAsJsonObject("enchants"), targetTrim)) { // we are currently ignoring trims, but if we ever stop doing so..
                    continue;
                }
                long price = auction.get("price").getAsLong();
                int count = item.get("count").getAsInt();
                return (double) price / count;
            }

            page++;
        }
        return null;
    }

    private JsonObject fetchAuctionPage(int page, String searchQuery) throws IOException, InterruptedException {
        URI url = URI.create(API_URL + page);

        ItemFilter filter = new ItemFilter(searchQuery, SortMode.LOWEST_PRICE);

        final HttpRequest request = HttpRequest
                .newBuilder(url)
                .method("GET", HttpRequest.BodyPublishers.ofString(filter.toString()))
                .header("Authorization", "Bearer " + this.apiKey)
                .header("Accept", "application/json")
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        String content = response.body();

        JsonElement jsonElement = JsonParser.parseString(content);
        return jsonElement.getAsJsonObject();
    }

    private boolean matchesEnchantments(JsonObject enchants, Map<String, Integer> targetEnchantments) {
        if (enchants == null || !enchants.has("enchantments")) return false;

        JsonObject enchantments = enchants.getAsJsonObject("enchantments");
        if (enchantments == null) {
            return targetEnchantments.isEmpty();
        }
        if (!enchantments.has("levels") || enchantments.get("levels") instanceof JsonNull) {
            return targetEnchantments.isEmpty();
        }
        JsonObject levels = enchantments.getAsJsonObject("levels");

        for (Map.Entry<String, Integer> entry : targetEnchantments.entrySet()) {
            if (!levels.has(entry.getKey())) {
                return false;
            }
            if (levels.get(entry.getKey()).getAsInt() != entry.getValue()) {
                return false;
            }
        }

        // Prüfe, ob zusätzliche Verzauberungen existieren
        Set<String> itemEnchantKeys = levels.keySet();
        if (itemEnchantKeys.size() != targetEnchantments.size()) {
            return false;
        }

        return true;
    }

    private boolean matchesTrim(JsonObject enchants, Map<String, String> targetTrim) {
        if (enchants == null || !enchants.has("trim")) return false;

        JsonObject trim = enchants.getAsJsonObject("trim");

        String targetMaterial = targetTrim.get("material");
        String targetPattern = targetTrim.get("pattern");

        String itemMaterial = trim.has("material") ? trim.get("material").getAsString() : null;
        String itemPattern = trim.has("pattern") ? trim.get("pattern").getAsString() : null;

        return (targetMaterial.equals(itemMaterial)) && (targetPattern.equals(itemPattern));
    }

    public record ItemFilter(String itemName, SortMode sortMode) {
        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("{");
            builder.append('"').append("search").append('"').append(":");
            builder.append('"');
            builder.append(itemName()).append('"').append(",");
            builder.append('"').append("sort").append('"').append(":");
            builder.append('"').append(sortMode()).append('"');
            builder.append("}");

            return builder.toString();
        }
    }

    public enum SortMode {
        LOWEST_PRICE,
        HIGHEST_PRICE,
        RECENTLY_LISTED,
        LAST_LISTED;

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }
}
