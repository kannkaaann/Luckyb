package jp.luckylobby;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** カジノ用コイン(ワールドに永続保存)。 */
public class Coins extends PersistentState {
    public static final long START = 1000;
    private final Map<UUID, Long> m = new HashMap<>();

    public static Coins of(MinecraftServer s) {
        return s.getOverworld().getPersistentStateManager().getOrCreate(Coins::load, Coins::new, "luckylobby_coins");
    }

    private static Coins load(NbtCompound nbt) {
        Coins c = new Coins();
        NbtCompound o = nbt.getCompound("c");
        for (String k : o.getKeys()) c.m.put(UUID.fromString(k), o.getLong(k));
        return c;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtCompound o = new NbtCompound();
        m.forEach((k, v) -> o.putLong(k.toString(), v));
        nbt.put("c", o);
        return nbt;
    }

    public static long get(MinecraftServer s, UUID id) { return of(s).m.getOrDefault(id, START); }

    public static void set(MinecraftServer s, UUID id, long v) {
        Coins c = of(s); c.m.put(id, Math.max(0, v)); c.markDirty();
    }

    public static void add(MinecraftServer s, UUID id, long n) { set(s, id, get(s, id) + n); }

    public static boolean take(MinecraftServer s, UUID id, long n) {
        long cur = get(s, id);
        if (cur < n) return false;
        set(s, id, cur - n);
        return true;
    }
}
