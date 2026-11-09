import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AdminConsole {
   private final Database db;
   private final int adminPort = Integer.getInteger("zerde.admin.port", 8082);
   private final HttpServer http;
   private final Map<String, Long> sessions = new ConcurrentHashMap<>();
   private final SecureRandom random = new SecureRandom();
   private final ExecutorService pool = Executors.newFixedThreadPool(4);

   AdminConsole(Database var1) throws Exception {
      this.db = var1;
      this.http = HttpServer.create(new InetSocketAddress("127.0.0.1", this.adminPort), 0);
      this.http.createContext("/", this::handle);
      this.http.setExecutor(this.pool);
      this.http.start();
   }

   void stop() {
      this.http.stop(0);
      this.pool.shutdownNow();
   }

   private String token() {
      byte[] var1 = new byte[32];
      this.random.nextBytes(var1);
      return Base64.getUrlEncoder().withoutPadding().encodeToString(var1);
   }

   private static String val(Map<String, Object> var0, String var1) {
      Object var2 = var0.get(var1);
      return var2 == null ? "" : String.valueOf(var2).trim();
   }

   private static String escape(Object var0) {
      return String.valueOf(var0).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
   }

   private static Map<String, Object> map(Object... var0) {
      LinkedHashMap var1 = new LinkedHashMap();

      for (byte var2 = 0; var2 < var0.length; var2 += 2) {
         var1.put(String.valueOf(var0[var2]), var0[var2 + 1]);
      }

      return var1;
   }

   private static String hash(String var0, String var1) throws Exception {
      return Util.hash(var0, var1);
   }

   private String cookie(HttpExchange var1) {
      String var2 = var1.getRequestHeaders().getFirst("Cookie");
      if (var2 == null) {
         return "";
      } else {
         for (String var6 : var2.split(";")) {
            var6 = var6.trim();
            if (var6.startsWith("za_session=")) {
               return var6.substring(11);
            }
         }

         return "";
      }
   }

   private boolean auth(HttpExchange var1) {
      String var2 = this.cookie(var1);
      Long var3 = this.sessions.get(var2);
      return var3 != null && var3 > System.currentTimeMillis();
   }

   private void respond(HttpExchange var1, int var2, String var3, String var4) throws IOException {
      byte[] var5 = var3.getBytes(StandardCharsets.UTF_8);
      Headers var6 = var1.getResponseHeaders();
      var6.set("Content-Type", var4 + "; charset=utf-8");
      var6.set("Cache-Control", "no-store");
      var6.set("X-Content-Type-Options", "nosniff");
      var6.set("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; form-action 'self'; base-uri 'none'; frame-ancestors 'none'");
      var6.set("X-Frame-Options", "DENY");
      var1.sendResponseHeaders(var2, var5.length);

      try (OutputStream var7 = var1.getResponseBody()) {
         var7.write(var5);
      }
   }

   private void go(HttpExchange var1, String var2) throws IOException {
      var1.getResponseHeaders().set("Location", var2);
      var1.sendResponseHeaders(303, -1L);
      var1.close();
   }

   private Map<String, Object> form(HttpExchange var1) throws IOException {
      if (!"POST".equals(var1.getRequestMethod())) {
         throw new IllegalArgumentException("Требуется POST");
      } else {
         String var2 = var1.getRequestHeaders().getFirst("Content-Type");
         if (var2 != null && var2.startsWith("application/x-www-form-urlencoded")) {
            byte[] var3 = var1.getRequestBody().readNBytes(32769);
            if (var3.length > 32768) {
               throw new IllegalArgumentException("Слишком большой запрос");
            } else {
               HashMap var4 = new HashMap();

               for (String var8 : new String(var3, StandardCharsets.UTF_8).split("&")) {
                  String[] var9 = var8.split("=", 2);
                  String var10 = URLDecoder.decode(var9[0], StandardCharsets.UTF_8);
                  String var11 = URLDecoder.decode(var9.length == 2 ? var9[1] : "", StandardCharsets.UTF_8);
                  var4.put(var10, var11);
               }

               return var4;
            }
         } else {
            throw new IllegalArgumentException("Форма недопустима");
         }
      }
   }

   private void verifyRequest(HttpExchange var1, Map<String, Object> var2) {
      String var3 = var1.getRequestHeaders().getFirst("Origin");
      if (var3 != null && !var3.equals("http://localhost:" + this.adminPort) && !var3.equals("http://127.0.0.1:" + this.adminPort)) {
         throw new SecurityException("Неверный Origin");
      } else {
         String var4 = val(var2, "csrf");
         if (!var4.equals(this.cookie(var1)) || !this.auth(var1)) {
            throw new SecurityException("Недостаточно прав");
         }
      }
   }

   private String nav(String var1) {
      return "<!doctype html><html lang='ru'><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'><title>Zerde AI — администратор</title><style>body{font-family:system-ui;background:#f3f6fb;color:#17243c;margin:0}header{background:#152c55;color:white;padding:22px max(20px,calc((100vw - 1050px)/2))}main{max-width:1050px;margin:auto;padding:24px}section{background:white;border-radius:14px;padding:22px;margin:18px 0;box-shadow:0 2px 14px #20305013}input,select,button{font:inherit;padding:10px;border-radius:8px;border:1px solid #bcc9d9;max-width:100%;box-sizing:border-box}button{background:#195bcb;color:white;cursor:pointer;border:0}label{display:block;margin:10px 0}.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(175px,1fr));gap:15px}.tile{background:#eef4ff;padding:16px;border-radius:12px}table{border-collapse:collapse;width:100%;font-size:14px}td,th{border-bottom:1px solid #e0e5eb;padding:10px;text-align:left}small{color:#61728e}a{color:#1455bb}.error{color:#ac2730}.scroll{overflow:auto}</style><header><h1>Zerde AI — администратор</h1><div>Управление преподавателями и мониторинг обучения · <a style='color:white' href='http://localhost:8080/'>Открыть Zerde AI</a></div></header><main>"
         + var1
         + "</main></html>";
   }

   private String input(String var1, String var2) {
      return "<input type='hidden' name='csrf' value='" + escape(var1) + "'><button>" + escape(var2) + "</button>";
   }

   private String loginPage(boolean var1) {
      return this.nav(
         "<section><h2>"
            + (var1 ? "Первоначальная настройка администратора" : "Вход администратора")
            + "</h2><p>Доступно только на этом компьютере.</p><form method='post' action='"
            + (var1 ? "/setup" : "/login")
            + "'><label>Логин <input name='login' value='Бақыт' required minlength='3' maxlength='80' autocomplete='username'></label><label>Пароль <input name='password' type='password' required minlength='12' maxlength='128' autocomplete='"
            + (var1 ? "new-password" : "current-password")
            + "'></label>"
            + (var1 ? "<label>Повтор пароля <input name='confirm' type='password' required minlength='12'></label>" : "")
            + "<button>"
            + (var1 ? "Создать администратора" : "Войти")
            + "</button></form></section>"
      );
   }

   private List<Map<String, Object>> list(String var1) throws Exception {
      synchronized (this.db) {
         return this.db.all(var1);
      }
   }

   private static long longNumber(Object var0) {
      try {
         return Long.parseLong(String.valueOf(var0));
      } catch (Exception var2) {
         return 0L;
      }
   }

   private static int number(Object var0) {
      if (var0 instanceof Number) {
         return ((Number)var0).intValue();
      } else {
         try {
            return Integer.parseInt(String.valueOf(var0));
         } catch (Exception var2) {
            return 0;
         }
      }
   }

   private String dashboard(String var1) throws Exception {
      List<Map<String, Object>> var2 = this.list("users");
      List<Map<String, Object>> var3 = this.list("attempts");
      List<Map<String, Object>> var4 = this.list("tasks");
      int var5 = 0;
      int var6 = 0;
      int var7 = 0;
      double var8 = 0.0;

      for (Map var11 : var2) {
         if ("student".equals(val(var11, "role"))) {
            var5++;
         }

         if ("teacher".equals(val(var11, "role"))) {
            var6++;
         }
      }

      StringBuilder var18 = new StringBuilder("<section><p><form method='post' action='/logout'>" + this.input(var1, "Выйти") + "</form></p><div class='grid'>");

      for (Map var12 : var3) {
         if ("submitted".equals(val(var12, "status")) || "graded".equals(val(var12, "status"))) {
            var7++;
            var8 += number(var12.get("percent"));
         }
      }

      var18.append(
         "<div class='tile'><strong>"
            + var6
            + "</strong><p>Преподавателей</p></div><div class='tile'><strong>"
            + var5
            + "</strong><p>Студентов</p></div><div class='tile'><strong>"
            + var4.size()
            + "</strong><p>Заданий</p></div><div class='tile'><strong>"
            + var3.size()
            + "</strong><p>Попыток</p></div></div></section>"
      );
      var18.append(
         "<section><h2>Добавить преподавателя</h2><form method='post' action='/add-teacher'><label>Фамилия / логин <input name='login' minlength='2' maxlength='80' required></label><label>Полное ФИО <input name='name' minlength='2' maxlength='160' required></label><label>Пароль (от 12 символов) <input type='password' name='password' minlength='12' maxlength='128' required></label>"
            + this.input(var1, "Создать преподавателя")
            + "</form></section>"
      );
      var18.append("<section class='scroll'><h2>Преподаватели</h2><table><tr><th>ФИО</th><th>Логин</th><th>Статус</th><th>Действие</th></tr>");

      for (Map var22 : var2) {
         if ("teacher".equals(val(var22, "role"))) {
            String var13 = val(var22, "id");
            boolean var14 = Boolean.TRUE.equals(var22.get("active"));
            var18.append(
               "<tr><td>"
                  + escape(val(var22, "name"))
                  + "</td><td>"
                  + escape(val(var22, "login"))
                  + "</td><td>"
                  + (var14 ? "Активен" : "Отключён")
                  + "</td><td><form method='post' action='/toggle-teacher'><input type='hidden' name='id' value='"
                  + escape(var13)
                  + "'>"
                  + this.input(var1, var14 ? "Отключить" : "Включить")
                  + "</form></td></tr>"
            );
         }
      }

      var18.append(
         "</table></section><section class='scroll'><h2>Последние попытки</h2><table><tr><th>Студент</th><th>Задание</th><th>Статус</th><th>Баллы</th></tr>"
      );
      HashMap var21 = new HashMap();
      HashMap var23 = new HashMap();

      for (Map var27 : var2) {
         var21.put(val(var27, "id"), val(var27, "name"));
      }

      for (Map var28 : var4) {
         var23.put(val(var28, "id"), val(var28, "title"));
      }

      var3.sort((var0, var1x) -> Long.compare(longNumber(var1x.get("started")), longNumber(var0.get("started"))));
      int var26 = 0;

      for (Map var15 : var3) {
         if (var26++ >= 35) {
            break;
         }

         String var16 = val(var15, "student");
         String var17 = val(var15, "task");
         var18.append(
            "<tr><td>"
               + escape(var21.getOrDefault(var16, var16))
               + "</td><td>"
               + escape(var23.getOrDefault(var17, var17))
               + "</td><td>"
               + escape(val(var15, "status"))
               + "</td><td>"
               + escape(var15.getOrDefault("score", "—"))
               + "</td></tr>"
         );
      }

      var18.append("</table><small>Показаны существующие попытки; отсутствие оценки не считается нулём.</small></section>");
      return this.nav(var18.toString());
   }

   private void handle(HttpExchange var1) throws IOException {
      try {
         String var2 = var1.getRequestHeaders().getFirst("Host");
         if (var2 == null || !var2.equals("localhost:" + this.adminPort) && !var2.equals("127.0.0.1:" + this.adminPort)) {
            this.respond(var1, 403, "Недопустимый Host", "text/plain");
            return;
         }

         String var3 = var1.getRequestURI().getPath();
         boolean var4 = "POST".equals(var1.getRequestMethod());
         boolean var5 = this.list("zerde-admin").isEmpty();
         if (var5) {
            if (var3.equals("/setup") && var4) {
               Map var20 = this.form(var1);
               String var23 = val(var20, "login");
               String var26 = val(var20, "password");
               if (var23.length() >= 3 && var23.length() <= 80 && var26.length() >= 12 && var26.length() <= 128 && var26.equals(val(var20, "confirm"))) {
                  synchronized (this.db) {
                     if (!this.db.all("zerde-admin").isEmpty()) {
                        throw new IllegalArgumentException("Администратор уже существует");
                     }

                     String var32 = Util.salt();
                     this.db.put("zerde-admin", map("id", "root", "login", var23, "salt", var32, "hash", hash(var26, var32)));
                  }

                  this.go(var1, "/");
                  return;
               }

               throw new IllegalArgumentException("Проверь логин и пароль (минимум 12 символов)");
            }

            this.respond(var1, 200, this.loginPage(true), "text/html");
            return;
         }

         if (var3.equals("/login") && var4) {
            Map var19 = this.form(var1);
            String var22 = val(var19, "login");
            String var25 = val(var19, "password");
            boolean var28 = false;

            for (Map var34 : this.list("zerde-admin")) {
               if (var22.equals(val(var34, "login"))
                  && MessageDigest.isEqual(
                     hash(var25, val(var34, "salt")).getBytes(StandardCharsets.UTF_8), val(var34, "hash").getBytes(StandardCharsets.UTF_8)
                  )) {
                  var28 = true;
               }
            }

            if (!var28) {
               this.respond(var1, 403, this.nav("<section><p class='error'>Неверный логин или пароль</p><a href='/'>Повторить</a></section>"), "text/html");
               return;
            }

            String var31 = this.token();
            this.sessions.put(var31, System.currentTimeMillis() + 3600000L);
            var1.getResponseHeaders().add("Set-Cookie", "za_session=" + var31 + "; HttpOnly; SameSite=Strict; Path=/");
            this.go(var1, "/");
            return;
         }

         if (!this.auth(var1)) {
            this.respond(var1, 200, this.loginPage(false), "text/html");
            return;
         }

         if (var3.equals("/") && !var4) {
            this.respond(var1, 200, this.dashboard(this.cookie(var1)), "text/html");
            return;
         }

         Map var6 = this.form(var1);
         this.verifyRequest(var1, var6);
         if (var3.equals("/logout")) {
            this.sessions.remove(this.cookie(var1));
            var1.getResponseHeaders().add("Set-Cookie", "za_session=; HttpOnly; SameSite=Strict; Path=/; Max-Age=0");
            this.go(var1, "/");
            return;
         }

         if (var3.equals("/add-teacher")) {
            String var21 = val(var6, "login");
            String var24 = val(var6, "name");
            String var27 = val(var6, "password");
            if (var21.length() >= 2 && var21.length() <= 80 && var24.length() >= 2 && var24.length() <= 160 && var27.length() >= 12 && var27.length() <= 128) {
               synchronized (this.db) {
                  for (Map var12 : this.db.all("users")) {
                     if ("teacher".equals(val(var12, "role")) && var21.equalsIgnoreCase(val(var12, "login"))) {
                        throw new IllegalArgumentException("Логин уже используется");
                     }
                  }

                  String var33 = Util.salt();
                  this.db
                     .put(
                        "users",
                        map(
                           "id",
                           UUID.randomUUID().toString(),
                           "role",
                           "teacher",
                           "login",
                           var21,
                           "name",
                           var24,
                           "salt",
                           var33,
                           "hash",
                           hash(var27, var33),
                           "avatar",
                           "",
                           "group",
                           "",
                           "active",
                           true
                        )
                     );
               }

               this.go(var1, "/");
               return;
            }

            throw new IllegalArgumentException("Проверь поля преподавателя");
         }

         if (var3.equals("/toggle-teacher")) {
            String var7 = val(var6, "id");
            synchronized (this.db) {
               Map var9 = this.db.get("users", var7);
               if (var9 == null || !"teacher".equals(val(var9, "role"))) {
                  throw new IllegalArgumentException("Преподаватель не найден");
               }

               var9.put("active", !Boolean.TRUE.equals(var9.get("active")));
               this.db.put("users", var9);
            }

            this.go(var1, "/");
            return;
         }

         this.respond(var1, 404, "Не найдено", "text/plain");
      } catch (Exception var18) {
         this.respond(var1, 400, this.nav("<section><p class='error'>" + escape(var18.getMessage()) + "</p><a href='/'>Назад</a></section>"), "text/html");
      }
   }
}
