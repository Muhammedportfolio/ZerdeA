import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BooleanSupplier;

public class TunnelTest {
   static int checks;

   static void ok(boolean var0, String var1) {
      if (!var0) {
         throw new AssertionError(var1);
      } else {
         checks++;
         System.out.println("PASS " + var1);
      }
   }

   static void await(BooleanSupplier var0) throws Exception {
      long var1 = System.nanoTime() + 5000000000L;

      while (!var0.getAsBoolean()) {
         if (System.nanoTime() > var1) {
            throw new AssertionError("timeout");
         }

         Thread.sleep(20L);
      }
   }

   static List<String> command(String var0, Path var1) {
      return List.of(
         Path.of(System.getProperty("java.home"), "bin", "java").toString(),
         "-cp",
         System.getProperty("java.class.path"),
         "TunnelTest$Child",
         var0,
         var1.toString()
      );
   }

   public static void main(String[] var0) throws Exception {
      Path var1 = Files.createTempFile("tunnel-pid", ".txt");

      try (Tunnel var2 = new Tunnel(command("ready", var1), var0x -> {})) {
         var2.start();
         await(() -> !var2.snapshot().url().isEmpty());
         ok(var2.snapshot().state() == Tunnel.State.CONNECTING, "URL alone is not a connected tunnel");
         await(() -> var2.snapshot().state() == Tunnel.State.READY);
         ok(var2.snapshot().url().equals("https://test-zerde-link.trycloudflare.com"), "registered connection exposes exact HTTPS URL");
         long var3 = Long.parseLong(Files.readString(var1));
         var2.accept(1L, "Retrying connection in up to 1s");
         ok(var2.snapshot().state() == Tunnel.State.CONNECTING, "connection loss clears ready state");
         var2.start();
         ok(var2.snapshot().url().isEmpty(), "retry clears previous address");
         await(() -> var2.snapshot().state() == Tunnel.State.READY);
         long var5 = Long.parseLong(Files.readString(var1));
         await(() -> !ProcessHandle.of(var3).map(ProcessHandle::isAlive).orElse(false));
         ok(var3 != var5, "retry replaces only owned child process");
         var2.close();
         await(() -> !ProcessHandle.of(var5).map(ProcessHandle::isAlive).orElse(false));
         ok(var2.snapshot().state() == Tunnel.State.STOPPED && var2.snapshot().url().isEmpty(), "close kills child and removes link");
         var2.start();
         ok(var2.snapshot().state() == Tunnel.State.STOPPED, "closed owner cannot spawn another child");
      }

      try (Tunnel var13 = new Tunnel(command("exit", var1), var0x -> {})) {
         var13.start();
         await(() -> var13.snapshot().state() == Tunnel.State.ERROR);
         ok(var13.snapshot().url().isEmpty(), "early process exit reports failure with no stale URL");
      }

      try (Tunnel var14 = new Tunnel(List.of("/not-installed/zerde-cloudflared"), var0x -> {})) {
         var14.start();
         await(() -> var14.snapshot().state() == Tunnel.State.ERROR);
         ok(!var14.snapshot().message().isBlank(), "missing binary produces useful error");
      }

      ok(!Tunnel.ADDRESS.matcher("https://hello.trycloudflare.com.evil.example").find(), "rejects lookalike external domain");
      ok(
         Tunnel.command(Path.of("folder with spaces"), 8080).get(0).equals(Path.of("folder with spaces/tools/cloudflared.exe").toString()),
         "paths with spaces remain a single process argument"
      );
      Files.deleteIfExists(var1);
      System.out.println("TOTAL " + checks + " tunnel checks passed");
   }

   public static class Child {
      public static void main(String[] var0) throws Exception {
         Files.writeString(Path.of(var0[1]), Long.toString(ProcessHandle.current().pid()));
         System.out.println("INF | https://test-zerde-link.trycloudflare.com |");
         System.out.flush();
         Thread.sleep(250L);
         if (!var0[0].equals("exit")) {
            System.out.println("INF Registered tunnel connection connIndex=0");
            System.out.flush();
            Thread.sleep(60000L);
         }
      }
   }
}
