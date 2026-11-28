package jp.luckylobby;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;
import net.minecraft.world.dimension.DimensionTypes;

import java.util.*;

/**
 * ラッキー一本道レース(空中コース版)。
 * - 空中に浮く幅1マスの一本道をプレイヤー(レーン)ごとに敷く。レーン間のすき間は1マス。
 * - 道の上にラッキーブロックの壁(高さ2・幅3)が立ちはだかる。壊さないと進めない。
 * - 道の真上(高さ3マス目)にバリアブロックを敷き、ブロックを積んで壁を越えられないようにする。
 * - ラッキーブロックで道の地面が壊れても、すぐ張り直す。
 * - 死んだら最初から: スタート地点へ戻り、そのレーンの壁(ラッキーブロック)も全て新品に置き直す。
 * - 自分のレーンの中心から2マス近く離れる(隣レーンへ移る等)と即死、落下も即死。
 * - 曲がり角・制限時間・ランダムイベント・順位は添付の「一本道レース v3.2」を参考。
 */
public class RaceGame extends Game {
    static final int Y = 100;          // 道の高さ
    static final int MARGIN = 10;      // 角から最初/最後の壁までの距離
    static final int START_CELL = 3;   // スタート位置(後ろに3マスの足場)
    static final int GOAL_CELLS = 3;   // 終端の金ブロック数
    static final int[][] DIRS = {{0, 1}, {-1, 0}, {0, -1}, {1, 0}}; // 南,西,北,東 (右折 = +1)
    static final BlockState[] WOOL = {
            Blocks.RED_WOOL.getDefaultState(), Blocks.BLUE_WOOL.getDefaultState(), Blocks.LIME_WOOL.getDefaultState(),
            Blocks.YELLOW_WOOL.getDefaultState(), Blocks.LIGHT_BLUE_WOOL.getDefaultState(), Blocks.ORANGE_WOOL.getDefaultState(),
            Blocks.MAGENTA_WOOL.getDefaultState(), Blocks.WHITE_WOOL.getDefaultState()};

    record Seg(int dir, int len) {}

    private final Random rnd = new Random();
    private final List<Seg> segs = new ArrayList<>();
    private int n;
    private int[] off;
    private final List<List<int[]>> cells = new ArrayList<>();
    private final List<Map<Long, Integer>> cellIndex = new ArrayList<>();
    private final List<List<Integer>> wallIdx = new ArrayList<>();
    private final Map<UUID, Integer> lane = new HashMap<>(), cur = new HashMap<>(), best = new HashMap<>();
    /** 各レーンの壁の位置 {中心x, 中心z, 法線x, 法線z} */
    private final List<List<int[]>> wallList = new ArrayList<>();
    /** 壁を構成するブロック全部(BlockPos.asLong) */
    private final Set<Long> wallBlocks = new HashSet<>();
    /** 全レーンの道のマス(x,z) */
    private final Set<Long> pathKeys = new HashSet<>();
    private final List<UUID> finishOrder = new ArrayList<>();
    private int finishTimer = -1, timeLeft, eventIn = 500;

    public RaceGame(MinecraftServer server, Settings s, List<ServerPlayerEntity> ps, Map<UUID, Integer> teams) {
        super(server, s, ps, teams);
    }

    @Override public String name() { return "ラッキー一本道レース"; }

    @Override protected ServerWorld createWorld() {
        return open(WorldFactory.voidConfig(server, DimensionTypes.OVERWORLD, 6000, false)
                .setGameRule(GameRules.KEEP_INVENTORY, true));
    }

    static int[] normal(int dir) { return new int[]{DIRS[dir][1], -DIRS[dir][0]}; } // 進行方向の左

    private static long key(int x, int z) { return ((long) x << 32) ^ (z & 0xffffffffL); }

