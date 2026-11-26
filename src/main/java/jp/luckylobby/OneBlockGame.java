package jp.luckylobby;

import net.minecraft.entity.boss.dragon.EnderDragonFight;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionTypes;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ラッキーワンブロック: 1ブロックから始めてエンドラ討伐。
 * ラッキーブロックから出るエンドポータルフレーム/エンダーアイで通常どおりポータルを作り、
 * サーバー(ワールド)共通の本物のエンドへ入る。討伐したらゲーム終了。
 * 再戦は「新しいワールドを作り直す」前提のため、ドラゴン戦のリセットは行わない。
 */
public class OneBlockGame extends Game {
    private static final BlockPos CENTER = new BlockPos(0, 64, 0);

    public OneBlockGame(MinecraftServer server, Settings s, List<ServerPlayerEntity> ps, Map<UUID, Integer> teams) {
        super(server, s, ps, teams);
    }

    @Override public String name() { return "ラッキーワンブロック"; }

    /** このワールドで既にドラゴンを倒していれば開始できない(エラー文を返す)。 */
    public static String precheck(MinecraftServer server) {
        ServerWorld end = server.getWorld(World.END);
        if (end == null) return "§cこのサーバーはエンドが無効です";
        EnderDragonFight f = end.getEnderDragonFight();
        if (f != null && f.hasPreviouslyKilled())
            return "§cこのワールドのエンダードラゴンは討伐済みです。新しいワールドで遊んでください";
        return null;
    }

    @Override protected ServerWorld createWorld() {
        return open(WorldFactory.voidConfig(server, DimensionTypes.OVERWORLD, 1000, true));
    }

    @Override protected void build() {
        world.setBlockState(CENTER, Util.luckyBlock().getDefaultState(), 3);
    }

    @Override protected void assignSpawns() {
        for (PInfo i : players.values()) { i.spawn = new Vec3d(0.5, 65, 0.5); i.yaw = 0; }
    }

    @Override protected void giveKit(ServerPlayerEntity p, PInfo i) {
        p.getInventory().offerOrDrop(new ItemStack(Items.BREAD, 5));
    }

    @Override protected void onStart() {
        Util.broadcast(onlineAll(), "§6[ワンブロック] §f足元のラッキーブロックを壊して進め！");
        Util.broadcast(onlineAll(), "§dゴール: §fラッキーブロックから §eエンドポータルフレーム×12 §fと §eエンダーアイ §fを集めてポータルを作り、§5エンダードラゴン §fを討伐");
    }

    @Override protected void onTick() {
        // ラッキーブロックの再生成 + 乗っている人の押し上げ
        if (world.getBlockState(CENTER).getBlock() != Util.luckyBlock()) {
            world.setBlockState(CENTER, Util.luckyBlock().getDefaultState(), 3);
            for (ServerPlayerEntity p : world.getPlayers())
                if (Math.abs(p.getX() - 0.5) < 1.0 && Math.abs(p.getZ() - 0.5) < 1.0 && p.getY() < 65 && p.getY() > 63)
                    p.requestTeleport(p.getX(), 65.0, p.getZ());
        }
        // ドラゴン討伐判定(本物のエンドの共通ドラゴン戦データ)
        ServerWorld end = server.getWorld(World.END);
        EnderDragonFight fight = end == null ? null : end.getEnderDragonFight();
        if (fight != null && fight.hasPreviouslyKilled()) {
            for (ServerPlayerEntity p : onlineAll()) Util.title(p, "§6§lCLEAR!", "§fエンダードラゴン討伐！", 5, 80, 20);
            end("§6§lエンダードラゴン討伐！ §fクリアタイム " + Util.mmss(ticks - 200)
                    + "\n§7このワールドは終了して、新しいワールドで遊び直してください");
        }
        if (onlineAll().isEmpty()) end("§c参加者がいなくなりました");
    }

    /** ゲーム終了時は、本物のエンドにいる人も含めてロビーへ戻す。 */
    @Override protected List<ServerPlayerEntity> allInWorlds() {
        List<ServerPlayerEntity> l = super.allInWorlds();
        ServerWorld end = server.getWorld(World.END);
        if (end != null) for (ServerPlayerEntity p : end.getPlayers()) if (players.containsKey(p.getUuid()) && !l.contains(p)) l.add(p);
        return l;
    }
}
