package jp.luckylobby;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.*;

public final class GameManager {
    public static final Settings settings = new Settings();
    public static Game current;

    public static List<ServerPlayerEntity> lobbyParticipants() {
        List<ServerPlayerEntity> l = new ArrayList<>();
        if (Lobby.world == null) return l;
        for (ServerPlayerEntity p : Lobby.world.getPlayers()) if (!settings.isSpectator(p.getUuid())) l.add(p);
        return l;
    }

    /** 成功なら null、失敗ならエラーメッセージ。 */
    public static String start(MinecraftServer server) {
        if (current != null) return "§cすでにゲーム進行中です";
        if (!Util.luckyPresent()) return "§cLucky Block Mod(lucky:lucky_block)が見つかりません";
        List<ServerPlayerEntity> ps = lobbyParticipants();
        if (ps.isEmpty()) return "§c参加者がいません";
        Map<UUID, Integer> teams = settings.resolveTeams(ps);
        switch (settings.mode) {
            case ONEBLOCK -> { String e = OneBlockGame.precheck(server); if (e != null) return e; }
            case RACE -> { if (ps.size() > 8) return "§cレースは最大8人です(観戦に回してください)"; }
            case PVP -> {
                Set<Integer> groups = new HashSet<>();
                int solo = 1000;
                for (ServerPlayerEntity p : ps) { int t = teams.get(p.getUuid()); groups.add(t >= 0 ? t : solo++); }
                if (groups.size() < 2) return "§cPVPは2人(2チーム)以上必要です";
            }
            default -> {}
        }
        current = switch (settings.mode) {
            case ONEBLOCK -> new OneBlockGame(server, settings, ps, teams);
            case PVP -> new PvpGame(server, settings, ps, teams);
            case RACE -> new RaceGame(server, settings, ps, teams);
        };
        current.start();
        return null;
    }

    public static void tick() {
        if (current == null) return;
        current.tick();
        if (current.finished) current = null;
    }

    public static void stop() { if (current != null) current.forceEnd(); }

    public static void shutdown(MinecraftServer server) {
        if (current != null) { current.finish(); current = null; }
    }

    public static boolean isActiveParticipant(UUID id) {
        return current != null && !current.finished && current.players.containsKey(id)
                && current.players.get(id).alive;
    }

    public static boolean allowDeath(LivingEntity e, DamageSource src) {
        if (current != null && e instanceof ServerPlayerEntity p && current.players.containsKey(p.getUuid()))
            return current.onDeath(p, src);
        return true;
    }

    public static boolean allowDamage(LivingEntity e) {
        if (!(e instanceof ServerPlayerEntity p)) return true;
        if (p.getServerWorld() == Lobby.world) return false;
        if (current != null && current.players.containsKey(p.getUuid()) && current.state != Game.State.RUNNING) return false;
        return true;
    }

    public static void onDisconnect(ServerPlayerEntity p) {
        if (current != null) current.onDisconnect(p);
    }
}