    @Override protected void build() {
        n = players.size();
        off = new int[n];
        for (int i = 0; i < n; i++) off[i] = i * 2 - (n - 1);   // レーン間隔=2 (すき間1マス)

        // コース設計: カーブなしなら直線、ありなら 左右交互 の階段状(自己交差しない)
        int total = s.raceLength;
        if (!s.raceCurves) segs.add(new Seg(0, total));
        else {
            int dir = 0, remaining = total; boolean right = rnd.nextBoolean();
            while (remaining > 0) {
                int len = Math.min(remaining, 30 + rnd.nextInt(31));
                if (remaining - len > 0 && remaining - len < 30) len = remaining;
                segs.add(new Seg(dir, len));
                remaining -= len;
                dir = right ? (dir + 1) % 4 : (dir + 3) % 4;
                right = !right;
            }
        }
        int K = segs.size();
        int[][] cs = new int[K][2], ce = new int[K][2];
        int x = 0, z = 0;
        for (int k = 0; k < K; k++) {
            cs[k] = new int[]{x, z};
            x += DIRS[segs.get(k).dir()][0] * segs.get(k).len();
            z += DIRS[segs.get(k).dir()][1] * segs.get(k).len();
            ce[k] = new int[]{x, z};
        }
        int f = Block.NOTIFY_LISTENERS;
        BlockState lucky = Util.luckyBlock().getDefaultState();

        for (int i = 0; i < n; i++) {
            int o = off[i];
            List<int[]> pts = new ArrayList<>();
            int[] n0 = normal(segs.get(0).dir());
            pts.add(new int[]{cs[0][0] + o * n0[0], cs[0][1] + o * n0[1]});
            for (int k = 0; k < K - 1; k++) {
                int[] a = normal(segs.get(k).dir()), b = normal(segs.get(k + 1).dir());
                pts.add(new int[]{ce[k][0] + o * (a[0] + b[0]), ce[k][1] + o * (a[1] + b[1])});
            }
            int[] nl = normal(segs.get(K - 1).dir());
            pts.add(new int[]{ce[K - 1][0] + o * nl[0], ce[K - 1][1] + o * nl[1]});

            List<int[]> c = new ArrayList<>();
            c.add(pts.get(0));
            for (int p = 1; p < pts.size(); p++) {
                int[] a = c.get(c.size() - 1), b = pts.get(p);
                int dx = Integer.signum(b[0] - a[0]), dz = Integer.signum(b[1] - a[1]);
                int cx = a[0], cz = a[1];
                while (cx != b[0] || cz != b[1]) { cx += dx; cz += dz; c.add(new int[]{cx, cz}); }
            }
            cells.add(c);
            Map<Long, Integer> idx = new HashMap<>();
            for (int j = 0; j < c.size(); j++) {
                int[] q = c.get(j);
                idx.putIfAbsent(key(q[0], q[1]), j);
                pathKeys.add(key(q[0], q[1]));
                world.setBlockState(new BlockPos(q[0], Y, q[1]), j >= c.size() - GOAL_CELLS
                        ? Blocks.GOLD_BLOCK.getDefaultState() : WOOL[i % 8], f);
            }
            cellIndex.add(idx);
            wallIdx.add(new ArrayList<>());
            wallList.add(new ArrayList<>());
        }

        // ラッキーブロックの壁: 道+左右のすき間 の 3幅 x 高さ2
        for (int k = 0; k < K; k++) {
            Seg sg = segs.get(k);
            int[] d = DIRS[sg.dir()], nn = normal(sg.dir());
            for (int u = MARGIN; u <= sg.len() - MARGIN; u += s.raceSpacing) {
                int wx = cs[k][0] + d[0] * u, wz = cs[k][1] + d[1] * u;
                for (int i = 0; i < n; i++) {
                    int px = wx + off[i] * nn[0], pz = wz + off[i] * nn[1];
                    wallList.get(i).add(new int[]{px, pz, nn[0], nn[1]});
                    placeWall(i, wallList.get(i).get(wallList.get(i).size() - 1), lucky);
                    Integer wi = cellIndex.get(i).get(key(px, pz));
                    if (wi != null) wallIdx.get(i).add(wi);
                }
            }
        }
        buildBarrier();
    }

