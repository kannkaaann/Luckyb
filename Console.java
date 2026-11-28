package jp.luckylobby;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.UUID;

/** ロビーの管理コンソール(ボタン)とチーム選択の床。設定はすべてロビー内で行う。 */
public final class Console {
    private static final Settings.Mode[] MODES = Settings.Mode.values();
    private static final int[] MODE_X = {-6, 0, 6};
    private static final int[] SET_X = {-10, -6, -2, 2, 6, 10};
    private static final int MODE_Z = -15, SET_Z = -12, ACT_Z = -9;

    private static final Ui.Label[] modeL = new Ui.Label[3];
    private static final Ui.Label[] setL = new Ui.Label[6];
    private static Ui.Label info, startL, stopL;

    // チーム床: [中心x, 中心z, 種別] 種別 0..3=チーム, -2=観戦, -1=おまかせ
    private static final int[][] PADS = {{-9, -3, 0}, {-3, -3, 1}, {3, -3, 2}, {9, -3, 3}, {-6, 3, -2}, {6, 3, -1}};
    private static final BlockState[] PAD_BLOCK = {
            Blocks.RED_CONCRETE.getDefaultState(), Blocks.BLUE_CONCRETE.getDefaultState(),
            Blocks.LIME_CONCRETE.getDefaultState(), Blocks.YELLOW_CONCRETE.getDefaultState()};
    private static final Ui.Label[] padL = new Ui.Label[6];

    public static void build(ServerWorld w) {
        for (int i = 0; i < 3; i++) {
            final Settings.Mode m = MODES[i];
            Block ped = i == 0 ? Blocks.LIME_CONCRETE : i == 1 ? Blocks.RED_CONCRETE : Blocks.YELLOW_CONCRETE;
            modeL[i] = Ui.button(w, MODE_X[i], MODE_Z, ped, "", p -> admin(p, () -> GameManager.settings.mode = m));
        }
        for (int i = 0; i < 6; i++) {
            final int idx = i;
            setL[i] = Ui.button(w, SET_X[i], SET_Z, Blocks.LIGHT_BLUE_CONCRETE, "", p -> admin(p, () -> settingClick(idx)));
        }
        startL = Ui.button(w, -3, ACT_Z, Blocks.EMERALD_BLOCK, "§a§l▶ スタート", p -> admin(p, () -> {
            String err = GameManager.start(p.getServer());
            if (err != null) p.sendMessage(Util.plain(err));
        }));
        stopL = Ui.button(w, 3, ACT_Z, Blocks.REDSTONE_BLOCK, "§c§l■ ゲーム強制終了", p -> admin(p, () -> {
            if (GameManager.current == null) p.sendMessage(Util.plain("§7進行中のゲームはありません"));
            else GameManager.stop();
        }));
        info = Ui.label(w, 0.5, 70.3, MODE_Z + 0.5, 1.6f);
        Ui.label(w, 0.5, 68.6, SET_Z + 0.5 - 2.5, 1f).text("§7▼ モードを選び、設定を変えて、スタート ▼ §8(管理者のみ)");

        // チーム選択の床
        for (int i = 0; i < PADS.length; i++) {
            int cx = PADS[i][0], cz = PADS[i][1], kind = PADS[i][2];
            BlockState b = kind >= 0 ? PAD_BLOCK[kind] : kind == -2 ? Blocks.GRAY_CONCRETE.getDefaultState() : Blocks.WHITE_CONCRETE.getDefaultState();
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
                w.setBlockState(new BlockPos(cx + dx, 64, cz + dz), b, Block.NOTIFY_LISTENERS);
            padL[i] = Ui.label(w, cx + 0.5, 66.2, cz + 0.5, 1.1f);
        }
        Ui.label(w, 0.5, 66.4, -6.0 + 0.5, 1f).text("§7▼ 乗った色のチームで参加(PVP/レース) ▼");
        refresh(w.getServer());
    }

    private static void admin(ServerPlayerEntity p, Runnable r) {
        if (!Util.isAdmin(p)) { p.sendMessage(Util.plain("§c管理者(OP/ホスト)専用です")); return; }
        r.run();
        refresh(p.getServer());
    }

    // ----- 設定ボタン(モードごとに意味が変わる) -----
    private static String teamText(Settings s) { return s.teamCount == 0 ? "個人戦" : s.teamCount + "チーム戦"; }

    private static String settingText(int i) {
        Settings s = GameManager.settings;
        return switch (s.mode) {
            case ONEBLOCK -> i == 0 ? "§7(設定なし)\n§fエンドラ討伐が目標" : "";
            case PVP -> switch (i) {
                case 0 -> "§bチーム設定\n§f" + teamText(s);
                case 1 -> "§e初期ラッキーブロック\n§f" + s.startLucky + "個";
                case 2 -> "§e制限時間\n§f" + (s.pvpMinutes == 0 ? "無制限" : s.pvpMinutes + "分");
                default -> "";
            };
            case RACE -> switch (i) {
                case 0 -> "§bチーム設定\n§f" + teamText(s);
                case 1 -> "§eコース長\n§f" + s.raceLength + "m";
                case 2 -> "§eブロック壁の間隔\n§f" + s.raceSpacing + "マス";
                case 3 -> "§e曲がり角\n§f" + (s.raceCurves ? "あり" : "なし");
                case 4 -> "§e制限時間\n§f" + s.raceMinutes + "分";
                default -> "§eランダムイベント\n§f" + (s.raceEvents ? "ON" : "OFF");
            };
        };
    }

