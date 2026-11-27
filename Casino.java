package jp.luckylobby;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.util.*;

/** ロビーのカジノ: スロット / ブラックジャック / ビデオポーカー (チェストGUI)。 */
public final class Casino {
    static final int[] BETS = {10, 50, 100, 500, 1000};
    static final Random RND = new Random();

    // ---------- 入口 ----------
    public static void openMenu(ServerPlayerEntity p) {
        ensureCoins(p);
        Gui.open(p, Text.literal("§6§lカジノ"), 3, new Gui.Logic() {
            public void init(Gui g) {
                g.fill();
                g.set(11, Util.item(Items.GOLD_INGOT, "§6§lスロットマシン", "§7揃えば大当たり！", "§7最大 x200"));
                g.set(13, Util.item(Items.EMERALD, "§a§lブラックジャック", "§721に近づけ！ BJは x2.5"));
                g.set(15, Util.item(Items.LAPIS_LAZULI, "§9§lビデオポーカー", "§7ジャックス・オア・ベター", "§7ロイヤルフラッシュ x800"));
                g.set(22, Util.item(Items.SUNFLOWER, "§e所持コイン: §f" + Coins.get(p.getServer(), p.getUuid())));
            }
            public void click(Gui g, int slot, int b, ServerPlayerEntity pl) {
                switch (slot) {
                    case 11 -> open(pl, "slot");
                    case 13 -> open(pl, "blackjack");
                    case 15 -> open(pl, "poker");
                    default -> {}
                }
            }
        });
    }

    public static void open(ServerPlayerEntity p, String game) {
        ensureCoins(p);
        switch (game) {
            case "slot" -> Gui.open(p, Text.literal("§6スロットマシン"), 6, new Slots());
            case "blackjack", "bj" -> Gui.open(p, Text.literal("§aブラックジャック"), 6, new Blackjack());
            case "poker" -> Gui.open(p, Text.literal("§9ビデオポーカー"), 6, new Poker());
            default -> openMenu(p);
        }
    }

    static void ensureCoins(ServerPlayerEntity p) {
        if (Coins.get(p.getServer(), p.getUuid()) < 10) {
            Coins.set(p.getServer(), p.getUuid(), 500);
            p.sendMessage(Util.plain("§e[カジノ] コインが尽きたので救済コイン §f500 §eを支給しました"));
        }
    }

    static long bal(ServerPlayerEntity p) { return Coins.get(p.getServer(), p.getUuid()); }

    static void sound(ServerPlayerEntity p, boolean good) {
        p.playSound(good ? SoundEvents.ENTITY_PLAYER_LEVELUP : SoundEvents.ENTITY_VILLAGER_NO, 0.7f, 1f);
    }

    /** 共通: ベット額ボタン(−:slotMinus, 表示:slotShow, +:slotPlus) */
    static ItemStack betShow(int bet, long balance) {
        return Util.item(Items.GOLD_INGOT, "§6ベット: §f" + bet, "§7所持コイン: §f" + balance);
    }

    // ---------- カード ----------
    record Card(int rank, int suit) {
        static final String[] SUITS = {"♠", "♥", "♦", "♣"};
        String label() {
            String r = switch (rank) { case 1 -> "A"; case 11 -> "J"; case 12 -> "Q"; case 13 -> "K"; default -> String.valueOf(rank); };
            return (suit == 1 || suit == 2 ? "§c" : "§f") + SUITS[suit] + " " + r;
        }
        ItemStack item() { return Util.item(Items.PAPER, "§l" + label()); }
    }

    static List<Card> deck() {
        List<Card> d = new ArrayList<>();
        for (int s = 0; s < 4; s++) for (int r = 1; r <= 13; r++) d.add(new Card(r, s));
        Collections.shuffle(d, RND);
        return d;
    }

    static Card draw(List<Card> d) { return d.remove(d.size() - 1); }

