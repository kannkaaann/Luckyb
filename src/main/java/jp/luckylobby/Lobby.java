package jp.luckylobby;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import net.minecraft.world.dimension.DimensionTypes;
import xyz.nucleoid.fantasy.Fantasy;
import xyz.nucleoid.fantasy.RuntimeWorldHandle;

public final class Lobby {
    public static final Identifier ID = new Identifier("luckylobby", "lobby");
    public static ServerWorld world;
    private static RuntimeWorldHandle handle;

    public static final BlockPos SPAWN = new BlockPos(0, 65, 0);
    public static final BlockPos SLOT = new BlockPos(12, 65, 0);
    public static final BlockPos BLACKJACK = new BlockPos(0, 65, 12);
    public static final BlockPos POKER = new BlockPos(-12, 65, 0);
    public static final BlockPos ADMIN = new BlockPos(0, 65, -12);

    public static void init(MinecraftServer server) {
        handle = Fantasy.get(server).getOrOpenPersistentWorld(ID,
                WorldFactory.voidConfig(server, DimensionTypes.OVERWORLD, 6000, false));
        world = handle.asWorld();
        for (int cx = -2; cx <= 2; cx++) for (int cz = -2; cz <= 2; cz++) world.setChunkForced(cx, cz, true);
        build(server);
    }

    private static void build(MinecraftServer server) {
        BlockState q1 = Blocks.QUARTZ_BLOCK.getDefaultState(), q2 = Blocks.SMOOTH_QUARTZ.getDefaultState();
        for (int x = -22; x <= 22; x++) for (int z = -22; z <= 22; z++) {
            double r = Math.sqrt(x * x + z * z);
            if (r > 20.5) continue;
            BlockState top = r >= 19.0 ? Blocks.GOLD_BLOCK.getDefaultState() : (((x + z) & 1) == 0 ? q1 : q2);
            world.setBlockState(new BlockPos(x, 64, z), top, Block.NOTIFY_LISTENERS);
            world.setBlockState(new BlockPos(x, 63, z), Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
        }
        kiosk(SLOT, Blocks.GOLD_BLOCK, "§6§l★ スロットマシン ★");
        kiosk(BLACKJACK, Blocks.EMERALD_BLOCK, "§a§l♠ ブラックジャック ♠");
        kiosk(POKER, Blocks.LAPIS_BLOCK, "§9§l♥ ビデオポーカー ♥");
        kiosk(ADMIN, Blocks.BEACON, "§c§l管理コンソール §7(OP専用)");
        cmd(server, "kill @e[type=minecraft:armor_stand,tag=ll_label]");
        label(server, SLOT, "§6§l★ スロットマシン ★");
        label(server, BLACKJACK, "§a§l♠ ブラックジャック ♠");
        label(server, POKER, "§9§l♥ ビデオポーカー ♥");
        label(server, ADMIN, "§c§l管理コンソール §7(OP専用)");
    }

    private static void kiosk(BlockPos p, Block b, String ignored) {
        world.setBlockState(p, b.getDefaultState(), Block.NOTIFY_LISTENERS);
    }

    private static void label(MinecraftServer server, BlockPos p, String text) {
        String json = "{\"text\":\"" + text.replace("§", "\\u00a7") + "\"}";
        cmd(server, "summon minecraft:armor_stand " + (p.getX() + 0.5) + " " + (p.getY() + 1.1) + " " + (p.getZ() + 0.5)
                + " {Invisible:1b,Marker:1b,NoGravity:1b,CustomNameVisible:1b,Tags:[\"ll_label\"],CustomName:'" + json + "'}");
    }

    private static void cmd(MinecraftServer server, String c) {
        server.getCommandManager().executeWithPrefix(
                server.getCommandSource().withWorld(world).withSilent(), c);
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
        p.teleport(world, 0.5, 65, 0.5, 0f, 0f);
        p.setSpawnPoint(world.getRegistryKey(), SPAWN, 0f, true, false);
        giveKit(p);
        server(p).getCommandManager().executeWithPrefix(
                server(p).getCommandSource().withSilent(), "team leave " + p.getName().getString());
    }

    private static MinecraftServer server(ServerPlayerEntity p) { return p.getServer(); }

    private static void giveKit(ServerPlayerEntity p) {
        ItemStack admin = Util.item(Items.COMPASS, "§e§l管理メニュー §7(OP)", "§7右クリックでモード/チーム設定とスタート");
        admin.getOrCreateNbt().putString("ll_item", "admin");
        ItemStack casino = Util.item(Items.EMERALD, "§a§lカジノメニュー", "§7右クリックでスロット/ブラックジャック/ポーカー");
        casino.getOrCreateNbt().putString("ll_item", "casino");
        p.getInventory().setStack(0, admin);
        p.getInventory().setStack(1, casino);
    }

    public static void onJoin(ServerPlayerEntity p) {
        if (GameManager.isActiveParticipant(p.getUuid())) return;
        sendToLobby(p);
        p.sendMessage(Util.plain("§6[Lucky Lobby] §fようこそ！ 待ち時間はカジノで遊べます。 §7/lucky casino"));
    }

    public static void tick(MinecraftServer server) {
        if (world == null) return;
        for (ServerPlayerEntity p : world.getPlayers()) {
            if (p.getY() < 40) p.teleport(world, 0.5, 65, 0.5, 0f, 0f);
            if (server.getTicks() % 100 == 0) p.getHungerManager().setFoodLevel(20);
        }
    }

    public static boolean isLobbyPlayer(ServerPlayerEntity p) { return p.getServerWorld() == world; }
}
