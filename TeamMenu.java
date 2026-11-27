package jp.luckylobby;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.*;

/** プレイヤーをクリックしてチーム → 観戦 → 自動 と切り替える。 */
public class TeamMenu implements Gui.Logic {
    private final List<UUID> order = new ArrayList<>();

    public static void open(ServerPlayerEntity p) {
        Gui.open(p, Text.literal("§bチーム割り当て"), 6, new TeamMenu());
    }

    @Override public void init(Gui g) { render(g); }

    private void render(Gui g) {
        Settings s = GameManager.settings;
        g.fill();
        order.clear();
        int slot = 0;
        for (ServerPlayerEntity pl : Lobby.world.getPlayers()) {
            if (slot >= 45) break;
            int t = s.teamOf.getOrDefault(pl.getUuid(), -1);
            if (t >= s.teamCount) t = -1;
            String label = t == -2 ? "§7観戦" : t == -1 ? "§f自動(ランダム)" : Util.teamLabel(t);
            ItemStack head = Util.item(Items.PLAYER_HEAD, "§f" + pl.getName().getString(), "§7所属: " + label, "§8クリックで切替");
            head.getOrCreateNbt().putString("SkullOwner", pl.getName().getString());
            g.set(slot, head);
            order.add(pl.getUuid());
            slot++;
        }
        g.set(45, Util.item(Items.ARROW, "§7← 管理メニューへ戻る"));
    }

    @Override public void click(Gui g, int slot, int button, ServerPlayerEntity p) {
        Settings s = GameManager.settings;
        if (slot == 45) { AdminMenu.open(p); return; }
        if (slot < order.size()) {
            UUID id = order.get(slot);
            int cur = s.teamOf.getOrDefault(id, -1);
            if (cur >= s.teamCount) cur = -1;
            int nxt = cur == -1 ? 0 : cur == -2 ? -1 : (cur + 1 >= s.teamCount ? -2 : cur + 1);
            s.teamOf.put(id, nxt);
        }
        render(g);
    }
}
