"""Compile the real reload coordinator against controlled API stubs; no Gradle or game required."""
import pathlib
import subprocess
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[3]
STUBS = {
    "dev/xyat/kineticcore/api/event/KineticEventPriority.java": "package dev.xyat.kineticcore.api.event; public enum KineticEventPriority { NORMAL }",
    "dev/xyat/kineticcore/api/server/event/KineticServerEvents.java": """package dev.xyat.kineticcore.api.server.event;
import java.util.function.*; import net.minecraft.server.MinecraftServer; import net.minecraft.server.level.ServerPlayer;
import dev.xyat.kineticcore.api.event.KineticEventPriority;
public class KineticServerEvents {
 public enum TickPhase { END }
 public static Consumer<MinecraftServer> started, stopping;
 public static BiConsumer<MinecraftServer,ServerPlayer> sync;
 public static Consumer<MinecraftServer> tick;
 public static void onStarted(KineticEventPriority p, Consumer<MinecraftServer> c) { started=c; }
 public static void onStopping(KineticEventPriority p, Consumer<MinecraftServer> c) { stopping=c; }
 public static void onDatapackSync(KineticEventPriority p, BiConsumer<MinecraftServer,ServerPlayer> c) { sync=c; }
 public static void onTick(KineticEventPriority p, TickPhase phase, Consumer<MinecraftServer> c) { tick=c; }
}""",
    "dev/xyat/taczworkshop/TaczWorkshop.java": """package dev.xyat.taczworkshop; public class TaczWorkshop {
 public static final Logger LOGGER=new Logger(); public static class Logger {
 public void info(String s,Object... a) {} public void warn(String s,Object... a) {} public void error(String s,Object... a) {} }
}""",
    "dev/xyat/taczworkshop/network/TaczDataNetwork.java": """package dev.xyat.taczworkshop.network;
import net.minecraft.server.level.ServerPlayer; public class TaczDataNetwork {
 public static void sendDataList(ServerPlayer p) {} public static void completeDataBatchReload(ServerPlayer p,long b,int e) {}
 public static void failDataBatchReload(ServerPlayer p) {} }""",
    "dev/xyat/taczworkshop/server/TaczDataStore.java": "package dev.xyat.taczworkshop.server; public class TaczDataStore { public static void snapshot() {} }",
    "dev/xyat/taczworkshop/server/TaczDataReload.java": """package dev.xyat.taczworkshop.server;
import java.util.*; import java.util.concurrent.*; import net.minecraft.server.MinecraftServer;
public class TaczDataReload {
 public static final List<CompletableFuture<Void>> pending=new ArrayList<>(); public static int sends;
 static CompletableFuture<Void> reload(MinecraftServer s) { var f=new CompletableFuture<Void>(); pending.add(f); return f; }
 static void sync(MinecraftServer s) { sends++; }
}""",
    "net/minecraft/server/level/ServerPlayer.java": """package net.minecraft.server.level;
import java.util.UUID; import net.minecraft.server.MinecraftServer; public class ServerPlayer {
 public boolean hasPermissions(int n) { return true; } public MinecraftServer getServer() { return null; }
 public UUID getUUID() { return new UUID(0,1); } }""",
    "net/minecraft/server/MinecraftServer.java": """package net.minecraft.server;
import java.util.*; import java.util.concurrent.*; import net.minecraft.server.level.ServerPlayer;
public class MinecraftServer implements Executor {
 public final List<CompletableFuture<Void>> full=new ArrayList<>(); public List<String> selected=List.of("vanilla","pack:z","pack:a");
 public List<String> requested; public boolean throwReload; public void execute(Runnable r) { r.run(); }
 public CompletableFuture<Void> reloadResources(Collection<String> packs) { if(throwReload) throw new IllegalStateException("startup failure"); requested=new ArrayList<>(packs); var f=new CompletableFuture<Void>(); full.add(f); return f; }
 public Packs getPackRepository() { return new Packs(); } public class Packs { public Collection<String> getSelectedIds() { return selected; } }
 public Commands getCommands() { return new Commands(); } public Object createCommandSourceStack() { return this; }
 public class Commands { public void performPrefixedCommand(Object s,String c) { reloadResources(selected); } }
 public Players getPlayerList() { return new Players(); } public class Players {
 public List<ServerPlayer> getPlayers() { return List.of(); } public ServerPlayer getPlayer(UUID id) { return null; } }
}""",
}