    private void placeWall(int lane, int[] w, BlockState lucky) {
        for (int t = -1; t <= 1; t++) for (int h = 1; h <= 2; h++) {
            BlockPos bp = new BlockPos(w[0] + t * w[2], Y + h, w[1] + t * w[3]);
            wallBlocks.add(bp.asLong());
            world.setBlockState(bp, lucky, Block.NOTIFY_LISTENERS);
        }
    }

    /** 道の真上(高さ3マス目 = Y+3)にバリアを敷く。ブロックを積んで壁を越えるのを防ぐ。 */
    private void buildBarrier() {
        BlockState bar = Blocks.BARRIER.getDefaultState();
        Set<Long> done = new HashSet<>();
        for (List<int[]> c : cells) for (int[] q : c)
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                BlockPos bp = new BlockPos(q[0] + dx, Y + 3, q[1] + dz);
                if (done.add(bp.asLong())) world.setBlockState(bp, bar, Block.NOTIFY_LISTENERS);
            }
    }

    private Block groundOf(int lane, int idx) {
        return idx >= cells.get(lane).size() - GOAL_CELLS ? Blocks.GOLD_BLOCK : WOOL[lane % 8].getBlock();
    }

    /** 道の地面(と真上のバリア)が壊れていたら、すぐ置き直す。 */
    private void ensureCells(int lane, int from, int to) {
        List<int[]> c = cells.get(lane);
        for (int j = Math.max(0, from); j <= Math.min(c.size() - 1, to); j++) {
            int[] q = c.get(j);
            BlockPos g = new BlockPos(q[0], Y, q[1]);
            if (!world.isChunkLoaded(g)) continue;
            Block exp = groundOf(lane, j);
            if (!world.getBlockState(g).isOf(exp)) world.setBlockState(g, exp.getDefaultState(), Block.NOTIFY_LISTENERS);
            BlockPos b = g.up(3);
            if (!world.getBlockState(b).isOf(Blocks.BARRIER)) world.setBlockState(b, Blocks.BARRIER.getDefaultState(), Block.NOTIFY_LISTENERS);
        }
    }

    /** 死亡時: そのレーンを完全に最初の状態へ戻す(壁は全て新品、地面も張り直し、中のゴミも撤去)。 */
    private void resetLane(int lane) {
        List<int[]> c = cells.get(lane);
        BlockState lucky = Util.luckyBlock().getDefaultState(), air = Blocks.AIR.getDefaultState();
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (int j = 0; j < c.size(); j++) {
            int[] q = c.get(j);
            minX = Math.min(minX, q[0]); maxX = Math.max(maxX, q[0]);
            minZ = Math.min(minZ, q[1]); maxZ = Math.max(maxZ, q[1]);
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                int x = q[0] + dx, z = q[1] + dz;
                boolean isPath = pathKeys.contains(key(x, z));
                for (int h = 1; h <= 2; h++) {
                    BlockPos bp = new BlockPos(x, Y + h, z);
                    if (!wallBlocks.contains(bp.asLong()) && !world.getBlockState(bp).isAir()) world.setBlockState(bp, air, Block.NOTIFY_LISTENERS);
                }
                if (!isPath) { // すき間に積まれたブロックを撤去
                    BlockPos g = new BlockPos(x, Y, z);
                    if (!world.getBlockState(g).isAir()) world.setBlockState(g, air, Block.NOTIFY_LISTENERS);
                }
            }
        }
        ensureCells(lane, 0, c.size() - 1);
        for (int[] w : wallList.get(lane)) placeWall(lane, w, lucky);
        Box box = new Box(minX - 3, Y - 6, minZ - 3, maxX + 4, Y + 12, maxZ + 4);
        for (Entity e : world.getEntitiesByClass(Entity.class, box, en -> !(en instanceof PlayerEntity))) e.discard();
    }

    @Override public boolean onDeath(ServerPlayerEntity v, DamageSource src) {
        PInfo i = players.get(v.getUuid());
        if (i == null || state != State.RUNNING || finishOrder.contains(i.id)) return true;
        int ln = lane.get(i.id);
        resetLane(ln);
        cur.put(i.id, START_CELL);
        best.put(i.id, START_CELL);
        Util.actionBar(v, "§c最初からやり直し！ 壁も元に戻りました");
        return true;
    }

    @Override protected void assignSpawns() {
        int[] d0 = DIRS[segs.get(0).dir()];
        float yaw = (float) Math.toDegrees(Math.atan2(-d0[0], d0[1]));
        int idx = 0;
        for (PInfo i : players.values()) {
            lane.put(i.id, idx);
            int[] c = cells.get(idx).get(START_CELL);
            i.spawn = new Vec3d(c[0] + 0.5, Y + 1, c[1] + 0.5);
            i.yaw = yaw;
            cur.put(i.id, START_CELL); best.put(i.id, START_CELL);
            idx++;
        }
    }

    @Override protected void giveKit(ServerPlayerEntity p, PInfo i) {
        ItemStack pick = new ItemStack(Items.IRON_PICKAXE);
        pick.addEnchantment(Enchantments.EFFICIENCY, 3);
        pick.getOrCreateNbt().putBoolean("Unbreakable", true);
        p.getInventory().offerOrDrop(pick);
        p.getInventory().offerOrDrop(new ItemStack(Items.BREAD, 8));
    }

    @Override protected void onStart() {
        timeLeft = s.raceMinutes * 1200;
        eventIn = 500;
        Util.broadcast(onlineAll(), "§e[レース] §f空中の一本道を進め！ ラッキーブロックの壁を壊して先へ。§c隣のレーンへ移る/落ちると即死！死んだら最初から！");
    }

    private double progress(UUID id) { return best.get(id) / (double) (cells.get(lane.get(id)).size() - 1); }

    private List<PInfo> ranking() {
        List<PInfo> l = new ArrayList<>(players.values());
        l.sort((a, b) -> {
            int fa = finishOrder.indexOf(a.id), fb = finishOrder.indexOf(b.id);
            if (fa >= 0 || fb >= 0) return fa < 0 ? 1 : fb < 0 ? -1 : Integer.compare(fa, fb);
            return Double.compare(progress(b.id), progress(a.id));
        });
        return l;
    }

    @Override protected void onTick() {
        timeLeft--;
        int online = 0;
        for (PInfo i : players.values()) {
            ServerPlayerEntity p = online(i);
            if (p == null) continue;
            online++;
            if (!p.isAlive() || finishOrder.contains(i.id)) continue;
            int ln = lane.get(i.id);
            List<int[]> c = cells.get(ln);
            int from = Math.max(0, cur.get(i.id) - 30), to = Math.min(c.size() - 1, cur.get(i.id) + 14);
            int bj = from; double bd = Double.MAX_VALUE;
            for (int j = from; j <= to; j++) {
                double dx = p.getX() - (c.get(j)[0] + 0.5), dz = p.getZ() - (c.get(j)[1] + 0.5);
                double d = dx * dx + dz * dz;
                if (d < bd) { bd = d; bj = j; }
            }
            // 即死: レーンから2マス近く離れた / 落下
            if (Math.sqrt(bd) >= 1.75 || p.getY() < Y - 6) {
                p.kill();
                continue;
            }
            cur.put(i.id, bj);
            if (bj > best.get(i.id)) best.put(i.id, bj);
            int b = best.get(i.id);
            if (b >= c.size() - GOAL_CELLS) {
                finishOrder.add(i.id);
                int rank = finishOrder.size();
                Util.broadcast(allInWorlds(), "§a🏁 " + rank + "位: §f" + i.name + (i.team >= 0 ? " §7(" + Util.teamLabel(i.team) + "§7)" : "") + " §7[" + Util.mmss(ticks - 200) + "]");
                Util.title(p, "§6§l" + rank + "位！", "§fゴール！", 5, 60, 10);
                if (rank == 1) finishTimer = 300;
            }
        }
        // 地面(とバリア)の即時修復: プレイヤー付近は毎tick、全体は1秒ごと
        for (PInfo i : players.values()) {
            ServerPlayerEntity p = online(i);
            if (p == null || !p.isAlive()) continue;
            int cj = cur.get(i.id);
            ensureCells(lane.get(i.id), cj - 4, cj + 24);
        }
        if (ticks % 20 == 0) for (int l = 0; l < n; l++) ensureCells(l, 0, cells.get(l).size() - 1);
        if (ticks % 10 == 0) {
            List<PInfo> rk = ranking();
            for (int r = 0; r < rk.size(); r++) {
                ServerPlayerEntity p = online(rk.get(r));
                if (p != null) Util.actionBar(p, "§e順位 §f" + (r + 1) + "§7/" + rk.size() + "  §e進行 §f" + (int) (progress(rk.get(r).id) * 100) + "%  §b残り §f" + Util.mmss(timeLeft));
            }
        }
        if (s.raceEvents && --eventIn <= 0) { eventIn = 400 + rnd.nextInt(300); event(); }
        if ((finishTimer > 0 && --finishTimer == 0) || timeLeft <= 0
                || (!finishOrder.isEmpty() && finishOrder.size() >= online)) finishRace();
        if (online == 0) end("§c参加者がいなくなりました");
    }

    // ---- ランダムイベント(添付パック準拠) ----
    private void eff(PInfo i, StatusEffect e, int sec, int amp) {
        ServerPlayerEntity p = online(i);
        if (p != null) p.addStatusEffect(new StatusEffectInstance(e, sec * 20, amp, false, true));
    }

    private void event() {
        List<PInfo> rk = ranking();
        if (rk.size() < 2) return;
        PInfo first = rk.get(0), last = rk.get(rk.size() - 1);
        List<ServerPlayerEntity> all = allInWorlds();
        switch (rnd.nextInt(5)) {
            case 0 -> { Util.broadcast(all, "§c§l⚡ 【重力妨害】§f1位が重くなる！"); eff(first, StatusEffects.SLOWNESS, 6, 2); eff(first, StatusEffects.BLINDNESS, 3, 0); }
            case 1 -> {
                Util.broadcast(all, "§a§l⚡ 【ラッキーワープ】§f最下位が24m先へワープ＆スピードUP！");
                ServerPlayerEntity p = online(last);
                if (p != null) {
                    List<int[]> c = cells.get(lane.get(last.id));
                    int t = Math.min(c.size() - 1 - GOAL_CELLS - 1, best.get(last.id) + 24);
                    if (t > best.get(last.id)) {
                        p.teleport(world, c.get(t)[0] + 0.5, Y + 1, c.get(t)[1] + 0.5, last.yaw, 0f);
                        cur.put(last.id, t); best.put(last.id, t);
                    }
                    eff(last, StatusEffects.SPEED, 8, 2);
                }
            }
            case 2 -> { Util.broadcast(all, "§c§l⚡ 【ジャンプ封印】§f1位は5秒間ジャンプできない！"); eff(first, StatusEffects.JUMP_BOOST, 5, -56); eff(first, StatusEffects.SLOWNESS, 5, 0); }
            case 3 -> {
                Util.broadcast(all, "§e§l⚡ 【逆転チャンス】§f下位にスピード&ジャンプ、1位は発光！");
                for (int r = rk.size() / 2; r < rk.size(); r++) { eff(rk.get(r), StatusEffects.SPEED, 8, 2); eff(rk.get(r), StatusEffects.JUMP_BOOST, 8, 1); }
                eff(first, StatusEffects.GLOWING, 8, 0);
            }
            default -> { Util.broadcast(all, "§5§l⚡ 【めまい】§f1位がふらふらになる！"); eff(first, StatusEffects.NAUSEA, 10, 0); eff(first, StatusEffects.SLOWNESS, 6, 1); }
        }
    }

    private void finishRace() {
        if (state == State.ENDING) return;
        StringBuilder sb = new StringBuilder("§6§l結果 ");
        int r = 1;
        for (PInfo i : ranking()) {
            sb.append("§e").append(r++).append("位§f").append(i.name).append(finishOrder.contains(i.id) ? "" : "§7(" + (int) (progress(i.id) * 100) + "%)").append(" ");
        }
        end(sb.toString());
    }
}