    // ================= スロット =================
    static final class Slots implements Gui.Logic {
        static final Item[] ICON = {Items.APPLE, Items.YELLOW_DYE, Items.BELL, Items.DIAMOND, Items.NETHER_STAR};
        static final String[] NAME = {"§cチェリー", "§eレモン", "§6ベル", "§bダイヤ", "§d§lセブン"};
        static final int[] W = {8, 6, 4, 2, 1};
        static final int[] PAY3 = {5, 10, 20, 50, 200};
        int betIdx = 1, t = -1, bet;
        int[] reel = {0, 0, 0}, fin = new int[3];
        final int[] stopAt = {30, 45, 60};
        String msg = "§7SPINでレバーを引こう";

        static int pick() {
            int r = RND.nextInt(21), acc = 0;
            for (int i = 0; i < W.length; i++) { acc += W[i]; if (r < acc) return i; }
            return 0;
        }

        public void init(Gui g) { g.fill(); render(g, null); }

        void render(Gui g, ServerPlayerEntity p) {
            ServerPlayerEntity v = g.viewer;
            g.set(4, Util.item(Items.SUNFLOWER, "§e所持コイン: §f" + bal(v)));
            for (int r = 0; r < 3; r++) g.set(21 + r, Util.item(ICON[reel[r]], NAME[reel[r]]));
            g.set(12, Util.item(Items.HOPPER, "§7▼")); g.set(13, Util.item(Items.HOPPER, "§7▼")); g.set(14, Util.item(Items.HOPPER, "§7▼"));
            g.set(31, Util.item(Items.PAPER, msg));
            g.set(45, Util.item(Items.BARRIER, "§c閉じる"));
            g.set(47, Util.item(Items.RED_STAINED_GLASS_PANE, "§cベット −"));
            g.set(48, betShow(BETS[betIdx], bal(v)));
            g.set(49, Util.item(Items.LIME_CONCRETE, "§a§lSPIN", "§7レバーを引く"));
            g.set(51, Util.item(Items.LIME_STAINED_GLASS_PANE, "§aベット ＋"));
            g.set(53, Util.item(Items.BOOK, "§e配当表", "§c×3 §7x5  §e×3 §7x10  §6×3 §7x20", "§b×3 §7x50  §d×3 §7x200", "§cチェリー2個 §7x1"));
        }

        public void click(Gui g, int slot, int b, ServerPlayerEntity p) {
            if (t >= 0) return;
            if (slot == 45) { g.close(); return; }
            if (slot == 47 && betIdx > 0) betIdx--;
            if (slot == 51 && betIdx < BETS.length - 1) betIdx++;
            if (slot == 49) {
                bet = BETS[betIdx];
                if (!Coins.take(p.getServer(), p.getUuid(), bet)) { msg = "§cコインが足りません"; sound(p, false); }
                else { for (int i = 0; i < 3; i++) fin[i] = pick(); t = 0; msg = "§eスピン中…"; }
            }
            render(g, p);
        }

        public void tick(Gui g) {
            if (t < 0) return;
            t++;
            ServerPlayerEntity p = g.viewer;
            for (int r = 0; r < 3; r++) {
                if (t < stopAt[r]) { if (t % 3 == 0) reel[r] = RND.nextInt(5); }
                else if (t == stopAt[r]) { reel[r] = fin[r]; p.playSound(SoundEvents.ENTITY_ITEM_PICKUP, 1f, 0.8f + 0.2f * r); }
            }
            if (t >= stopAt[2]) {
                t = -1;
                long win = 0;
                if (fin[0] == fin[1] && fin[1] == fin[2]) win = (long) PAY3[fin[0]] * bet;
                else { int ch = 0; for (int x : fin) if (x == 0) ch++; if (ch == 2) win = bet; }
                if (win > 0) { Coins.add(p.getServer(), p.getUuid(), win); msg = "§6§l当たり！ +" + win + " コイン"; sound(p, true); }
                else msg = "§7はずれ…";
            }
            render(g, p);
        }
    }

