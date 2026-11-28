package jp.luckylobby;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/** ロビーのカジノ。スロット / ブラックジャック / ビデオポーカーを、ボタンと空中表示だけで遊ぶ。 */
public final class Casino {
    static final int[] BETS = {10, 50, 100, 500, 1000};
    static final Random RND = new Random();
    static Slot slot;
    static Blackjack bj;
    static Poker poker;

    public static void build(ServerWorld w) {
        slot = new Slot(w);
        bj = new Blackjack(w);
        poker = new Poker(w);
        Ui.button(w, 0, 6, Blocks.GOLD_BLOCK, "§e所持コイン確認", p -> { ensureCoins(p); info(p); p.sendMessage(Util.plain("§e所持コイン: §f" + bal(p))); });
        Ui.label(w, 0.5, 69.6, 5.5, 1f).text("§6§l★ CASINO ★\n§f左奥: スロット  /  正面: ブラックジャック  /  右奥: ポーカー\n§7ボタンをクリックして遊ぼう(最初は1000コイン)");
    }

    public static void tick(MinecraftServer s) {
        if (slot == null) return;
        slot.tick();
        bj.tick();
        poker.tick();
    }

    // ---------- 共通 ----------
    static void ensureCoins(ServerPlayerEntity p) {
        if (Coins.get(p.getServer(), p.getUuid()) < 10) {
            Coins.set(p.getServer(), p.getUuid(), 500);
            p.sendMessage(Util.plain("§e[カジノ] コインが尽きたので救済コイン §f500 §eを支給しました"));
        }
    }

    static long bal(ServerPlayerEntity p) { return Coins.get(p.getServer(), p.getUuid()); }

    static void info(ServerPlayerEntity p) { Util.actionBar(p, "§e所持コイン §f" + bal(p)); }

    static void sound(ServerWorld w, BlockPos pos, SoundEvent s, float pitch) {
        w.playSound(null, pos, s, SoundCategory.BLOCKS, 1f, pitch);
    }

    static void fx(ServerWorld w, double x, double y, double z, boolean win) {
        w.spawnParticles(win ? ParticleTypes.HAPPY_VILLAGER : ParticleTypes.SMOKE, x, y, z, 25, 1.2, 0.6, 0.6, 0.05);
        sound(w, BlockPos.ofFloored(x, y, z), win ? SoundEvents.ENTITY_PLAYER_LEVELUP : SoundEvents.ENTITY_VILLAGER_NO, 1f);
    }

    static void wall(ServerWorld w, int x1, int x2, int z, int h, Block b) {
        for (int x = x1; x <= x2; x++) for (int y = 65; y < 65 + h; y++)
            w.setBlockState(new BlockPos(x, y, z), b.getDefaultState(), Block.NOTIFY_LISTENERS);
    }

    // ---------- カード ----------
    record Card(int rank, int suit) {
        static final String[] SUITS = {"♠", "♥", "♦", "♣"};
        String label() {
            String r = switch (rank) { case 1 -> "A"; case 11 -> "J"; case 12 -> "Q"; case 13 -> "K"; default -> String.valueOf(rank); };
            return (suit == 1 || suit == 2 ? "§c" : "§f") + SUITS[suit] + r;
        }
    }

    static List<Card> deck() {
        List<Card> d = new ArrayList<>();
        for (int s = 0; s < 4; s++) for (int r = 1; r <= 13; r++) d.add(new Card(r, s));
        Collections.shuffle(d, RND);
        return d;
    }

    static Card draw(List<Card> d) { return d.remove(d.size() - 1); }

    // =================== スロット ===================
    static final class Slot {
        static final Item[] ICON = {Items.APPLE, Items.YELLOW_DYE, Items.BELL, Items.DIAMOND, Items.NETHER_STAR};
        static final String[] NAME = {"§cチェリー", "§eレモン", "§6ベル", "§bダイヤ", "§d§lセブン"};
        static final int[] W = {8, 6, 4, 2, 1};
        static final int[] PAY3 = {5, 10, 20, 50, 200};
        final ServerWorld w;
        final Ui.Label[] reel = new Ui.Label[3];
        final Ui.Label title;
        int betIdx = 1, t = -1, bet;
        UUID user;
        String userName = "";
        String msg = "§7SPINでレバーを引こう";
        final int[] cur = {0, 0, 0}, fin = new int[3], stopAt = {30, 45, 60};

