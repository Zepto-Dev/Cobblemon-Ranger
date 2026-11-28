package dev.zepto.cobblemonranger.rescue;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.pokemon.Species;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.zepto.cobblemonranger.CobblemonRanger;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Which species get trapped in each scenario, from {@code data/<namespace>/ranger_rescues/*.json}:
 * <pre>{"scenario": "rubble", "pools": [{"biomes": ["#minecraft:is_nether"], "species": ["numel"]},
 *                                      {"species": ["geodude", "roggenrola"]}]}</pre>
 * A pool with {@code biomes} (biome ids or {@code #tags}) is used where it matches; pools without biomes are the
 * fallback. Several files may add pools to the same scenario, so a datapack can extend the lists.
 */
public final class RescueSpecies {
    private record Pool(List<String> biomes, List<String> species) {
        boolean matches(Holder<Biome> biome) {
            for (String entry : biomes) {
                if (entry.startsWith("#")) {
                    if (biome.is(TagKey.create(Registries.BIOME, ResourceLocation.parse(entry.substring(1))))) {
                        return true;
                    }
                } else if (biome.is(ResourceLocation.parse(entry))) {
                    return true;
                }
            }
            return false;
        }
    }

    private static Map<RescueScenario, List<Pool>> pools = Map.of();

    private RescueSpecies() {
    }

    /** A random species name for this scenario and biome, or null if the data has none that Cobblemon knows. */
    public static String pick(RescueScenario scenario, Holder<Biome> biome, RandomSource random) {
        List<String> matching = new ArrayList<>();
        List<String> fallback = new ArrayList<>();
        for (Pool pool : pools.getOrDefault(scenario, List.of())) {
            if (pool.biomes.isEmpty()) {
                fallback.addAll(pool.species);
            } else if (pool.matches(biome)) {
                matching.addAll(pool.species);
            }
        }
        List<String> names = matching.isEmpty() ? fallback : matching;
        names.removeIf(name -> {
            Species species = PokemonSpecies.getByName(name);
            return species == null || !species.getImplemented();
        });
        return names.isEmpty() ? null : names.get(random.nextInt(names.size()));
    }

    public static boolean hasAny(RescueScenario scenario) {
        return !pools.getOrDefault(scenario, List.of()).isEmpty();
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new Loader());
    }

    private static final class Loader extends SimpleJsonResourceReloadListener {
        Loader() {
            super(new Gson(), "ranger_rescues");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
            Map<RescueScenario, List<Pool>> loaded = new EnumMap<>(RescueScenario.class);
            files.forEach((file, json) -> {
                try {
                    JsonObject object = json.getAsJsonObject();
                    RescueScenario scenario = RescueScenario.byId(object.get("scenario").getAsString());
                    if (scenario == null) {
                        throw new IllegalArgumentException("unknown scenario " + object.get("scenario"));
                    }
                    for (JsonElement poolJson : object.getAsJsonArray("pools")) {
                        JsonObject pool = poolJson.getAsJsonObject();
                        List<String> biomes = new ArrayList<>();
                        if (pool.has("biomes")) {
                            pool.getAsJsonArray("biomes").forEach(b -> biomes.add(b.getAsString()));
                        }
                        List<String> species = new ArrayList<>();
                        pool.getAsJsonArray("species").forEach(s -> species.add(s.getAsString().toLowerCase(Locale.ROOT)));
                        loaded.computeIfAbsent(scenario, s -> new ArrayList<>())
                                .add(new Pool(List.copyOf(biomes), List.copyOf(species)));
                    }
                } catch (RuntimeException e) {
                    CobblemonRanger.LOGGER.warn("Skipping rescue file {}: {}", file, e.toString());
                }
            });
            pools = loaded;
            CobblemonRanger.LOGGER.info("Loaded rescue species for {} scenarios", loaded.size());
        }
    }
}