    // ================= ブラックジャック =================
    static final class Blackjack implements Gui.Logic {
        List<Card> deck = new ArrayList<>(), ph = new ArrayList<>(), dh = new ArrayList<>();
        int phase = 0, betIdx = 1, bet; // 0=ベット 1=プレイ 2=終了
        String msg = "§7ベットを選んでDEAL";

        static int value(List<Card> h) {
            int sum = 0, aces = 0;
            for (Card c : h) { if (c.rank() == 1) { aces++; sum += 11; } else sum += Math.min(10, c.rank()); }
            while (sum > 21 && aces-- > 0) sum -= 10;
            return sum;
        }

        public void init(Gui g) { g.fill(); render(g); }

        void render(Gui g) {
            for (int s : new int[]{10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43}) g.set(s, Util.item(Items.GRAY_STAINED_GLASS_PANE, " "));
            ServerPlayerEntity v = g.viewer;
            g.set(4, Util.item(Items.SUNFLOWER, "§e所持コイン: §f" + bal(v)));
            int[] ds = {10,11,12,13,14,15,16,19,20,21,22,23,24,25};
            for (int i = 0; i < dh.size() && i < ds.length; i++)
                g.set(ds[i], (phase == 1 && i == 1) ? Util.item(Items.PAPER, "§8？") : dh.get(i).item());
            int[] ps = {28,29,30,31,32,33,34,37,38,39,40,41,42,43};
            for (int i = 0; i < ph.size() && i < ps.length; i++) g.set(ps[i], ph.get(i).item());
            g.set(9, Util.item(Items.RED_BANNER, "§cディーラー", dh.isEmpty() ? "" : "§7合計: §f" + (phase == 1 ? "?" : value(dh))));
            g.set(27, Util.item(Items.GREEN_BANNER, "§aあなた", ph.isEmpty() ? "" : "§7合計: §f" + value(ph)));
            g.set(35, Util.item(Items.PAPER, msg));
            g.set(45, Util.item(Items.BARRIER, "§c閉じる"));
            boolean bettingPhase = phase != 1;
            g.set(46, bettingPhase ? Util.item(Items.RED_STAINED_GLASS_PANE, "§cベット −") : filler());
            g.set(47, betShow(phase == 1 ? bet : BETS[betIdx], bal(v)));
            g.set(48, bettingPhase ? Util.item(Items.LIME_STAINED_GLASS_PANE, "§aベット ＋") : filler());
            g.set(49, bettingPhase ? Util.item(Items.LIME_CONCRETE, "§a§lDEAL") : filler());
            g.set(50, phase == 1 ? Util.item(Items.YELLOW_CONCRETE, "§e§lHIT", "§7カードを引く") : filler());
            g.set(51, phase == 1 ? Util.item(Items.ORANGE_CONCRETE, "§6§lSTAND", "§7勝負する") : filler());
            g.set(52, phase == 1 && ph.size() == 2 ? Util.item(Items.PURPLE_CONCRETE, "§d§lDOUBLE", "§7ベット2倍で1枚だけ引く") : filler());
        }

        static ItemStack filler() { return Util.item(Items.GRAY_STAINED_GLASS_PANE, " "); }

        public void click(Gui g, int slot, int b, ServerPlayerEntity p) {
            if (slot == 45) { g.close(); return; }
            if (phase != 1) {
                if (slot == 46 && betIdx > 0) betIdx--;
                if (slot == 48 && betIdx < BETS.length - 1) betIdx++;
                if (slot == 49) deal(p);
            } else {
                if (slot == 50) { ph.add(draw(deck)); if (value(ph) > 21) settle(p, "§cバースト！"); else if (value(ph) == 21) stand(p); }
                if (slot == 51) stand(p);
                if (slot == 52 && ph.size() == 2 && Coins.take(p.getServer(), p.getUuid(), bet)) {
                    bet *= 2; ph.add(draw(deck));
                    if (value(ph) > 21) settle(p, "§cバースト！"); else stand(p);
                }
            }
            render(g);
        }

