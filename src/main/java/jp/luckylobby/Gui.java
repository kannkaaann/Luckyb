package jp.luckylobby;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/** クライアントMod不要のチェストGUI。アイテムは一切動かせない。 */
public final class Gui extends GenericContainerScreenHandler {
    public interface Logic {
        void init(Gui g);
        void click(Gui g, int slot, int button, ServerPlayerEntity p);
        default void tick(Gui g) {}
        default void closed(Gui g) {}
    }

    private static final Set<Gui> OPEN = new HashSet<>();

    public final SimpleInventory inv;
    public final Logic logic;
    public final ServerPlayerEntity viewer;
    public final int rows;

    private Gui(int id, PlayerInventory pi, int rows, SimpleInventory inv, Logic logic) {
        super(type(rows), id, pi, inv, rows);
        this.inv = inv; this.logic = logic; this.rows = rows;
        this.viewer = (ServerPlayerEntity) pi.player;
    }

    private static ScreenHandlerType<GenericContainerScreenHandler> type(int rows) {
        return switch (rows) {
            case 1 -> ScreenHandlerType.GENERIC_9X1;
            case 2 -> ScreenHandlerType.GENERIC_9X2;
            case 3 -> ScreenHandlerType.GENERIC_9X3;
            case 4 -> ScreenHandlerType.GENERIC_9X4;
            case 5 -> ScreenHandlerType.GENERIC_9X5;
            default -> ScreenHandlerType.GENERIC_9X6;
        };
    }

    public static void open(ServerPlayerEntity p, Text title, int rows, Logic logic) {
        p.openHandledScreen(new SimpleNamedScreenHandlerFactory((id, pi, pl) -> {
            Gui g = new Gui(id, pi, rows, new SimpleInventory(rows * 9), logic);
            OPEN.add(g);
            logic.init(g);
            return g;
        }, title));
    }

    public void set(int slot, ItemStack s) { if (slot >= 0 && slot < rows * 9) inv.setStack(slot, s); }

    public void fill() {
        for (int i = 0; i < rows * 9; i++) inv.setStack(i, Util.item(Items.GRAY_STAINED_GLASS_PANE, " "));
    }

    public void close() { viewer.closeHandledScreen(); }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        if (slotIndex >= 0 && slotIndex < rows * 9 && actionType != SlotActionType.QUICK_CRAFT) {
            logic.click(this, slotIndex, button, (ServerPlayerEntity) player);
        }
        this.syncState();
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) { return ItemStack.EMPTY; }

    @Override
    public boolean canUse(PlayerEntity player) { return true; }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        OPEN.remove(this);
        logic.closed(this);
    }

    public static void tickAll() {
        for (Gui g : new ArrayList<>(OPEN)) {
            if (g.viewer.currentScreenHandler != g) { OPEN.remove(g); continue; }
            g.logic.tick(g);
        }
    }
}