    private static void settingClick(int i) {
        Settings s = GameManager.settings;
        switch (s.mode) {
            case PVP -> {
                if (i == 0) s.teamCount = Settings.next(Settings.TEAM_COUNTS, s.teamCount);
                if (i == 1) s.startLucky = Settings.next(Settings.LUCKY_COUNTS, s.startLucky);
                if (i == 2) s.pvpMinutes = Settings.next(Settings.PVP_MINUTES, s.pvpMinutes);
            }
            case RACE -> {
                if (i == 0) s.teamCount = Settings.next(Settings.TEAM_COUNTS, s.teamCount);
                if (i == 1) s.raceLength = Settings.next(Settings.RACE_LENGTHS, s.raceLength);
                if (i == 2) s.raceSpacing = Settings.next(Settings.RACE_SPACINGS, s.raceSpacing);
                if (i == 3) s.raceCurves = !s.raceCurves;
                if (i == 4) s.raceMinutes = Settings.next(Settings.RACE_MINUTES, s.raceMinutes);
                if (i == 5) s.raceEvents = !s.raceEvents;
            }
            default -> {}
        }
    }

    private static boolean teamActive() {
        Settings s = GameManager.settings;
        return s.mode != Settings.Mode.ONEBLOCK && s.teamCount > 0;
    }

    /** 表示を最新の設定に更新。 */
    public static void refresh(MinecraftServer server) {
        if (info == null || Lobby.world == null) return;
        Settings s = GameManager.settings;
        for (int i = 0; i < 3; i++)
            modeL[i].text((s.mode == MODES[i] ? "§a§l▶ " : "§7") + MODES[i].label + "\n§8" + (s.mode == MODES[i] ? "選択中" : "クリックで選択"));
        for (int i = 0; i < 6; i++) setL[i].text(settingText(i).isEmpty() ? "§8-" : settingText(i));
        List<ServerPlayerEntity> all = Lobby.world.getPlayers();
        long spec = all.stream().filter(p -> s.isSpectator(p.getUuid())).count();
        info.text("§6§l★ LUCKY LOBBY ★\n§fモード: §e" + s.mode.label + "\n§7参加 §f" + (all.size() - spec) + "人 §7/ 観戦 §f" + spec + "人\n"
                + (GameManager.current == null ? "§a待機中" : "§c" + GameManager.current.name() + " 進行中"));
        int[] cnt = new int[6];
        for (ServerPlayerEntity p : all) {
            int t = s.teamOf.getOrDefault(p.getUuid(), -1);
            if (t >= 0 && t < 4) cnt[t]++; else if (t == -2) cnt[4]++; else cnt[5]++;
        }
        for (int i = 0; i < PADS.length; i++) {
            int kind = PADS[i][2];
            if (kind >= 0) {
                boolean active = teamActive() && kind < s.teamCount;
                padL[i].text(active ? Util.teamLabel(kind) + " §f" + cnt[i] + "人" : "§8" + Settings.TEAM_NAMES[kind] + "チーム(未使用)");
            } else if (kind == -2) padL[i].text("§7観戦 §f" + cnt[4] + "人");
            else padL[i].text("§fおまかせ(自動) §f" + cnt[5] + "人");
        }
    }

    private static int tickCount;

    /** 毎tick: チーム床の判定と表示更新。 */
    public static void tick(MinecraftServer server) {
        if (Lobby.world == null) return;
        tickCount++;
        Settings s = GameManager.settings;
        if (tickCount % 5 == 0) {
            boolean changed = false;
            for (ServerPlayerEntity p : Lobby.world.getPlayers()) {
                if (p.getY() > 67) continue;
                for (int[] pad : PADS) {
                    if (Math.abs(p.getX() - (pad[0] + 0.5)) >= 1.5 || Math.abs(p.getZ() - (pad[1] + 0.5)) >= 1.5) continue;
                    int kind = pad[2];
                    if (kind >= 0 && !(teamActive() && kind < s.teamCount)) break;
                    UUID id = p.getUuid();
                    if (s.teamOf.getOrDefault(id, -1) != kind) {
                        s.teamOf.put(id, kind);
                        changed = true;
                        Util.actionBar(p, kind >= 0 ? "§f" + Util.teamLabel(kind) + " §fに参加しました" : kind == -2 ? "§7観戦にしました" : "§f自動(ランダム)にしました");
                    }
                    break;
                }
            }
            if (changed) refresh(server);
        }
        if (tickCount % 20 == 0) refresh(server);
    }
}
