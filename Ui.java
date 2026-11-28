package jp.luckylobby;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.WallMountedBlock;
import net.minecraft.block.enums.WallMountLocation;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtFloat;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** ロビー内の物理ボタン(ブロック)と、空中に浮かぶ文字/アイテム表示。インベントリ画面は使わない。 */
public final class Ui {
    private static final Map<Long, Consumer<ServerPlayerEntity>> BUTTONS = new HashMap<>();

    public static void clear() { BUTTONS.clear(); }

    /** ボタンのクリックを処理。登録されたボタンなら true。 */
    public static boolean click(BlockPos pos, ServerPlayerEntity p) {
        Consumer<ServerPlayerEntity> a = BUTTONS.get(pos.asLong());
        if (a == null) return false;
        a.accept(p);
        return true;
    }

    /** 台座(ブロック)の上に石のボタンを置き、上に表示ラベルを出す。 */
    public static Label button(ServerWorld w, int x, int z, Block pedestal, String text, Consumer<ServerPlayerEntity> action) {
        BlockPos base = new BlockPos(x, 65, z);
        w.setBlockState(base, pedestal.getDefaultState(), Block.NOTIFY_LISTENERS);
        BlockPos top = base.up();
        w.setBlockState(top, Blocks.STONE_BUTTON.getDefaultState().with(WallMountedBlock.FACE, WallMountLocation.FLOOR), Block.NOTIFY_LISTENERS);
        BUTTONS.put(base.asLong(), action);
        BUTTONS.put(top.asLong(), action);
        Label l = label(w, x + 0.5, 67.3, z + 0.5, 1f);
        l.text(text);
        return l;
    }

    public static Label label(ServerWorld w, double x, double y, double z, float scale) {
        return new Label(w, "text_display", x, y, z, scale);
    }

    public static Label itemLabel(ServerWorld w, double x, double y, double z, float scale) {
        return new Label(w, "item_display", x, y, z, scale);
    }

    public static final class Label {
        private final ServerWorld world;
        private final String type;
        private final double x, y, z;
        private final float scale;
        private UUID id;
        private String last = "";

        Label(ServerWorld w, String type, double x, double y, double z, float scale) {
            this.world = w; this.type = type; this.x = x; this.y = y; this.z = z; this.scale = scale;
        }

        private Entity entity() { return id == null ? null : world.getEntity(id); }

        /** 文字を変更(§カラーコード・改行可)。 */
        public void text(String s) {
            if (s.equals(last) && entity() != null) return;
            last = s;
            apply(n -> {
                n.putString("text", Text.Serializer.toJson(Text.literal(s)));
                n.putInt("line_width", 400);
            });
        }

        /** 表示するアイテムを変更。 */
        public void item(Item it) {
            String key = Registries.ITEM.getId(it).toString();
            if (key.equals(last) && entity() != null) return;
            last = key;
            apply(n -> {
                NbtCompound c = new NbtCompound();
                c.putString("id", key);
                c.putByte("Count", (byte) 1);
                n.put("item", c);
                n.putString("item_display", "fixed");
            });
        }

        private void apply(Consumer<NbtCompound> f) {
            Entity e = entity();
            if (e == null) {
                e = Registries.ENTITY_TYPE.get(new Identifier(type)).create(world);
                if (e == null) return;
                e.refreshPositionAndAngles(x, y, z, 0f, 0f);
                e.addCommandTag("ll_label");
                NbtCompound n = e.writeNbt(new NbtCompound());
                n.putString("billboard", "center");
                if (scale != 1f) n.put("transformation", transform(scale));
                f.accept(n);
                e.readNbt(n);
                world.spawnEntity(e);
                id = e.getUuid();
            } else {
                NbtCompound n = e.writeNbt(new NbtCompound());
                f.accept(n);
                e.readNbt(n);
            }
        }
    }

    private static NbtCompound transform(float s) {
        NbtCompound t = new NbtCompound();
        t.put("translation", floats(0, 0, 0));
        t.put("left_rotation", floats(0, 0, 0, 1));
        t.put("scale", floats(s, s, s));
        t.put("right_rotation", floats(0, 0, 0, 1));
        return t;
    }

    private static NbtList floats(float... v) {
        NbtList l = new NbtList();
        for (float f : v) l.add(NbtFloat.of(f));
        return l;
    }
}