        Slot(ServerWorld w) {
            this.w = w;
            wall(w, -16, -8, 12, 5, Blocks.BLACK_CONCRETE);
            for (int i = 0; i < 3; i++) {
                reel[i] = Ui.itemLabel(w, -14.5 + 3 * i, 67.0, 11.5, 1.8f);
                reel[i].item(ICON[0]);
            }
            title = Ui.label(w, -11.5, 69.6, 11.5, 1.3f);
            Ui.button(w, -15, 8, Blocks.RED_CONCRETE, "§cベット −", p -> { if (t < 0 && betIdx > 0) betIdx--; render(); });
            Ui.button(w, -12, 8, Blocks.GOLD_BLOCK, "§6§lSPIN", this::spin);
            Ui.button(w, -9, 8, Blocks.LIME_CONCRETE, "§aベット ＋", p -> { if (t < 0 && betIdx < BETS.length - 1) betIdx++; render(); });
            render();
        }

        static int pick() {
            int r = RND.nextInt(21), acc = 0;
            for (int i = 0; i < W.length; i++) { acc += W[i]; if (r < acc) return i; }
            return 0;
        }

        void render() {
            title.text("§6§l★ SLOT ★\n§7ベット §f" + BETS[betIdx] + (userName.isEmpty() ? "" : "  §7| §f" + userName) + "\n" + msg);
        }

        void spin(ServerPlayerEntity p) {
            if (t >= 0) { Util.actionBar(p, "§c回転中です"); return; }
            ensureCoins(p);
            bet = BETS[betIdx];
            if (!Coins.take(p.getServer(), p.getUuid(), bet)) { Util.actionBar(p, "§cコインが足りません"); return; }
            user = p.getUuid();
            userName = p.getName().getString();
            for (int i = 0; i < 3; i++) fin[i] = pick();
            t = 0;
            msg = "§eスピン中…";
            info(p);
            render();
        }

        void tick() {
            if (t < 0) return;
            t++;
            for (int r = 0; r < 3; r++) {
                if (t < stopAt[r]) { if (t % 3 == 0) { cur[r] = RND.nextInt(5); reel[r].item(ICON[cur[r]]); } }
                else if (t == stopAt[r]) {
                    cur[r] = fin[r];
                    reel[r].item(ICON[fin[r]]);
                    sound(w, new BlockPos(-12, 67, 11), SoundEvents.ENTITY_ITEM_PICKUP, 0.8f + 0.2f * r);
                }
            }
            if (t >= stopAt[2]) {
                t = -1;
                long win = 0;
                if (fin[0] == fin[1] && fin[1] == fin[2]) win = (long) PAY3[fin[0]] * bet;
                else { int ch = 0; for (int x : fin) if (x == 0) ch++; if (ch == 2) win = bet; }
                ServerPlayerEntity p = w.getServer().getPlayerManager().getPlayer(user);
                if (win > 0) {
                    Coins.add(w.getServer(), user, win);
                    msg = "§6§l当たり！ +" + win;
                    fx(w, -11.5, 68, 10.5, true);
                } else { msg = "§7はずれ…"; fx(w, -11.5, 68, 10.5, false); }
                if (p != null) { info(p); p.sendMessage(Util.plain("§6[スロット] " + msg)); }
            }
            render();
        }
    }

    // =================== ブラックジャック ===================
    static final class Blackjack {
        final ServerWorld w;
        final Ui.Label dealerL, playerL, statusL;
        List<Card> deck = new ArrayList<>(), ph = new ArrayList<>(), dh = new ArrayList<>();
        int phase = 0, betIdx = 1, bet, idle; // 0=ベット 1=プレイ 2=終了
        UUID user;
        String userName = "";
        String msg = "§7ベットを選んで DEAL";

        Blackjack(ServerWorld w) {
            this.w = w;
            wall(w, -8, 8, 18, 6, Blocks.GREEN_CONCRETE);
            dealerL = Ui.label(w, 0.5, 69.2, 16.5, 1.4f);
            playerL = Ui.label(w, 0.5, 67.2, 16.5, 1.4f);
            statusL = Ui.label(w, 0.5, 71.4, 16.5, 1.3f);
            Ui.button(w, -8, 12, Blocks.RED_CONCRETE, "§cベット −", p -> bet(p, -1));
            Ui.button(w, -6, 12, Blocks.LIME_CONCRETE, "§aベット ＋", p -> bet(p, 1));
            Ui.button(w, -2, 12, Blocks.GOLD_BLOCK, "§6§lDEAL", this::deal);
            Ui.button(w, 0, 12, Blocks.YELLOW_CONCRETE, "§e§lHIT", this::hit);
            Ui.button(w, 2, 12, Blocks.ORANGE_CONCRETE, "§6§lSTAND", p -> stand(p));
            Ui.button(w, 4, 12, Blocks.PURPLE_CONCRETE, "§d§lDOUBLE", this::dbl);
            render();
        }

        static int value(List<Card> h) {
            int sum = 0, aces = 0;
            for (Card c : h) { if (c.rank() == 1) { aces++; sum += 11; } else sum += Math.min(10, c.rank()); }
            while (sum > 21 && aces-- > 0) sum -= 10;
            return sum;
        }