        void deal(ServerPlayerEntity p) {
            bet = BETS[betIdx];
            if (!Coins.take(p.getServer(), p.getUuid(), bet)) { msg = "§cコインが足りません"; sound(p, false); return; }
            deck = deck(); ph.clear(); dh.clear();
            ph.add(draw(deck)); dh.add(draw(deck)); ph.add(draw(deck)); dh.add(draw(deck));
            phase = 1; msg = "§7HIT / STAND / DOUBLE";
            boolean pbj = value(ph) == 21, dbj = value(dh) == 21;
            if (pbj || dbj) {
                phase = 2;
                if (pbj && dbj) { Coins.add(p.getServer(), p.getUuid(), bet); msg = "§eお互いBJ プッシュ"; }
                else if (pbj) { long w = (long) (bet * 2.5); Coins.add(p.getServer(), p.getUuid(), w); msg = "§6§lブラックジャック！ +" + w; sound(p, true); }
                else { msg = "§cディーラーBJ… 負け"; sound(p, false); }
            }
        }

        void stand(ServerPlayerEntity p) {
            phase = 2;
            while (value(dh) < 17) dh.add(draw(deck));
            int pv = value(ph), dv = value(dh);
            if (dv > 21) settle(p, "§aディーラーバースト！ 勝ち");
            else if (pv > dv) settle(p, "§a勝ち！ " + pv + " vs " + dv);
            else if (pv == dv) { Coins.add(p.getServer(), p.getUuid(), bet); msg = "§eプッシュ(引き分け)"; }
            else settle(p, "§c負け… " + pv + " vs " + dv);
        }

        /** メッセージに「勝ち」を含めば2倍払い戻し。 */
        void settle(ServerPlayerEntity p, String m) {
            phase = 2;
            if (m.contains("勝ち")) { Coins.add(p.getServer(), p.getUuid(), bet * 2L); msg = m + " §6+" + bet; sound(p, true); }
            else { msg = m + " §7-" + bet; sound(p, false); }
        }
    }

    // ================= ビデオポーカー =================
    static final class Poker implements Gui.Logic {
        static final String[] HANDS = {"ハイカード", "ジャックス・オア・ベター", "ツーペア", "スリーカード", "ストレート", "フラッシュ", "フルハウス", "フォーカード", "ストレートフラッシュ", "ロイヤルフラッシュ"};
        static final int[] PAY = {0, 1, 2, 3, 4, 6, 9, 25, 50, 800};
        List<Card> deck = new ArrayList<>();
        Card[] hand = new Card[5];
        boolean[] hold = new boolean[5];
        int phase = 0, betIdx = 1, bet; // 0=ベット 1=ホールド選択
        String msg = "§7ベットを選んでDEAL";
        static final int[] CARD_SLOTS = {20, 21, 22, 23, 24};

        static int eval(Card[] h) {
            int[] cnt = new int[14];
            boolean flush = true;
            for (Card c : h) { cnt[c.rank()]++; if (c.suit() != h[0].suit()) flush = false; }
            int pairs = 0, trips = 0, quads = 0, pairRank = 0;
            for (int r = 1; r <= 13; r++) {
                if (cnt[r] == 2) { pairs++; pairRank = r; }
                if (cnt[r] == 3) trips++;
                if (cnt[r] == 4) quads++;
            }
            boolean straight = false, royal = false;
            if (pairs == 0 && trips == 0 && quads == 0) {
                int min = 14, max = 0;
                for (int r = 1; r <= 13; r++) if (cnt[r] > 0) { min = Math.min(min, r); max = Math.max(max, r); }
                if (max - min == 4) straight = true;
                if (cnt[1] == 1 && cnt[10] == 1 && cnt[11] == 1 && cnt[12] == 1 && cnt[13] == 1) { straight = true; royal = true; }
            }
            if (straight && flush) return royal ? 9 : 8;
            if (quads > 0) return 7;
            if (trips > 0 && pairs > 0) return 6;
            if (flush) return 5;
            if (straight) return 4;
            if (trips > 0) return 3;
            if (pairs == 2) return 2;
            if (pairs == 1 && (pairRank == 1 || pairRank >= 11)) return 1;
            return 0;
        }