HARNESS = """package dev.xyat.taczworkshop.server;
import net.minecraft.server.MinecraftServer; import dev.xyat.kineticcore.api.server.event.KineticServerEvents;
public class ReloadHarness {
 static int done, failed; static void check(boolean ok,String s) { if(!ok) throw new AssertionError(s); }
 static void save(MinecraftServer s) { TaczDataRuntime.applyTaczData(s,()->done++,()->failed++); }
 public static void main(String[] args) {
  var s=new MinecraftServer(); TaczDataRuntime.register(); KineticServerEvents.started.accept(s);
  if(args[0].equals("fallback-start-failure")) {
   s.throwReload=true; save(s); save(s); TaczDataReload.pending.get(0).completeExceptionally(new IllegalStateException("target"));
   check(failed==1 && done==0 && TaczDataReload.pending.size()==2,"synchronous fallback failure stranded/repeated receipt");
   TaczDataReload.pending.get(1).complete(null); check(done==1 && failed==1,"save after fallback startup failure"); return;
  }
  if(args[0].equals("target-timeout")) {
   save(s); for(int i=0;i<6000;i++) KineticServerEvents.tick.accept(s); save(s);
   check(failed==1 && TaczDataReload.pending.size()==1,"target timeout released active future");
   TaczDataReload.pending.get(0).complete(null);
   check(done==0 && failed==1 && TaczDataReload.pending.size()==2,"late target completion duplicated receipt or stranded queue");
   TaczDataReload.pending.get(1).complete(null); check(done==1 && failed==1,"target timeout receipt counts");
   return;
  }
  if(args[0].equals("target-restart")) {
   save(s); KineticServerEvents.stopping.accept(s); var fresh=new MinecraftServer(); KineticServerEvents.started.accept(fresh); save(fresh);
   KineticServerEvents.stopping.accept(s);
   TaczDataReload.pending.get(0).completeExceptionally(new IllegalStateException("old target"));
   check(s.full.isEmpty() && done==0 && failed==0,"old target launched fallback after stop");
   TaczDataReload.pending.get(1).complete(null); check(done==1,"new session lost target save"); return;
  }
  if(args[0].equals("reentrant")) {
   TaczDataRuntime.applyTaczData(s,()->{ done++; save(s); throw new IllegalStateException("callback"); },()->failed++);
   TaczDataReload.pending.get(0).complete(null);
   check(TaczDataReload.pending.size()==2,"throwing callback stranded its reentrant save");
   TaczDataReload.pending.get(1).complete(null); check(done==2 && failed==0,"reentrant receipts repeated"); return;
  }
  save(s); save(s); TaczDataReload.pending.get(0).completeExceptionally(new IllegalStateException("injected"));
  check(s.full.size()==1,"one fallback starts");
  check(TaczDataReload.pending.size()==1,"queued save overlapped running full reload");
  check(s.requested.equals(s.selected),"enabled pack order changed");
  KineticServerEvents.sync.accept(s,null);
  check(done==0,"datapack event acknowledged an unfinished future");
  if(args[0].equals("timeout")) {
   for(int i=0;i<6000;i++) KineticServerEvents.tick.accept(s);
   check(failed==1 && done==0,"timeout must fail active waiter once"); save(s);
   check(TaczDataReload.pending.size()==1,"timeout unlocked a running reload");
   s.full.get(0).complete(null);
   check(done==0 && failed==1,"late success repeated timed-out receipt");
   check(TaczDataReload.pending.size()==2,"queue not resumed after timed-out future ended");
   TaczDataReload.pending.get(1).complete(null);
   check(done==2 && failed==1,"queued saves not acknowledged once");
  } else if(args[0].equals("restart")) {
   KineticServerEvents.stopping.accept(s); var fresh=new MinecraftServer(); KineticServerEvents.started.accept(fresh); save(fresh);
   s.full.get(0).complete(null);
   check(done==0 && failed==0 && TaczDataReload.pending.size()==2,"old full callback crossed server epoch");
   TaczDataReload.pending.get(1).complete(null); check(done==1,"new world save did not finish");
  } else if(args[0].equals("failure")) {
   s.full.get(0).completeExceptionally(new IllegalArgumentException("full failure"));
   check(failed==1 && done==0,"full failure did not fail exactly once");
   check(TaczDataReload.pending.size()==2,"full failure stranded queued save");
   TaczDataReload.pending.get(1).complete(null); check(done==1 && failed==1,"failure changed next receipt");
  } else {
   s.full.get(0).complete(null); check(done==1 && failed==0,"fallback completion receipt");
   check(TaczDataReload.pending.size()==2,"queued save never resumed");
   TaczDataReload.pending.get(1).complete(null); check(done==2 && failed==0,"success receipts repeated/missing");
   TaczDataRuntime.reloadManagedData(s,true); check(s.full.size()==2,"manual reload behavior changed");
  }
  System.out.println("PASS "+args[0]);
 }
}"""


class ReloadSerializationTest(unittest.TestCase):
    def test_real_coordinator(self):
        with tempfile.TemporaryDirectory(prefix="tacz-reload-test-") as temp:
            root = pathlib.Path(temp)
            for name, text in STUBS.items():
                path = root / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(text, encoding="utf-8")
            harness = root / "dev/xyat/taczworkshop/server/ReloadHarness.java"
            harness.write_text(HARNESS, encoding="utf-8")
            output = root / "classes"
            output.mkdir()
            sources = [str(p) for p in root.rglob("*.java")]
            sources.append(str(ROOT / "src/main/java/dev/xyat/taczworkshop/server/TaczDataRuntime.java"))
            result = subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(output), *sources], capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            for scenario in ("success", "timeout", "failure", "restart", "target-timeout", "target-restart", "reentrant", "fallback-start-failure"):
                with self.subTest(scenario=scenario):
                    result = subprocess.run(["java", "-cp", str(output), "dev.xyat.taczworkshop.server.ReloadHarness", scenario], capture_output=True, text=True)
                    self.assertEqual(result.returncode, 0, result.stdout + result.stderr)


if __name__ == "__main__":
    unittest.main()
