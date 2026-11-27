package jp.luckylobby;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.*;

/** 管理者用ロビーメニュー(モード / チーム / 各種設定 / スタート)。 */
public class AdminMenu implements Gui.Logic {
    public static void open(ServerPlayerEntity p) {
        if (!Util.isAdmin(p)) { p.sendMessage(Util.plain("§c管理者(OP)専用です")); return; }
        Gui.open(p, Text.literal("§6ラッキーロビー管理"), 6, new AdminMenu());
    }

    private static final int[] MODE_SLOTS = {10, 13, 16};

    @Override public void init(Gui g) { render(g); }

    private void render(Gui g) {
        Settings s = GameManager.settings;
        g.fill();
        g.set(4, Util.item(Items.NETHER_STAR, "§6§lモード選択", "§7現在: §f" + s.mode.label));
        Settings.Mode[] ms = Settings.Mode.values();
        for (int i = 0; i < ms.length; i++) {
            ItemStack it = Util.item(ms[i].icon, "§e" + ms[i].label, "§7" + ms[i].desc, "",
                    s.mode == ms[i] ? "§a▶ 選択中" : "§8クリックで選択");
            g.set(MODE_SLOTS[i], s.mode == ms[i] ? Util.glow(it) : it);
        }
        boolean coop = s.mode == Settings.Mode.ONEBLOCK;
        g.set(28, coop ? Util.item(Items.BARRIER, "§7チーム: 協力戦", "§7全員で協力してドラゴンを倒します")
                : Util.item(Items.WHITE_BANNER, "§bチーム設定: §f" + (s.teamCount == 0 ? "個人戦" : s.teamCount + "チーム"),
                        "§7クリックで 個人戦→2→3→4 を切替"));
        switch (s.mode) {
            case ONEBLOCK -> g.set(30, Util.item(Items.END_PORTAL_FRAME, "§dゴール: エンドラ討伐",
                    "§7ラッキーブロックから出るフレーム/エンダーアイで", "§7通常のエンドポータルを作って入ります", "§c討伐済みワールドでは開始できません"));
            case PVP -> {
                g.set(30, Util.item(Util.luckyItem(), "§e初期ラッキーブロック: §f" + s.startLucky + "個", "§7クリックで 8/16/32/64/128"));
                g.set(32, Util.item(Items.CLOCK, "§e制限時間: §f" + (s.pvpMinutes == 0 ? "無制限" : s.pvpMinutes + "分"), "§7クリックで切替"));
            }
            case RACE -> {
                g.set(30, Util.item(Items.RAIL, "§eコース長: §f" + s.raceLength + "m", "§7クリックで 100/200/300/400"));
                g.set(32, Util.item(Util.luckyItem(), "§eブロック壁の間隔: §f" + s.raceSpacing + "マス", "§7小さいほど壁が多く難しい"));
                g.set(34, Util.item(Items.COMPASS, "§e曲がり角: §f" + (s.raceCurves ? "あり" : "なし(直線)"), "§7クリックで切替"));
                g.set(36, Util.item(Items.CLOCK, "§e制限時間: §f" + s.raceMinutes + "分", "§7クリックで 5/10/15"));
                g.set(37, Util.item(Items.LIGHTNING_ROD, "§eランダムイベント: §f" + (s.raceEvents ? "ON" : "OFF"), "§71位妨害・最下位ワープなど"));
            }
        }
        if (!coop && s.teamCount > 0) g.set(38, Util.item(Items.PLAYER_HEAD, "§aチーム割り当て", "§7プレイヤーごとにチームを指定", "§7未指定はランダム振り分け"));

        List<ServerPlayerEntity> all = Lobby.world.getPlayers();
        long spec = all.stream().filter(p -> s.isSpectator(p.getUuid())).count();
        g.set(40, Util.item(Items.PAPER, "§f参加予定: §a" + (all.size() - spec) + "人 §7(観戦 " + spec + "人)", "§7ロビーにいるプレイヤーが対象"));
        g.set(45, Util.item(Items.BARRIER, "§c閉じる"));
        g.set(47, Util.item(Items.ENDER_PEARL, "§7チーム指定を全てリセット", "§7(観戦も解除してランダム振り分けに)"));
        g.set(49, Util.item(Items.LIME_CONCRETE, "§a§l▶ スタート", "§7" + s.mode.label + " を開始します"));
        if (GameManager.current != null) g.set(51, Util.item(Items.RED_CONCRETE, "§c§lゲームを強制終了"));
    }

    @Override public void click(Gui g, int slot, int button, ServerPlayerEntity p) {
        Settings s = GameManager.settings;
        Settings.Mode[] ms = Settings.Mode.values();
        for (int i = 0; i < ms.length; i++) if (slot == MODE_SLOTS[i]) s.mode = ms[i];
        if (slot == 28 && s.mode != Settings.Mode.ONEBLOCK) s.teamCount = Settings.next(Settings.TEAM_COUNTS, s.teamCount);
        if (slot == 30) switch (s.mode) {
            case ONEBLOCK -> {}
            case PVP -> s.startLucky = Settings.next(Settings.LUCKY_COUNTS, s.startLucky);
            case RACE -> s.raceLength = Settings.next(Settings.RACE_LENGTHS, s.raceLength);
        }
        if (slot == 32) switch (s.mode) {
            case PVP -> s.pvpMinutes = Settings.next(Settings.PVP_MINUTES, s.pvpMinutes);
            case RACE -> s.raceSpacing = Settings.next(Settings.RACE_SPACINGS, s.raceSpacing);
            default -> {}
        }
        if (slot == 38 && s.mode != Settings.Mode.ONEBLOCK && s.teamCount > 0) { TeamMenu.open(p); return; }
        if (slot == 34 && s.mode == Settings.Mode.RACE) s.raceCurves = !s.raceCurves;
        if (slot == 36 && s.mode == Settings.Mode.RACE) s.raceMinutes = Settings.next(Settings.RACE_MINUTES, s.raceMinutes);
        if (slot == 37 && s.mode == Settings.Mode.RACE) s.raceEvents = !s.raceEvents;
        if (slot == 45) { g.close(); return; }
        if (slot == 47) s.teamOf.clear();
        if (slot == 49) {
            String err = GameManager.start(p.getServer());
            if (err != null) p.sendMessage(Util.plain(err));
            else g.close();
            return;
        }
        if (slot == 51 && GameManager.current != null) { GameManager.stop(); g.close(); return; }
        render(g);
    }
}