        boolean playing() { return phase == 1; }

        boolean mine(ServerPlayerEntity p) {
            if (playing() && user != null && !user.equals(p.getUuid())) { Util.actionBar(p, "§c他のプレイヤーが使用中です"); return false; }
            return true;
        }

        void render() {
            StringBuilder d = new StringBuilder("§cディーラー");
            if (!dh.isEmpty()) {
                d.append(" §7(").append(playing() ? "?" : String.valueOf(value(dh))).append(")\n");
                for (int i = 0; i < dh.size(); i++) d.append(playing() && i == 1 ? "§8？" : dh.get(i).label()).append("  ");
            }
            dealerL.text(d.toString());
            StringBuilder p = new StringBuilder("§a" + (userName.isEmpty() ? "プレイヤー" : userName));
            if (!ph.isEmpty()) {
                p.append(" §7(").append(value(ph)).append(")\n");
                for (Card c : ph) p.append(c.label()).append("  ");
            }
            playerL.text(p.toString());
            statusL.text("§a§l♠ BLACKJACK ♠\n§7ベット §f" + (playing() ? bet : BETS[betIdx]) + "\n" + msg);
        }

        void bet(ServerPlayerEntity p, int d) {
            if (playing()) { Util.actionBar(p, "§cゲーム中は変更できません"); return; }
            betIdx = Math.max(0, Math.min(BETS.length - 1, betIdx + d));
            render();
        }

        void deal(ServerPlayerEntity p) {
            if (playing()) { Util.actionBar(p, mine(p) ? "§cゲーム中です(HIT/STAND/DOUBLE)" : "§c使用中です"); return; }
            ensureCoins(p);
            bet = BETS[betIdx];
            if (!Coins.take(p.getServer(), p.getUuid(), bet)) { Util.actionBar(p, "§cコインが足りません"); return; }
            user = p.getUuid(); userName = p.getName().getString(); idle = 0;
            deck = deck(); ph.clear(); dh.clear();
            ph.add(draw(deck)); dh.add(draw(deck)); ph.add(draw(deck)); dh.add(draw(deck));
            phase = 1; msg = "§7HIT / STAND / DOUBLE";
            boolean pbj = value(ph) == 21, dbj = value(dh) == 21;
            if (pbj || dbj) {
                phase = 2;
                if (pbj && dbj) { Coins.add(p.getServer(), user, bet); msg = "§eお互いBJ プッシュ"; }
                else if (pbj) { long win = (long) (bet * 2.5); Coins.add(p.getServer(), user, win); msg = "§6§lブラックジャック！ +" + win; fx(w, 0.5, 68, 15.5, true); }
                else { msg = "§cディーラーBJ… 負け"; fx(w, 0.5, 68, 15.5, false); }
            }
            info(p);
            render();
        }

        void hit(ServerPlayerEntity p) {
            if (!playing() || !mine(p)) return;
            idle = 0;
            ph.add(draw(deck));
            if (value(ph) > 21) settle(p, false, "§cバースト！");
            else if (value(ph) == 21) stand(p);
            render();
        }

        void dbl(ServerPlayerEntity p) {
            if (!playing() || !mine(p) || ph.size() != 2) return;
            if (!Coins.take(p.getServer(), p.getUuid(), bet)) { Util.actionBar(p, "§cコインが足りません"); return; }
            bet *= 2;
            ph.add(draw(deck));
            if (value(ph) > 21) settle(p, false, "§cバースト！"); else stand(p);
            render();
        }

        void stand(ServerPlayerEntity p) {
            if (!playing() || (p != null && !mine(p))) return;
            phase = 2;
            while (value(dh) < 17) dh.add(draw(deck));
            int pv = value(ph), dv = value(dh);
            if (dv > 21) settle(p, true, "§aディーラーバースト！ 勝ち");
            else if (pv > dv) settle(p, true, "§a勝ち！ " + pv + " vs " + dv);
            else if (pv == dv) { Coins.add(w.getServer(), user, bet); msg = "§eプッシュ(引き分け)"; }
            else settle(p, false, "§c負け… " + pv + " vs " + dv);
            render();
        }

        void settle(ServerPlayerEntity p, boolean win, String m) {
            phase = 2;
            if (win) { Coins.add(w.getServer(), user, bet * 2L); msg = m + " §6+" + bet; }
            else msg = m + " §7-" + bet;
            fx(w, 0.5, 68, 15.5, win);
            ServerPlayerEntity u = w.getServer().getPlayerManager().getPlayer(user);
            if (u != null) info(u);
        }

        void tick() {
            if (playing() && ++idle > 1800) { stand(null); }
        }
    }

