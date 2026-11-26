package jp.luckylobby;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameRules;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.dimension.DimensionType;
import xyz.nucleoid.fantasy.RuntimeWorldConfig;
import xyz.nucleoid.fantasy.util.VoidChunkGenerator;

/** Fantasy用のワールド設定。Fantasyのバージョン差で壊れやすい箇所はここに集約。 */
public final class WorldFactory {
    public static RuntimeWorldConfig voidConfig(MinecraftServer server, RegistryKey<DimensionType> dim,
                                                long timeOfDay, boolean daylightCycle) {
        // ★ VoidChunkGenerator のコンストラクタがバージョンで違う場合はここを修正
        RegistryEntry<Biome> biome = server.getRegistryManager().get(RegistryKeys.BIOME).entryOf(BiomeKeys.PLAINS);
        return new RuntimeWorldConfig()
                .setDimensionType(dim)
                .setGenerator(new VoidChunkGenerator(biome))
                .setDifficulty(Difficulty.NORMAL)
                .setTimeOfDay(timeOfDay)
                .setGameRule(GameRules.DO_DAYLIGHT_CYCLE, daylightCycle)
                .setGameRule(GameRules.DO_WEATHER_CYCLE, false)
                .setGameRule(GameRules.DO_MOB_SPAWNING, false);
    }
}
