package jp.luckylobby;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;

public class LuckyLobbyMod implements ModInitializer {
    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(Lobby::init);
        ServerLifecycleEvents.SERVER_STOPPING.register(GameManager::shutdown);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            GameManager.tick();
            Lobby.tick(server);
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> Lobby.onJoin(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> GameManager.onDisconnect(handler.player));
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((e, src, amount) -> GameManager.allowDamage(e));
        ServerLivingEntityEvents.ALLOW_DEATH.register((e, src, amount) -> GameManager.allowDeath(e, src));

        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world.isClient || hand != Hand.MAIN_HAND || world != Lobby.world || !(player instanceof ServerPlayerEntity sp))
                return ActionResult.PASS;
            return Ui.click(hit.getBlockPos(), sp) ? ActionResult.SUCCESS : ActionResult.PASS;
        });

        CommandRegistrationCallback.EVENT.register((d, access, env) -> {
            var op = (java.util.function.Predicate<net.minecraft.server.command.ServerCommandSource>) s -> s.hasPermissionLevel(2) || (s.getPlayer() != null && Util.isAdmin(s.getPlayer()));
            d.register(CommandManager.literal("lucky")
                .then(CommandManager.literal("coins")
                    .executes(c -> {
                        ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
                        p.sendMessage(Text.literal("§e所持コイン: §f" + Coins.get(p.getServer(), p.getUuid())));
                        return 1; })
                    .then(CommandManager.literal("give").requires(op)
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                            .then(CommandManager.argument("amount", IntegerArgumentType.integer(1))
                                .executes(c -> {
                                    ServerPlayerEntity t = EntityArgumentType.getPlayer(c, "player");
                                    Coins.add(c.getSource().getServer(), t.getUuid(), IntegerArgumentType.getInteger(c, "amount"));
                                    c.getSource().sendFeedback(() -> Text.literal("コインを付与しました"), true);
                                    return 1; })))))
                .then(CommandManager.literal("lobby").executes(c -> {
                    ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
                    if (GameManager.isActiveParticipant(p.getUuid())) p.sendMessage(Text.literal("§cゲーム中は戻れません"));
                    else Lobby.sendToLobby(p);
                    return 1; }))
                .then(CommandManager.literal("rebuild").requires(op).executes(c -> { Lobby.build(c.getSource().getServer()); return 1; }))
                .then(CommandManager.literal("stop").requires(op).executes(c -> { GameManager.stop(); return 1; })));
        });
    }
}
