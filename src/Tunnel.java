import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class Tunnel implements AutoCloseable {
   static final Pattern ADDRESS = Pattern.compile("https://[a-z0-9]+(?:-[a-z0-9]+)*\\.trycloudflare\\.com(?=[\\s/|]|$)");
   private final List<String> command;
   private final Consumer<Tunnel.View> listener;
   private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(var0 -> {
      Thread var1x = new Thread(var0, "tunnel-timeout");
      var1x.setDaemon(true);
      return var1x;
   });
   private Tunnel.View view = new Tunnel.View(Tunnel.State.STOPPED, "", "Туннель остановлен");
   private Process process;
   private long generation;
   private boolean closed;
   private boolean connected;
   private ScheduledFuture<?> timeout;

   Tunnel(List<String> var1, Consumer<Tunnel.View> var2) {
      this.command = List.copyOf(var1);
      this.listener = var2;
   }

   static List<String> command(Path var0, int var1) {
      return List.of(
         var0.resolve("tools/cloudflared.exe").toString(),
         "tunnel",
         "--config",
         var0.resolve("tools/quick-tunnel.yml").toString(),
         "--no-autoupdate",
         "--url",
         "http://127.0.0.1:" + var1
      );
   }

   synchronized Tunnel.View snapshot() {
      return this.view;
   }

   private void update(Tunnel.State var1, String var2, String var3) {
      this.view = new Tunnel.View(var1, var2, var3);
      this.listener.accept(this.view);
   }

   synchronized void start() {
      if (!this.closed) {
         long var1 = ++this.generation;
         this.connected = false;
         if (this.timeout != null) {
            this.timeout.cancel(false);
         }

         if (this.process != null) {
            this.process.destroyForcibly();
            this.process = null;
         }

         this.update(Tunnel.State.CONNECTING, "", "Подключение к интернету…");
         this.timeout = this.timer.schedule(() -> this.expired(var1), 90L, TimeUnit.SECONDS);
         Thread var3 = new Thread(() -> this.run(var1), "zerde-tunnel");
         var3.setDaemon(true);
         var3.start();
      }
   }

   private void run(long var1) {
      Process var3 = null;

      try {
         try {
            synchronized (this) {
               if (this.closed || var1 != this.generation) {
                  return;
               }

               var3 = new ProcessBuilder(this.command).redirectErrorStream(true).start();
               this.process = var3;
            }

            String var5;
            try (BufferedReader var4 = new BufferedReader(new InputStreamReader(var3.getInputStream(), StandardCharsets.UTF_8))) {
               while ((var5 = var4.readLine()) != null) {
                  this.accept(var1, var5);
               }
            }

            int var18 = var3.waitFor();
            this.fail(var1, "Туннель завершился (код " + var18 + "). Нажмите «Повторить подключение».");
         } catch (Exception var16) {
            this.fail(
               var1,
               var16 instanceof IOException
                  ? "Не удалось запустить туннель. Проверьте интернет и наличие cloudflared в папке программы."
                  : "Соединение прервано. Нажмите «Повторить подключение»."
            );
         }
      } finally {
         if (var3 != null && var3.isAlive()) {
            var3.destroyForcibly();
         }
      }
   }

   synchronized void accept(long var1, String var3) {
      if (!this.closed && var1 == this.generation && this.view.state() != Tunnel.State.ERROR) {
         Matcher var4 = ADDRESS.matcher(var3);
         String var5 = this.view.url();
         if (var4.find()) {
            var5 = var4.group();
         }

         if (var3.contains("Registered tunnel connection")) {
            this.connected = true;
         }

         if (var3.contains("Retrying connection") || var3.contains("Unregistered tunnel connection") || var3.contains("failed to serve tunnel connection")) {
            this.connected = false;
         }

         if (this.connected && !var5.isEmpty()) {
            if (this.timeout != null) {
               this.timeout.cancel(false);
            }

            this.update(Tunnel.State.READY, var5, "Туннель подключён — внешняя ссылка:");
         } else {
            this.update(Tunnel.State.CONNECTING, var5, "Подключение… Ссылка будет доступна после соединения.");
            if (this.timeout == null || this.timeout.isDone()) {
               this.timeout = this.timer.schedule(() -> this.expired(var1), 90L, TimeUnit.SECONDS);
            }
         }
      }
   }

   private synchronized void expired(long var1) {
      if (!this.closed && var1 == this.generation && this.view.state() != Tunnel.State.READY) {
         this.fail(var1, "Подключение не установлено за 90 секунд. Проверьте интернет и нажмите «Повторить подключение».");
         if (this.process != null) {
            this.process.destroyForcibly();
         }
      }
   }

   private synchronized void fail(long var1, String var3) {
      if (!this.closed && var1 == this.generation && this.view.state() != Tunnel.State.ERROR) {
         if (this.timeout != null) {
            this.timeout.cancel(false);
         }

         this.update(Tunnel.State.ERROR, "", var3);
      }
   }

   @Override
   public synchronized void close() {
      if (!this.closed) {
         this.closed = true;
         this.generation++;
         if (this.timeout != null) {
            this.timeout.cancel(false);
         }

         if (this.process != null) {
            this.process.destroyForcibly();
         }

         this.timer.shutdownNow();
         this.update(Tunnel.State.STOPPED, "", "Туннель остановлен");
      }
   }

   static enum State {
      CONNECTING,
      READY,
      ERROR,
      STOPPED;
   }

   record View(Tunnel.State state, String url, String message) {
   }
}
