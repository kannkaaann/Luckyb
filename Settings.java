package jp.luckylobby;

import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Formatting;

import java.util.*;

/** ロビーで管理者が設定する内容。 */
public class Settings {
    public enum Mode {
        ONEBLOCK("ラッキーワンブロック", Items.GRASS_BLOCK, "ラッキーブロックからポータルを作りエンドラ討伐を目指す協力戦"),
        PVP("ラッキーブロックPVP", Items.DIAMOND_SWORD, "ラッキーブロックで装備を整えて最後の1人(1チーム)を目指す"),
        RACE("ラッキー一本道レース", Items.GOLDEN_BOOTS, "空中の一本道を塞ぐラッキーブロックを壊してゴールへ一番乗り");

        public final String label; public final Item icon; public final String desc;
        Mode(String l, Item i, String d) { label = l; icon = i; desc = d; }
    }

    public static final String[] TEAM_NAMES = {"赤", "青", "緑", "黄"};
    public static final Formatting[] TEAM_COLORS = {Formatting.RED, Formatting.BLUE, Formatting.GREEN, Formatting.YELLOW};
    public static final String[] TEAM_CMD_COLORS = {"red", "blue", "green", "yellow"};
    public static final Item[] TEAM_ITEMS = {Items.RED_CONCRETE, Items.BLUE_CONCRETE, Items.LIME_CONCRETE, Items.YELLOW_CONCRETE};

    public static final int[] TEAM_COUNTS = {0, 2, 3, 4};
    public static final int[] LUCKY_COUNTS = {8, 16, 32, 64, 128};
    public static final int[] PVP_MINUTES = {0, 5, 10, 15, 20};
    public static final int[] RACE_LENGTHS = {100, 200, 300, 400};
    public static final int[] RACE_SPACINGS = {5, 8, 12, 16};
    public static final int[] RACE_MINUTES = {5, 10, 15};

    public Mode mode = Mode.ONEBLOCK;
    public int teamCount = 0;          // 0 = 個人戦
    public int startLucky = 32;        // PVP 初期配布ラッキーブロック数
    public int pvpMinutes = 10;        // 0 = 無制限
    public int raceLength = 200;
    public int raceSpacing = 8;
    public boolean raceCurves = true;   // 曲がり角あり
    public int raceMinutes = 10;        // 制限時間
    public boolean raceEvents = true;   // ランダムイベント
    /** -1=自動, 0..3=チーム, -2=観戦 */
    public final Map<UUID, Integer> teamOf = new HashMap<>();

    public static int next(int[] arr, int cur) {
        for (int i = 0; i < arr.length; i++) if (arr[i] == cur) return arr[(i + 1) % arr.length];
        return arr[0];
    }

    public boolean isSpectator(UUID id) { return teamOf.getOrDefault(id, -1) == -2; }

    /** 手動指定を尊重し、未指定者を人数の少ないチームへランダムに振り分ける。 */
    public Map<UUID, Integer> resolveTeams(List<ServerPlayerEntity> ps) {
        Map<UUID, Integer> out = new HashMap<>();
        if (teamCount <= 0) { for (ServerPlayerEntity p : ps) out.put(p.getUuid(), -1); return out; }
        int[] sizes = new int[teamCount];
        List<ServerPlayerEntity> pending = new ArrayList<>();
        for (ServerPlayerEntity p : ps) {
            Integer t = teamOf.get(p.getUuid());
            if (t != null && t >= 0 && t < teamCount) { out.put(p.getUuid(), t); sizes[t]++; }
            else pending.add(p);
        }
        Collections.shuffle(pending);
        for (ServerPlayerEntity p : pending) {
            int best = 0;
            for (int i = 1; i < teamCount; i++) if (sizes[i] < sizes[best]) best = i;
            out.put(p.getUuid(), best);
            sizes[best]++;
        }
        return out;
    }
}
