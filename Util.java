package jp.luckylobby;

import net.minecraft.block.Block;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Collection;

public final class Util {
    public static final Identifier LUCKY = new Identifier("lucky", "lucky_block");

    public static boolean luckyPresent() { return Registries.BLOCK.containsId(LUCKY); }
    public static Block luckyBlock() { return Registries.BLOCK.get(LUCKY); }
    public static Item luckyItem() { return Registries.ITEM.get(LUCKY); }

    public static MutableText plain(String s) {
        return Text.literal(s).setStyle(Style.EMPTY.withItalic(false));
    }

    public static ItemStack item(Item item, String name, String... lore) {
        ItemStack s = new ItemStack(item);
        s.setCustomName(plain(name));
        if (lore.length > 0) {
            NbtList l = new NbtList();
            for (String x : lore) l.add(NbtString.of(Text.Serializer.toJson(plain(x))));
            s.getOrCreateSubNbt("display").put("Lore", l);
        }
        s.getOrCreateNbt().putInt("HideFlags", 63);
        return s;
    }

    public static ItemStack glow(ItemStack s) {
        s.addEnchantment(Enchantments.UNBREAKING, 1);
        return s;
    }

    public static ItemStack count(ItemStack s, int n) { s.setCount(n); return s; }

    public static void title(ServerPlayerEntity p, String t, String sub, int in, int stay, int out) {
        p.networkHandler.sendPacket(new TitleFadeS2CPacket(in, stay, out));
        p.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal(sub)));
        p.networkHandler.sendPacket(new TitleS2CPacket(Text.literal(t)));
    }

    public static void actionBar(ServerPlayerEntity p, String s) { p.sendMessage(Text.literal(s), true); }

    public static void broadcast(Collection<ServerPlayerEntity> ps, String msg) {
        for (ServerPlayerEntity p : ps) p.sendMessage(Text.literal(msg));
    }

    public static void giveLucky(ServerPlayerEntity p, int n) {
        Item it = luckyItem();
        while (n > 0) {
            int c = Math.min(64, n);
            p.getInventory().offerOrDrop(new ItemStack(it, c));
            n -= c;
        }
    }

    public static String teamLabel(int t) {
        if (t < 0) return "§f個人";
        return Settings.TEAM_COLORS[t] + "§l" + Settings.TEAM_NAMES[t] + "チーム§r";
    }

    /** OP、またはシングルプレイ/LANのホストなら管理者。 */
    public static boolean isAdmin(ServerPlayerEntity p) {
        return p.hasPermissionLevel(2) || p.getServer().isHost(p.getGameProfile());
    }

    public static String mmss(int ticks) {
        int s = Math.max(0, ticks / 20);
        return String.format("%02d:%02d", s / 60, s % 60);
    }
}
