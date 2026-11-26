package jp.luckylobby;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import xyz.nucleoid.fantasy.Fantasy;
import xyz.nucleoid.fantasy.RuntimeWorldConfig;
import xyz.nucleoid.fantasy.RuntimeWorldHandle;

import java.util.*;

public abstract class Game {
    public enum State { COUNTDOWN, RUNNING, ENDING }

    public static class PInfo {
        public UUID id; public String name; public int team = -1;
        public boolean alive = true; public int kills;
        public Vec3d spawn = Vec3d.ZERO; public float yaw;
    }

    protected final MinecraftServer server;
    protected final Settings s;
    public final Map<UUID, PInfo> players = new LinkedHashMap<>();
    protected final List<RuntimeWorldHandle> handles = new ArrayList<>();
    public ServerWorld world;
    public State state = State.COUNTDOWN;
    protected int ticks, countdown = 200, endTimer;
    public boolean finished;

    protected Game(MinecraftServer server, Settings s, List<ServerPlayerEntity> ps, Map<UUID, Integer> teams) {
        this.server = server; this.s = s;
        for (ServerPlayerEntity p : ps) {
            PInfo i = new PInfo();
            i.id = p.getUuid(); i.name = p.getName().getString(); i.team = teams.getOrDefault(i.id, -1);
            players.put(i.id, i);
        }
    }

    public abstract String name();
    protected abstract ServerWorld createWorld();
    protected abstract void build();
    protected abstract void assignSpawns();
    protected abstract void giveKit(ServerPlayerEntity p, PInfo i);
    protected void onStart() {}
    protected void onTick() {}
    public boolean onDeath(ServerPlayerEntity v, DamageSource src) { return true; }
    public void onDisconnect(ServerPlayerEntity p) {}

    protected ServerWorld open(RuntimeWorldConfig cfg) {
        RuntimeWorldHandle h = Fantasy.get(server).openTemporaryWorld(cfg);
        handles.add(h);
        return h.asWorld();
    }

    protected void cmd(String c) {
        server.getCommandManager().executeWithPrefix(server.getCommandSource().withSilent(), c);
    }

    public ServerPlayerEntity online(PInfo i) { return server.getPlayerManager().getPlayer(i.id); }

    public List<ServerPlayerEntity> onlineAll() {
        List<ServerPlayerEntity> l = new ArrayList<>();
        for (PInfo i : players.values()) { ServerPlayerEntity p = online(i); if (p != null) l.add(p); }
        return l;
    }

    public void start() {
        world = createWorld();
        build();
        assignSpawns();
        setupTeams();
        for (PInfo i : players.values()) {
            ServerPlayerEntity p = online(i);
            if (p == null) { i.alive = false; continue; }
            Lobby.resetPlayer(p);
            p.changeGameMode(GameMode.SURVIVAL);
            p.teleport(world, i.spawn.x, i.spawn.y, i.spawn.z, i.yaw, 0f);
            p.setSpawnPoint(world.getRegistryKey(), BlockPos.ofFloored(i.spawn), i.yaw, true, false);
            giveKit(p, i);
            Util.title(p, "§6" + name(), "§7まもなく開始…", 5, 40, 10);
        }
    }

    private void setupTeams() {
        if (s.teamCount <= 0) return;
        for (int t = 0; t < s.teamCount; t++) {
            cmd("team remove ll_t" + t);
            cmd("team add ll_t" + t);
            cmd("team modify ll_t" + t + " color " + Settings.TEAM_CMD_COLORS[t]);
            cmd("team modify ll_t" + t + " friendlyFire false");
        }
        for (PInfo i : players.values()) if (i.team >= 0) cmd("team join ll_t" + i.team + " " + i.name);
    }

    public final void tick() {
        ticks++;
        switch (state) {
            case COUNTDOWN -> {
                countdown--;
                for (PInfo i : players.values()) {
                    ServerPlayerEntity p = online(i);
                    if (p == null) continue;
                    if (p.squaredDistanceTo(i.spawn) > 0.5) p.requestTeleport(i.spawn.x, i.spawn.y, i.spawn.z);
                    if (countdown > 0 && countdown % 20 == 0 && countdown <= 100)
                        Util.title(p, "§e" + (countdown / 20), "§7準備！", 0, 15, 5);
                }
                if (countdown <= 0) {
                    state = State.RUNNING;
                    for (ServerPlayerEntity p : onlineAll()) Util.title(p, "§a§lSTART!", "§7" + name(), 0, 30, 10);
                    onStart();
                }
            }
            case RUNNING -> onTick();
            case ENDING -> { if (--endTimer <= 0) finish(); }
        }
    }

    public void end(String msg) {
        if (state == State.ENDING) return;
        state = State.ENDING;
        endTimer = 200;
        for (ServerPlayerEntity p : allInWorlds()) {
            p.sendMessage(Util.plain("§6=========================="));
            p.sendMessage(Util.plain(msg));
            p.sendMessage(Util.plain("§710秒後にロビーへ戻ります"));
            p.sendMessage(Util.plain("§6=========================="));
        }
    }

    public void forceEnd() { end("§c管理者によりゲームが終了されました"); endTimer = 40; }

    protected List<ServerPlayerEntity> allInWorlds() {
        List<ServerPlayerEntity> l = new ArrayList<>();
        for (RuntimeWorldHandle h : handles) l.addAll(h.asWorld().getPlayers());
        return l;
    }

    /** ロビーへ戻してワールドを削除。 */
    public void finish() {
        if (finished) return;
        for (ServerPlayerEntity p : allInWorlds()) Lobby.sendToLobby(p);
        for (PInfo i : players.values()) {
            ServerPlayerEntity p = online(i);
            if (p != null && !Lobby.isLobbyPlayer(p)) Lobby.sendToLobby(p);
        }
        for (int t = 0; t < Math.max(s.teamCount, 0); t++) cmd("team remove ll_t" + t);
        for (RuntimeWorldHandle h : handles) h.delete();
        handles.clear();
        finished = true;
    }
}
