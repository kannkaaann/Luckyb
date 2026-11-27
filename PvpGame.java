package jp.luckylobby;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.dimension.DimensionTypes;

import java.util.*;

/** ラッキーブロックPVP: 個人戦/チーム戦。初期配布ラッキーブロック数は設定可能。 */
public class PvpGame extends Game {
    private static final int R = 30;
    private int timeLeft;

    public PvpGame(MinecraftServer server, Settings s, List<ServerPlayerEntity> ps, Map<UUID, Integer> teams) {
        super(server, s, ps, teams);
    }

    @Override public String name() { return "ラッキーブロックPVP"; }

    @Override protected ServerWorld createWorld() {
        return open(WorldFactory.voidConfig(server, DimensionTypes.OVERWORLD, 6000, false));
    }

    @Override protected void build() {
        BlockState lucky = Util.luckyBlock().getDefaultState();
        BlockState a = Blocks.STONE_BRICKS.getDefaultState(), b = Blocks.POLISHED_ANDESITE.getDefaultState();
        int f = Block.NOTIFY_LISTENERS;
        for (int x = -R - 1; x <= R + 1; x++) for (int z = -R - 1; z <= R + 1; z++) {
            boolean edge = Math.abs(x) == R + 1 || Math.abs(z) == R + 1;
            if (edge) { for (int y = 63; y <= 90; y++) world.setBlockState(new BlockPos(x, y, z), Blocks.BEDROCK.getDefaultState(), f); }
            else world.setBlockState(new BlockPos(x, 63, z), ((x + z) & 1) == 0 ? a : b, f);
        }
        Random rnd = new Random();
        // 柱(足場・遮蔽物)
        for (int n = 0; n < 14; n++) {
            int px = rnd.nextInt(2 * R - 8) - R + 4, pz = rnd.nextInt(2 * R - 8) - R + 4;
            int h = 3 + rnd.nextInt(3);
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int y = 64; y < 64 + h; y++)
                world.setBlockState(new BlockPos(px + dx, y, pz + dz), a, f);
            world.setBlockState(new BlockPos(px, 64 + h, pz), lucky, f);
        }
        // 床に散らばるラッキーブロック
        for (int n = 0; n < 90; n++) {
            int x = rnd.nextInt(2 * R - 1) - R + 1, z = rnd.nextInt(2 * R - 1) - R + 1;
            if (Math.sqrt(x * x + z * z) > 20) continue; // スポーン帯(半径24付近)を避ける
            BlockPos p = new BlockPos(x, 64, z);
            if (world.getBlockState(p).isAir()) world.setBlockState(p, lucky, f);
        }
    }

    @Override protected void assignSpawns() {
        // グループ = チーム or 個人
        LinkedHashMap<Integer, List<PInfo>> groups = new LinkedHashMap<>();
        int solo = 1000;
        for (PInfo i : players.values()) groups.computeIfAbsent(i.team >= 0 ? i.team : solo++, k -> new ArrayList<>()).add(i);
        int g = 0, G = groups.size();
        for (List<PInfo> list : groups.values()) {
            double ang = 2 * Math.PI * g / G;
            double cx = Math.cos(ang) * 24, cz = Math.sin(ang) * 24;
            for (int k = 0; k < list.size(); k++) {
                double off = (k - (list.size() - 1) / 2.0) * 2.0;
                double x = cx - Math.sin(ang) * off, z = cz + Math.cos(ang) * off;
                PInfo i = list.get(k);
                i.spawn = new Vec3d(x + 0.5, 64, z + 0.5);
                i.yaw = (float) Math.toDegrees(Math.atan2(x, -z)); // 中心(原点)方向
            }
            g++;
        }
    }

    @Override protected void giveKit(ServerPlayerEntity p, PInfo i) {
        Util.giveLucky(p, s.startLucky);
        p.getInventory().offerOrDrop(new ItemStack(Items.COOKED_BEEF, 16));
    }

    @Override protected void onStart() {
        timeLeft = s.pvpMinutes * 1200;
        Util.broadcast(onlineAll(), "§c[PVP] §fラッキーブロックを設置・破壊して装備を整え、最後まで生き残れ！");
    }

    @Override protected void onTick() {
        if (ticks % 20 == 0) {
            long alive = players.values().stream().filter(i -> i.alive).count();
            String t = s.pvpMinutes > 0 ? "残り §f" + Util.mmss(timeLeft) + " §7| " : "";
            for (ServerPlayerEntity p : allInWorlds()) Util.actionBar(p, "§e" + t + "§c生存 §f" + alive);
        }
        if (s.pvpMinutes > 0 && --timeLeft <= 0) timeUp();
    }

    private String groupName(PInfo i) { return i.team >= 0 ? Util.teamLabel(i.team) : "§f" + i.name; }

    @Override public boolean onDeath(ServerPlayerEntity v, DamageSource src) {
        PInfo vi = players.get(v.getUuid());
        if (vi == null) return true;
        if (state != State.RUNNING || !vi.alive) { v.setHealth(v.getMaxHealth()); return false; }
        vi.alive = false;
        String by = "";
        Entity a = src.getAttacker();
        if (a instanceof ServerPlayerEntity k && !k.getUuid().equals(v.getUuid()) && players.containsKey(k.getUuid())) {
            players.get(k.getUuid()).kills++;
            by = " §7(by §f" + k.getName().getString() + "§7)";
        }
        v.getInventory().dropAll();
        v.setHealth(v.getMaxHealth());
        v.setFireTicks(0);
        v.clearStatusEffects();
        v.changeGameMode(GameMode.SPECTATOR);
        Util.broadcast(allInWorlds(), "§c☠ §f" + v.getName().getString() + " §cが脱落" + by);
        checkWin();
        return false;
    }

    @Override public void onDisconnect(ServerPlayerEntity p) {
        PInfo i = players.get(p.getUuid());
        if (i != null && i.alive && state == State.RUNNING) { i.alive = false; checkWin(); }
    }

    private void checkWin() {
        Set<Integer> keys = new HashSet<>();
        PInfo last = null;
        int idx = 0;
        for (PInfo i : players.values()) { idx++; if (i.alive) { keys.add(i.team >= 0 ? i.team : 1000 + idx); last = i; } }
        if (keys.size() > 1) return;
        if (last == null) end("§7全員脱落… 引き分け");
        else {
            for (ServerPlayerEntity p : allInWorlds()) Util.title(p, "§6§l勝者", groupName(last), 5, 80, 20);
            end("§6§l優勝: " + groupName(last));
        }
    }

    private void timeUp() {
        int max = -1; List<PInfo> top = new ArrayList<>();
        for (PInfo i : players.values()) if (i.alive) {
            if (i.kills > max) { max = i.kills; top.clear(); top.add(i); }
            else if (i.kills == max) top.add(i);
        }
        if (top.size() == 1) end("§e時間切れ！ 最多キル(§f" + max + "§e)で §6優勝: " + groupName(top.get(0)));
        else end("§e時間切れ！ 引き分け");
    }
}