    // =================== ビデオポーカー(ジャックス・オア・ベター) ===================
    static final class Poker {
        static final String[] HANDS = {"ハイカード", "ジャックス・オア・ベター", "ツーペア", "スリーカード", "ストレート", "フラッシュ", "フルハウス", "フォーカード", "ストレートフラッシュ", "ロイヤルフラッシュ"};
        static final int[] PAY = {0, 1, 2, 3, 4, 6, 9, 25, 50, 800};
        final ServerWorld w;
        final Ui.Label[] cardL = new Ui.Label[5];
        final Ui.Label statusL, dealBtn;
        List<Card> deck = new ArrayList<>();
        final Card[] hand = new Card[5];
        final boolean[] hold = new boolean[5];
        int phase = 0, betIdx = 1, bet, idle; // 0=ベット 1=ホールド選択
        UUID user;
        String userName = "";
        String msg = "§7ベットを選んで DEAL";

        Poker(ServerWorld w) {
            this.w = w;
            wall(w, 4, 16, 11, 7, Blocks.BLUE_CONCRETE);
            for (int i = 0; i < 5; i++) {
                final int idx = i;
                cardL[i] = Ui.label(w, 6.5 + 2 * i, 67.8, 9.5, 1.7f);
                Ui.button(w, 6 + 2 * i, 6, Blocks.WHITE_CONCRETE, "§fHOLD", p -> toggle(p, idx));
            }
            statusL = Ui.label(w, 10.5, 70.6, 9.5, 1.3f);
            Ui.label(w, 10.5, 72.4, 10.5, 0.9f).text("§e配当(×ベット)\n§fロイヤルフラッシュ§6800 §fSF§650 §f4カード§625 §fフルハウス§69\n§fフラッシュ§66 §fストレート§64 §f3カード§63 §f2ペア§62 §fJ以上ペア§61");
            Ui.button(w, 9, 3, Blocks.RED_CONCRETE, "§cベット −", p -> bet(p, -1));
            dealBtn = Ui.button(w, 11, 3, Blocks.GOLD_BLOCK, "§6§lDEAL", this::action);
            Ui.button(w, 13, 3, Blocks.LIME_CONCRETE, "§aベット ＋", p -> bet(p, 1));
            render();
        }

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

        boolean mine(ServerPlayerEntity p) {
            if (phase == 1 && user != null && !user.equals(p.getUuid())) { Util.actionBar(p, "§c他のプレイヤーが使用中です"); return false; }
            return true;
        }

        void render() {
            for (int i = 0; i < 5; i++)
                cardL[i].text(hand[i] == null ? "§8？" : hand[i].label() + (phase == 1 && hold[i] ? "\n§a§lHOLD" : ""));
            dealBtn.text(phase == 0 ? "§6§lDEAL" : "§e§lDRAW");
            statusL.text("§9§l♥ VIDEO POKER ♥\n§7ベット §f" + (phase == 1 ? bet : BETS[betIdx]) + (userName.isEmpty() ? "" : " §7| §f" + userName) + "\n" + msg);
        }

        void bet(ServerPlayerEntity p, int d) {
            if (phase == 1) { Util.actionBar(p, "§cゲーム中は変更できません"); return; }
            betIdx = Math.max(0, Math.min(BETS.length - 1, betIdx + d));
            render();
        }

        void toggle(ServerPlayerEntity p, int i) {
            if (phase != 1 || !mine(p)) return;
            idle = 0;
            hold[i] = !hold[i];
            render();
        }

        void action(ServerPlayerEntity p) {
            if (phase == 0) {
                ensureCoins(p);
                bet = BETS[betIdx];
                if (!Coins.take(p.getServer(), p.getUuid(), bet)) { Util.actionBar(p, "§cコインが足りません"); return; }
                user = p.getUuid(); userName = p.getName().getString(); idle = 0;
                deck = deck();
                for (int i = 0; i < 5; i++) { hand[i] = draw(deck); hold[i] = false; }
                phase = 1; msg = "§7残すカードの下のボタンでHOLD → DRAW";
                info(p);
            } else {
                if (!mine(p)) return;
                finishDraw(p);
            }
            render();
        }

        void finishDraw(ServerPlayerEntity p) {
            for (int i = 0; i < 5; i++) if (!hold[i]) hand[i] = draw(deck);
            int e = eval(hand);
            long win = (long) PAY[e] * bet;
            if (win > 0) { Coins.add(w.getServer(), user, win); msg = "§6§l" + HANDS[e] + "！ +" + win; }
            else msg = "§7" + HANDS[e] + "… はずれ";
            phase = 0;
            fx(w, 10.5, 68, 8.5, win > 0);
            ServerPlayerEntity u = w.getServer().getPlayerManager().getPlayer(user);
            if (u != null) info(u);
            java.util.Arrays.fill(hold, false);
        }

        void tick() {
            if (phase == 1 && ++idle > 1800) { finishDraw(null); render(); }
        }
    }
}
