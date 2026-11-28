package jp.luckylobby;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import net.minecraft.world.dimension.DimensionTypes;
import xyz.nucleoid.fantasy.Fantasy;
import xyz.nucleoid.fantasy.RuntimeWorldHandle;

/** ロビー: 円形の広場。操作はすべてブロック(ボタン/床)で行い、インベントリは使わない。 */
public final class Lobby {
    public static final Identifier ID = new Identifier("luckylobby", "lobby");
    public static ServerWorld world;
    private static RuntimeWorldHandle handle;
    public static final BlockPos SPAWN = new BlockPos(0, 65, 0);

    public static void init(MinecraftServer server) {
        handle = Fantasy.get(server).getOrOpenPersistentWorld(ID,
                WorldFactory.voidConfig(server, DimensionTypes.OVERWORLD, 6000, false));
        world = handle.asWorld();
        for (int cx = -2; cx <= 2; cx++) for (int cz = -2; cz <= 2; cz++) world.setChunkForced(cx, cz, true);
        build(server);
    }

    /** ロビーを(再)構築する。/lucky rebuild でも呼べる。 */
    public static void build(MinecraftServer server) {
        // 以前の表示を片付ける
        server.getCommandManager().executeWithPrefix(
                server.getCommandSource().withWorld(world).withSilent(), "kill @e[tag=ll_label]");
        // 以前の構造物を撤去(床の上の空間)
        BlockState air = Blocks.AIR.getDefaultState();
        for (int x = -22; x <= 22; x++) for (int z = -22; z <= 22; z++)
            for (int y = 65; y <= 78; y++) world.setBlockState(new BlockPos(x, y, z), air, Block.NOTIFY_LISTENERS);
        // 床
        BlockState q1 = Blocks.QUARTZ_BLOCK.getDefaultState(), q2 = Blocks.SMOOTH_QUARTZ.getDefaultState();
        for (int x = -22; x <= 22; x++) for (int z = -22; z <= 22; z++) {
            double r = Math.sqrt(x * x + z * z);
            if (r > 20.5) continue;
            BlockState top = r >= 19.0 ? Blocks.GOLD_BLOCK.getDefaultState() : (((x + z) & 1) == 0 ? q1 : q2);
            world.setBlockState(new BlockPos(x, 64, z), top, Block.NOTIFY_LISTENERS);
            world.setBlockState(new BlockPos(x, 63, z), Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
        }
        Ui.clear();
        Console.build(world);
        Casino.build(world);
    }

    // ---------- プレイヤー処理 ----------

    public static void resetPlayer(ServerPlayerEntity p) {
        p.getInventory().clear();
        p.clearStatusEffects();
        p.setHealth(p.getMaxHealth());
        p.getHungerManager().setFoodLevel(20);
        p.getHungerManager().setSaturationLevel(5f);
        p.setExperienceLevel(0);
        p.setExperiencePoints(0);
        p.setFireTicks(0);
        p.fallDistance = 0;
    }

    public static void sendToLobby(ServerPlayerEntity p) {
        resetPlayer(p);
        p.changeGameMode(GameMode.ADVENTURE);
        p.teleport(world, 0.5, 65, 0.5, 180f, 0f);
        p.setSpawnPoint(world.getRegistryKey(), SPAWN, 180f, true, false);
        p.getServer().getCommandManager().executeWithPrefix(
                p.getServer().getCommandSource().withSilent(), "team leave " + p.getName().getString());
    }

    public static void onJoin(ServerPlayerEntity p) {
        if (GameManager.isActiveParticipant(p.getUuid())) return;
        sendToLobby(p);
        p.sendMessage(Util.plain("§6[Lucky Lobby] §fようこそ！ ボタンをクリックして操作します。待ち時間はカジノで遊べます。"));
        Console.refresh(p.getServer());
    }

    public static void tick(MinecraftServer server) {
        if (world == null) return;
        for (ServerPlayerEntity p : world.getPlayers()) {
            if (p.getY() < 40) p.teleport(world, 0.5, 65, 0.5, 180f, 0f);
            if (server.getTicks() % 100 == 0) p.getHungerManager().setFoodLevel(20);
        }
        Console.tick(server);
        Casino.tick(server);
    }

    public static boolean isLobbyPlayer(ServerPlayerEntity p) { return p.getServerWorld() == world; }
}
