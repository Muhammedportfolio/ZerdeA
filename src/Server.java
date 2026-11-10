import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

class Server extends Util {
   final GoogleLink google;
   final Service svc;
   final Database db;
   final HttpServer http;
   final Map<String, Server.Session> sessions = new ConcurrentHashMap<>();
   final Map<String, Server.Rate> rates = new ConcurrentHashMap<>();
   final ExecutorService workers = Executors.newFixedThreadPool(12);
   final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();

   Server(Path var1, String var2, int var3) throws Exception {
      this.db = new Database(var1);
      if (this.db.postgres && this.db.all("users").isEmpty()) {
         this.db.close();
         throw new IllegalStateException("База Supabase пока пуста. Сначала перенесите проверенную резервную копию H2; записи и учётные записи автоматически не создаются.");
      }
      this.svc = new Service(this.db);
      this.google = new GoogleLink(this.svc.ai);

      for (Map var5 : this.db.all("attempts")) {
         if (s(var5, "status").equals("checking")) {
            var5.put("status", "active");
            var5.put("checkError", "Проверка была прервана перезапуском сервера");
            this.db.put("attempts", var5);
         }
      }

      this.svc.expire();
      this.http = HttpServer.create(new InetSocketAddress(var2, var3), 0);
      this.http.createContext("/", this::handle);
      this.http.setExecutor(this.workers);
      this.http.start();
      this.timer.scheduleAtFixedRate(() -> {
         try {
            this.svc.expire();
            this.sessions.entrySet().removeIf(var0 -> var0.getValue().expires < now());
         } catch (Exception var2x) {
            System.err.println("Timer: " + var2x.getClass().getSimpleName());
         }
      }, 5L, 5L, TimeUnit.SECONDS);
   }

   Map<String, Object> network() throws Exception {
      ArrayList var1 = new ArrayList();
      boolean var2 = this.http.getAddress().getAddress().isAnyLocalAddress();
      if (var2) {
         for (NetworkInterface var4 : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (var4.isUp() && !var4.isLoopback()) {
               for (InetAddress var6 : Collections.list(var4.getInetAddresses())) {
                  if (var6 instanceof Inet4Address && var6.isSiteLocalAddress()) {
                     var1.add("http://" + var6.getHostAddress() + ":" + this.http.getAddress().getPort());
                  }
               }
            }
         }
      }

      return m("shared", var2, "addresses", var1, "database", this.db.provider, "version", "2.3.0");
   }

   static String cookie(HttpExchange var0, String var1) {
      String var2 = var0.getRequestHeaders().getFirst("Cookie");
      if (var2 != null) {
         for (String var6 : var2.split(";")) {
            String[] var7 = var6.strip().split("=", 2);
            if (var7.length == 2 && var7[0].equals(var1)) {
               return var7[1];
            }
         }
      }

      return "";
   }

