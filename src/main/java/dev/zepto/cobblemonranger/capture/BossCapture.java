package dev.zepto.cobblemonranger.capture;

import com.cobblemon.mod.common.pokemon.Pokemon;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.zepto.cobblemonranger.CobblemonRanger;
import dev.zepto.cobblemonranger.config.RangerConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Boss captures: Alphas and legendaries fight back in stages. When the friendship gauge passes a stage's mark the
 * Pokémon shields itself for a moment, speeds up and switches to its next attack set. Alphas have 2 stages and
 * legendaries 3.
 *
 * <p>Which attacks a legendary uses in each stage comes from the data files in {@code data/<namespace>/ranger_bosses},
 * so datapacks can add sets for more species. Each file has a {@code species} list and a {@code stages} list, one list
 * of attack names per stage. A boss without a set uses its type's attacks, made stronger every stage.
 */
public final class BossCapture {
    public static final int ALPHA_STAGES = 2;
    public static final int LEGENDARY_STAGES = 3;

    public enum Kind { NONE, ALPHA, LEGENDARY }

    /** What kind of boss a Pokémon is, and the attack names for each stage (empty lists mean "its type's attacks"). */
    public record Boss(Kind kind, List<List<String>> stages) {
        public static final Boss NONE = new Boss(Kind.NONE, List.of(List.of()));

        public int stageCount() {
            return stages.size();
        }

        public boolean isBoss() {
            return kind != Kind.NONE;
        }
    }

    /** Species id ({@code cobblemon:groudon}) to its attack names per stage, loaded from the data files. */
    private static Map<String, List<List<String>>> sets = Map.of();

    private BossCapture() {
    }

    public static Boss of(Pokemon pokemon) {
        if (!RangerConfig.BOSS_CAPTURES.get()) {
            return Boss.NONE;
        }
        boolean legendary = isLegendary(pokemon);
        if (!legendary && !pokemon.isAlpha()) {
            return Boss.NONE;
        }
        int count = legendary ? LEGENDARY_STAGES : ALPHA_STAGES;
        List<List<String>> stages = new ArrayList<>();
        List<List<String>> set = legendary ? sets.get(pokemon.getSpecies().getResourceIdentifier().toString()) : null;
        for (int i = 0; i < count; i++) {
            stages.add(set != null && i < set.size() ? set.get(i) : List.of());
        }
        return new Boss(legendary ? Kind.LEGENDARY : Kind.ALPHA, stages);
    }

    /** Cobblemon's legendary and mythical labels, which datapack species can use too. */
    public static boolean isLegendary(Pokemon pokemon) {
        return pokemon.isLegendary() || pokemon.isMythical();
    }

    /**
     * The loot a battle would give for knocking out this Alpha: Cobblemon's Alpha reward table for its level. The
     * tiers start at levels 31, 51 and 66, as in Cobblemon.
     */
    public static List<ItemStack> alphaLoot(ServerPlayer player, Pokemon pokemon) {
        int level = pokemon.getLevel();
        int tier = level >= 66 ? 4 : level >= 51 ? 3 : level >= 31 ? 2 : 1;
        ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE,
                ResourceLocation.fromNamespaceAndPath("cobblemon", "alpha/alpha_rewards_tier" + tier));
        LootTable table = player.server.reloadableRegistries().getLootTable(key);
        if (table == LootTable.EMPTY) {
            return List.of();
        }
        LootParams params = new LootParams.Builder(player.serverLevel())
                .withParameter(LootContextParams.ORIGIN, player.position())
                .withParameter(LootContextParams.THIS_ENTITY, player)
                .withLuck(player.getLuck())
                .create(LootContextParamSets.CHEST);
        return table.getRandomItems(params);
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new Loader());
    }

    private static final class Loader extends SimpleJsonResourceReloadListener {
        Loader() {
            super(new Gson(), "ranger_bosses");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
            Map<String, List<List<String>>> loaded = new HashMap<>();
            files.forEach((file, json) -> {
                try {
                    JsonObject object = json.getAsJsonObject();
                    List<List<String>> stages = new ArrayList<>();
                    for (JsonElement stage : object.getAsJsonArray("stages")) {
                        List<String> attacks = new ArrayList<>();
                        for (JsonElement attack : stage.getAsJsonArray()) {
                            attacks.add(attack.getAsString().toLowerCase(Locale.ROOT));
                        }
                        stages.add(List.copyOf(attacks));
                    }
                    JsonArray species = object.getAsJsonArray("species");
                    for (JsonElement id : species) {
                        String name = id.getAsString().toLowerCase(Locale.ROOT);
                        loaded.put(name.contains(":") ? name : "cobblemon:" + name, List.copyOf(stages));
                    }
                } catch (RuntimeException e) {
                    CobblemonRanger.LOGGER.warn("Skipping boss capture file {}: {}", file, e.toString());
                }
            });
            sets = Map.copyOf(loaded);
            CobblemonRanger.LOGGER.info("Loaded boss capture attacks for {} species", sets.size());
        }
    }
}