        public void init(Gui g) { g.fill(); render(g); }

        void render(Gui g) {
            ServerPlayerEntity v = g.viewer;
            g.set(4, Util.item(Items.SUNFLOWER, "§e所持コイン: §f" + bal(v)));
            for (int i = 0; i < 5; i++) {
                g.set(CARD_SLOTS[i], hand[i] == null ? Util.item(Items.GRAY_STAINED_GLASS_PANE, " ") : hand[i].item());
                g.set(CARD_SLOTS[i] + 9, phase == 1
                        ? (hold[i] ? Util.item(Items.LIME_STAINED_GLASS_PANE, "§a§lHOLD", "§7クリックで解除") : Util.item(Items.RED_STAINED_GLASS_PANE, "§7捨てる", "§7カードをクリックでHOLD"))
                        : Util.item(Items.GRAY_STAINED_GLASS_PANE, " "));
            }
            g.set(40, Util.item(Items.PAPER, msg));
            g.set(45, Util.item(Items.BARRIER, "§c閉じる"));
            g.set(46, phase == 0 ? Util.item(Items.RED_STAINED_GLASS_PANE, "§cベット −") : Util.item(Items.GRAY_STAINED_GLASS_PANE, " "));
            g.set(47, betShow(phase == 1 ? bet : BETS[betIdx], bal(v)));
            g.set(48, phase == 0 ? Util.item(Items.LIME_STAINED_GLASS_PANE, "§aベット ＋") : Util.item(Items.GRAY_STAINED_GLASS_PANE, " "));
            g.set(49, phase == 0 ? Util.item(Items.LIME_CONCRETE, "§a§lDEAL") : Util.item(Items.YELLOW_CONCRETE, "§e§lDRAW", "§7HOLD以外を引き直す"));
            g.set(53, Util.item(Items.BOOK, "§e配当表(×ベット)", "§fロイヤルフラッシュ §6800", "§fストレートフラッシュ §650", "§fフォーカード §625", "§fフルハウス §69",
                    "§fフラッシュ §66", "§fストレート §64", "§fスリーカード §63", "§fツーペア §62", "§fJ以上のペア §61"));
        }

        public void click(Gui g, int slot, int b, ServerPlayerEntity p) {
            if (slot == 45) { g.close(); return; }
            if (phase == 0) {
                if (slot == 46 && betIdx > 0) betIdx--;
                if (slot == 48 && betIdx < BETS.length - 1) betIdx++;
                if (slot == 49) {
                    bet = BETS[betIdx];
                    if (!Coins.take(p.getServer(), p.getUuid(), bet)) { msg = "§cコインが足りません"; sound(p, false); }
                    else {
                        deck = deck();
                        for (int i = 0; i < 5; i++) { hand[i] = draw(deck); hold[i] = false; }
                        phase = 1; msg = "§7残すカードをクリックしてHOLD → DRAW";
                    }
                }
            } else {
                for (int i = 0; i < 5; i++) if (slot == CARD_SLOTS[i] || slot == CARD_SLOTS[i] + 9) hold[i] = !hold[i];
                if (slot == 49) {
                    for (int i = 0; i < 5; i++) if (!hold[i]) hand[i] = draw(deck);
                    int e = eval(hand);
                    long win = (long) PAY[e] * bet;
                    if (win > 0) { Coins.add(p.getServer(), p.getUuid(), win); msg = "§6§l" + HANDS[e] + "！ +" + win; sound(p, true); }
                    else { msg = "§7" + HANDS[e] + "… はずれ"; sound(p, false); }
                    phase = 0;
                }
            }
            render(g);
        }
    }
}