   void handle(HttpExchange var1) throws IOException {
      try {
         String var2 = var1.getRequestURI().getPath();
         if (!var2.startsWith("/api/")) {
            String var26 = switch (var2) {
               case "/", "/index.html" -> "index.html";
               case "/app.js" -> "app.js";
               case "/style.css" -> "style.css";
               default -> null;
            };
            if (var26 == null) {
               send(var1, 404, m("error", "Не найдено"));
               return;
            }

            try (InputStream var27 = Server.class.getResourceAsStream("/web/" + var26)) {
               byte[] var29 = var27 != null ? var27.readAllBytes() : Files.readAllBytes(Path.of("web", var26));
               bytes(
                  var1,
                  200,
                  var26.endsWith("js") ? "text/javascript; charset=utf-8" : (var26.endsWith("css") ? "text/css; charset=utf-8" : "text/html; charset=utf-8"),
                  var29,
                  null
               );
            }

            return;
         }

         String var3 = var1.getRequestHeaders().getFirst("Origin");
         String var4 = var1.getRequestHeaders().getFirst("Host");
         if (var3 != null && !var3.equals("http://" + var4) && !var3.equals("https://" + var4)) {
            throw new SecurityException("Недопустимый источник запроса");
         }

         boolean var5 = var1.getRequestMethod().equals("POST");
         Map var6 = m();
         if (var5) {
            check(
               var1.getRequestHeaders().getFirst("Content-Type") != null && var1.getRequestHeaders().getFirst("Content-Type").startsWith("application/json"),
               "Нужен application/json"
            );
            int var7 = var2.equals("/api/restore") ? 100000000 : 18000000;
            byte[] var8 = var1.getRequestBody().readNBytes(var7 + 1);
            check(var8.length <= var7, "Слишком большой запрос");
            var6 = obj(Json.parse(new String(var8, StandardCharsets.UTF_8)));
         }

         if (var2.equals("/api/login") && var5) {
            String var31 = var1.getRemoteAddress().getAddress().getHostAddress();
            Server.Rate var33 = this.rates.computeIfAbsent(var31, var0 -> new Server.Rate());
            synchronized (var33) {
               if (now() > var33.until) {
                  var33.count = 0;
                  var33.until = now() + 60000L;
               }

               check(var33.count < 8, "Слишком много попыток. Повторите через минуту.");
               var33.count++;
            }

            Map var34 = this.svc.login(var6);
            synchronized (var33) {
               var33.count = 0;
            }

            String var35 = id() + id();
            String var36 = id();
            this.sessions.put(var35, new Server.Session(s(var34, "id"), var36, now() + 43200000L));
            var1.getResponseHeaders()
               .set("Set-Cookie", "edutest=" + var35 + "; HttpOnly; SameSite=Strict; Path=/" + (Boolean.getBoolean("edutest.secureCookie") ? "; Secure" : ""));
            send(var1, 200, m("user", publicUser(var34), "csrf", var36));
            return;
         }

         String var30 = cookie(var1, "edutest");
         Server.Session var32 = this.sessions.get(var30);
         if (var32 == null || var32.expires < now()) {
            send(var1, 401, m("error", "Войдите в аккаунт"));
            return;
         }

         Map var9 = this.svc.user(var32.user);
         if (var5 && !var32.csrf.equals(var1.getRequestHeaders().getFirst("X-CSRF"))) {
            throw new SecurityException("Обновите страницу и повторите действие");
         }

         if (!var5) {
            switch (var2) {
               case "/api/state":
                  Map var38 = this.svc.state(var9);
                  var38.put("csrf", var32.csrf);
                  if (s(var9, "role").equals("teacher")) {
                     var38.put("network", this.network());
                  }

                  if (s(var9, "role").equals("teacher")) {
                     var38.put("google", this.google.status(s(var9, "id")));
                  }

                  send(var1, 200, var38);
                  return;
               case "/api/backup":
                  teacher(var9);
                  bytes(var1, 200, "application/json", Json.write(this.db.backup()).getBytes(StandardCharsets.UTF_8), "ZerdeAI-backup.json");
                  return;
               case "/api/export":
                  teacher(var9);
                  ArrayList var37 = new ArrayList();

                  for (Object var42 : list(this.svc.state(var9), "results")) {
                     if (s(obj(var42), "status").equals("submitted")) {
                        var37.add(obj(var42));
                     }
                  }

                  bytes(var1, 200, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", Excel.export(var37), "ZerdeAI-results.xlsx");
                  return;
               default:
                  throw new IllegalArgumentException("Неизвестный запрос");
            }
         }
         Object var10 = switch (var2) {
            case "/api/logout" -> {
               this.sessions.remove(var30);
               var1.getResponseHeaders().set("Set-Cookie", "edutest=; Max-Age=0; HttpOnly; SameSite=Strict; Path=/");
               yield m("ok", true);
            }
            case "/api/users/save" -> {
               Map var40 = this.svc.usersSave(var9, var6);
               this.sessions.entrySet().removeIf(var1x -> var1x.getValue().user.equals(s(var40, "id")));
               yield var40;
            }
            case "/api/profile" -> this.svc.profile(var9, var6);
            case "/api/groups/save" -> this.svc.groups(var9, var6);
            case "/api/chat/open" -> this.svc.chatOpen(var9, var6);
            case "/api/chat/send" -> this.svc.chatSend(var9, var6);
            case "/api/chat/read" -> this.svc.chatRead(var9, var6);
            case "/api/chat/attachment/upload" -> this.svc.chatAttachmentUpload(var9, var6);
            case "/api/chat/attachment/download" -> this.svc.chatAttachmentDownload(var9, var6);
            case "/api/course/save" -> this.svc.courseSave(var9, var6);
            case "/api/course/progress" -> this.svc.courseProgress(var9, var6);
            case "/api/tutor/new" -> this.svc.tutorNew(var9);
            case "/api/tutor/ask" -> this.svc.tutorAsk(var9, var6);
            case "/api/tutor/delete" -> this.svc.tutorDelete(var9, var6);
            case "/api/material/upload" -> this.svc.upload(var9, var6);
            case "/api/material/update" -> this.svc.materialUpdate(var9, var6);
            case "/api/material/download" -> {
               Map var39 = this.svc.owned(var9, "materials", req(var6, "id", 60));
               yield m("filename", s(var39, "filename"), "data", s(var39, "data"));
            }
            case "/api/task/generate" -> this.svc.generate(var9, var6);
            case "/api/task/save" -> this.svc.saveTask(var9, var6);
            case "/api/task/trash" -> this.svc.trashTask(var9, var6, false);
            case "/api/task/restore" -> this.svc.trashTask(var9, var6, true);
            case "/api/task/close" -> this.svc.closeTask(var9, var6);
            case "/api/attempt/start" -> this.svc.start(var9, var6);
            case "/api/attempt/get" -> this.svc.attemptPublic(var9, this.svc.getAttempt(var9, req(var6, "id", 60)));
            case "/api/attempt/save" -> this.svc.autosave(var9, var6);
            case "/api/attempt/submit" -> this.svc.submit(var9, var6);
            case "/api/attempt/review" -> this.svc.review(var9, var6);
            case "/api/grade" -> this.svc.correctGrade(var9, var6);
            case "/api/google/setup" -> {
               teacher(var9);
               check(var1.getRemoteAddress().getAddress().isLoopbackAddress(), "Настраивайте Google на ПК-сервере через http://localhost:8080.");
               this.google.configure(s(var9, "id"), var6);
               yield this.google.status(s(var9, "id"));
            }
            case "/api/google/start" -> {
               teacher(var9);
               check(var1.getRemoteAddress().getAddress().isLoopbackAddress(), "Привязывайте Google на ПК-сервере через http://localhost:8080.");
               yield this.google.start(s(var9, "id"));
            }
            case "/api/google/status" -> {
               teacher(var9);
               yield this.google.status(s(var9, "id"));
            }
            case "/api/google/cancel" -> {
               teacher(var9);
               this.google.cancel(s(var9, "id"));
               yield m("ok", true);
            }
            case "/api/ai/config" -> {
               teacher(var9);
               this.svc.ai.configure(s(var9, "id"), var6);
               yield this.svc.ai.publicConfig(s(var9, "id"));
            }
            case "/api/ai/models" -> {
               teacher(var9);
               yield m("models", this.svc.ai.models(s(var9, "id")));
            }
            case "/api/ai/test" -> {
               teacher(var9);
               yield this.svc.ai.ask(s(var9, "id"), "Верни JSON {status:строка ok}.");
            }
            case "/api/restore" -> {
               teacher(var9);
               check(password(var9, req(var6, "password", 128)), "Неверный пароль преподавателя");
               Map var13 = obj(var6.get("backup"));
               synchronized (this.svc) {
                  Files.writeString(this.db.home.resolve("before-restore-" + now() + ".json"), Json.write(this.db.backup()));
                  this.db.restore(var13);
                  this.sessions.clear();

                  for (String var16 : new ArrayList<>(this.google.flows.keySet())) {
                     this.google.cancel(var16);
                  }

                  this.svc.ai.clear();
               }

               yield m("ok", true);
            }
            default -> throw new IllegalArgumentException("Неизвестная операция");
         };
         send(var1, 200, var10);
      } catch (SecurityException var23) {
         send(var1, 403, m("error", var23.getMessage()));
      } catch (IllegalArgumentException var24) {
         send(var1, 400, m("error", var24.getMessage()));
      } catch (Exception var25) {
         var25.printStackTrace();
         send(var1, 500, m("error", "Операция не завершена. Проверьте параметры и журнал сервера."));
      }
   }

   static void send(HttpExchange var0, int var1, Object var2) throws IOException {
      bytes(var0, var1, "application/json; charset=utf-8", Json.write(var2).getBytes(StandardCharsets.UTF_8), null);
   }

   static void bytes(HttpExchange var0, int var1, String var2, byte[] var3, String var4) throws IOException {
      var0.getResponseHeaders().set("Content-Type", var2);
      var0.getResponseHeaders().set("Cache-Control", "no-store");
      var0.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
      var0.getResponseHeaders()
         .set(
            "Content-Security-Policy",
            "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; connect-src 'self'; frame-ancestors 'none'"
         );
      if (var4 != null) {
         var0.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + var4 + "\"");
      }

      var0.sendResponseHeaders(var1, var3.length);
      var0.getResponseBody().write(var3);
      var0.close();
   }

   void stop() throws Exception {
      this.google.close();
      this.timer.shutdownNow();
      this.http.stop(0);
      this.workers.shutdownNow();
      this.db.close();
   }

   public static void main(String[] var0) throws Exception {
      Path var1 = Path.of(System.getProperty("edutest.home", Path.of(System.getProperty("user.home"), "EduTestSuite2").toString()));
      String var2 = System.getProperty("edutest.host", "127.0.0.1");
      int var3 = Integer.getInteger("edutest.port", 8080);
      Server var4 = new Server(var1, var2, var3);
      Runtime.getRuntime().addShutdownHook(new Thread(() -> {
         try {
            var4.stop();
         } catch (Exception var2x) {
         }
      }));
      System.out.println("Zerde AI: http://localhost:" + var3 + "\nBind: " + var2 + "\nDatabase: " + var1 + "\nLAN: " + Json.write(var4.network()));
   }

   static class Rate {
      int count;
      long until;
   }

   record Session(String user, String csrf, long expires) {
   }
}
