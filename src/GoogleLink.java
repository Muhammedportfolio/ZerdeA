import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

class GoogleLink extends Util implements AutoCloseable {
   final AI ai;
   final Map<String, GoogleLink.Flow> flows = new ConcurrentHashMap<>();
   final ScheduledExecutorService expiry = Executors.newSingleThreadScheduledExecutor();
   final GoogleLink.Transport transport;

   GoogleLink(AI var1) {
      this(var1, (var0, var1x, var2, var3, var4) -> {
         Builder var5 = HttpRequest.newBuilder(URI.create(var0)).timeout(Duration.ofSeconds(25L));
         if (!var2.isBlank()) {
            var5.header("Authorization", "Bearer " + var2);
         }

         if (var1x.equals("POST")) {
            var5.header("Content-Type", var4).POST(BodyPublishers.ofString(var3));
         } else {
            var5.GET();
         }

         HttpResponse var6 = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10L)).build().send(var5.build(), BodyHandlers.ofString());

         Map var7;
         try {
            var7 = obj(Json.parse((String)var6.body()));
         } catch (Exception var9) {
            var7 = m();
         }

         return new GoogleLink.Reply(var6.statusCode(), var7);
      });
   }

   GoogleLink(AI var1, GoogleLink.Transport var2) {
      this.ai = var1;
      this.transport = var2;
   }

   String slot(String var1) {
      return "google-oauth:" + var1;
   }

   Map<String, Object> config(String var1) {
      return this.ai.settings.getOrDefault(this.slot(var1), m());
   }

   Map<String, Object> status(String var1) {
      GoogleLink.Flow var2 = this.flows.get(var1);
      Map var3 = this.config(var1);
      return m(
         "configured",
         !s(var3, "clientId").isBlank(),
         "projectNumber",
         s(var3, "projectNumber"),
         "status",
         var2 == null ? "idle" : var2.status,
         "message",
         var2 == null ? "" : var2.message
      );
   }

   synchronized void configure(String var1, Map<String, Object> var2) {
      check(
         !s(var2, "projectNumber").isBlank() && s(var2, "projectNumber").matches("[0-9]{5,30}"),
         "Укажите числовой Project number из Google Cloud, не Project ID."
      );
      Map var3 = obj(obj(var2.get("credentials")).get("installed"));
      String var4 = req(var3, "client_id", 300);
      check(var4.matches("[A-Za-z0-9_-]+\\.apps\\.googleusercontent\\.com"), "Нужен OAuth JSON типа Desktop app от Google.");
      String var5 = req(var3, "client_secret", 300);
      this.cancel(var1);
      synchronized (this.ai) {
         Map var7 = this.ai.settings.put(this.slot(var1), m("clientId", var4, "clientSecret", var5, "projectNumber", s(var2, "projectNumber")));

         try {
            this.ai.persist();
         } catch (RuntimeException var10) {
            if (var7 == null) {
               this.ai.settings.remove(this.slot(var1));
            } else {
               this.ai.settings.put(this.slot(var1), var7);
            }

            throw var10;
         }
      }
   }

   static String random() {
      byte[] var0 = new byte[32];
      new SecureRandom().nextBytes(var0);
      return Base64.getUrlEncoder().withoutPadding().encodeToString(var0);
   }

   static String enc(String var0) {
      return URLEncoder.encode(var0, StandardCharsets.UTF_8);
   }

   static String form(Map<String, Object> var0) {
      return var0.entrySet()
         .stream()
         .map(var0x -> enc((String)var0x.getKey()) + "=" + enc(String.valueOf(var0x.getValue())))
         .reduce((var0x, var1) -> var0x + "&" + var1)
         .orElse("");
   }

   synchronized Map<String, Object> start(String var1) throws Exception {
      Map var2 = copy(this.config(var1));
      check(!s(var2, "clientId").isBlank(), "Сначала выполните первичную настройку Google Cloud ниже.");
      this.cancel(var1);
      GoogleLink.Flow var3 = new GoogleLink.Flow();
      var3.uid = var1;
      var3.config = var2;
      var3.state = random();
      var3.verifier = random();
      var3.callback = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      var3.worker = Executors.newSingleThreadExecutor();
      var3.callback.setExecutor(var3.worker);
      var3.redirect = "http://127.0.0.1:" + var3.callback.getAddress().getPort() + "/oauth/callback";
      var3.callback.createContext("/oauth/callback", var2x -> this.callback(var3, var2x));
      var3.callback.start();
      this.flows.put(var1, var3);
      this.expiry.schedule(() -> {
         if (var3.status.equals("pending")) {
            var3.status = "expired";
            var3.message = "Время подключения истекло. Нажмите «Привязать Google» повторно.";
         }

         this.cleanup(var3);
      }, 10L, TimeUnit.MINUTES);
      String var4 = Base64.getUrlEncoder()
         .withoutPadding()
         .encodeToString(MessageDigest.getInstance("SHA-256").digest(var3.verifier.getBytes(StandardCharsets.US_ASCII)));
      String var5 = "https://accounts.google.com/o/oauth2/v2/auth?"
         + form(
            m(
               "client_id",
               s(var2, "clientId"),
               "redirect_uri",
               var3.redirect,
               "response_type",
               "code",
               "scope",
               "https://www.googleapis.com/auth/cloud-platform",
               "state",
               var3.state,
               "code_challenge",
               var4,
               "code_challenge_method",
               "S256",
               "access_type",
               "online",
               "prompt",
               "consent select_account"
            )
         );
      return m("url", var5);
   }

   static Map<String, String> query(String var0) {
      HashMap var1 = new HashMap();
      if (var0 != null) {
         for (String var5 : var0.split("&")) {
            String[] var6 = var5.split("=", 2);
            if (var6.length == 2) {
               var1.put(URLDecoder.decode(var6[0], StandardCharsets.UTF_8), URLDecoder.decode(var6[1], StandardCharsets.UTF_8));
            }
         }
      }

      return var1;
   }

   void callback(GoogleLink.Flow var1, HttpExchange var2) throws IOException {
      Map var3;
      try {
         var3 = query(var2.getRequestURI().getRawQuery());
      } catch (Exception var11) {
         reply(var2, 400, "Некорректный запрос");
         return;
      }

      if (var2.getRequestMethod().equals("GET")
         && var2.getRemoteAddress().getAddress().isLoopbackAddress()
         && var2.getRequestURI().getPath().equals("/oauth/callback")
         && var1.state.equals(var3.get("state"))) {
         synchronized (var1) {
            if (var1.consumed || !var1.status.equals("pending")) {
               reply(var2, 409, "Этот запрос уже обработан.");
               return;
            }

            var1.consumed = true;
         }

         try {
            if (var3.containsKey("error")) {
               var1.status = "cancelled";
               var1.message = "Разрешение Google не предоставлено. Подключение не изменено.";
            } else {
               check(var3.containsKey("code"), "Google не вернул код подтверждения.");
               var1.status = "working";
               var1.message = "Создание и подключение ключа…";
               this.complete(var1, (String)var3.get("code"));
               var1.status = "connected";
               var1.message = "Ключ Gemini подключён и сохранён. Вернитесь в Zerde AI и проверьте подключение.";
            }
         } catch (Exception var12) {
            if (!var1.status.equals("cancelled")) {
               var1.status = "error";
               var1.message = var12 instanceof IllegalArgumentException
                  ? var12.getMessage()
                  : "Не удалось завершить подключение Google. Проверьте сеть и настройки проекта; повторная попытка использует тот же ключ.";
            }
         } finally {
            reply(var2, 200, var1.message);
            this.expiry.schedule(() -> this.cleanup(var1), 1L, TimeUnit.SECONDS);
         }
      } else {
         reply(var2, 400, "Проверка подключения не пройдена. Вернитесь в Zerde AI.");
      }
   }

   void complete(GoogleLink.Flow var1, String var2) throws Exception {
      GoogleLink.Reply var3 = this.transport
         .send(
            "https://oauth2.googleapis.com/token",
            "POST",
            "",
            form(
               m(
                  "client_id",
                  s(var1.config, "clientId"),
                  "client_secret",
                  s(var1.config, "clientSecret"),
                  "code",
                  var2,
                  "code_verifier",
                  var1.verifier,
                  "redirect_uri",
                  var1.redirect,
                  "grant_type",
                  "authorization_code"
               )
            ),
            "application/x-www-form-urlencoded"
         );
      check(var3.status == 200, "Google не выдал доступ. Проверьте Desktop OAuth client, тестового пользователя и повторите вход.");
      String var4 = req(var3.body, "access_token", 20000);
      String var5 = s(var1.config, "projectNumber");
      String var6 = HexFormat.of()
         .formatHex(MessageDigest.getInstance("SHA-256").digest((var5 + ":" + var1.uid).getBytes(StandardCharsets.UTF_8)))
         .substring(0, 24);
      String var7 = "zerde-" + var6;
      String var8 = "https://apikeys.googleapis.com/v2/";
      String var9 = "projects/" + var5 + "/locations/global/keys/" + var7;
      GoogleLink.Reply var10 = this.transport.send(var8 + var9 + "/keyString", "GET", var4, "", "");
      String var11 = "";
      if (var10.status == 200) {
         var11 = s(var10.body, "keyString");
      } else {
         check(var10.status == 404, cloudError(var10.status));
         check(!var1.status.equals("cancelled"), "Подключение отменено.");
         GoogleLink.Reply var12 = this.transport
            .send(
               var8 + "projects/" + var5 + "/locations/global/keys?keyId=" + var7,
               "POST",
               var4,
               Json.write(m("displayName", "Zerde AI Gemini", "restrictions", m("apiTargets", List.of(m("service", "generativelanguage.googleapis.com"))))),
               "application/json"
            );
         if (var12.status != 409) {
            check(var12.status == 200 || var12.status == 201, cloudError(var12.status));
            Map var13 = var12.body;
            String var14 = s(var13, "name");
            check(var14.matches("operations/[A-Za-z0-9._-]+"), "Google вернул неизвестный формат операции.");

            for (int var15 = 0; var15 < 25 && !yes(var13, "done"); var15++) {
               check(!var1.status.equals("cancelled"), "Подключение отменено.");
               Thread.sleep(1000L);
               GoogleLink.Reply var16 = this.transport.send(var8 + var14, "GET", var4, "", "");
               check(var16.status == 200, cloudError(var16.status));
               var13 = var16.body;
            }

            check(yes(var13, "done"), "Google ещё создаёт ключ. Подождите немного и повторите привязку: дубликат ключа не будет создан.");
            check(!var13.containsKey("error"), "Google не создал ключ. Проверьте права проекта и лимит ключей в Google Cloud.");
         }

         GoogleLink.Reply var20 = this.transport.send(var8 + var9 + "/keyString", "GET", var4, "", "");
         check(var20.status == 200, cloudError(var20.status));
         var11 = s(var20.body, "keyString");
      }

      check(!var11.isBlank(), "Google вернул пустой ключ.");
      synchronized (this) {
         check(!var1.status.equals("cancelled") && this.flows.get(var1.uid) == var1, "Подключение отменено.");
         this.ai.configure(var1.uid, m("provider", "gemini", "model", "gemini-3.5-flash-lite", "key", var11));
      }
   }

   static String cloudError(int var0) {
      if (var0 == 403) {
         return "Google запретил управление ключом. Включите API Keys API и выдайте аккаунту права создания и чтения API-ключей в выбранном проекте. Проверьте также настройки OAuth.";
      } else if (var0 == 401) {
         return "Разрешение Google истекло. Повторите привязку.";
      } else {
         return var0 == 429
            ? "Google ограничил запросы. Повторите позже; смена ключа не увеличит квоту."
            : "Google Cloud вернул HTTP " + var0 + ". Проверьте Project number, API Keys API и настройки проекта.";
      }
   }

   synchronized void cancel(String var1) {
      GoogleLink.Flow var2 = this.flows.remove(var1);
      if (var2 != null) {
         var2.status = "cancelled";
         var2.message = "Подключение отменено";
         this.cleanup(var2);
      }
   }

   void cleanup(GoogleLink.Flow var1) {
      if (var1.callback != null) {
         var1.callback.stop(0);
      }

      if (var1.worker != null) {
         var1.worker.shutdownNow();
      }
   }

   static void reply(HttpExchange var0, int var1, String var2) throws IOException {
      byte[] var3 = ("Zerde AI\n\n" + var2).getBytes(StandardCharsets.UTF_8);
      var0.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
      var0.getResponseHeaders().set("Cache-Control", "no-store");
      var0.getResponseHeaders().set("Referrer-Policy", "no-referrer");
      var0.sendResponseHeaders(var1, var3.length);
      var0.getResponseBody().write(var3);
      var0.close();
   }

   @Override
   public synchronized void close() {
      for (String var2 : new ArrayList<>(this.flows.keySet())) {
         this.cancel(var2);
      }

      this.expiry.shutdownNow();
   }

   static class Flow {
      String uid;
      String state;
      String verifier;
      String redirect;
      Map<String, Object> config;
      HttpServer callback;
      ExecutorService worker;
      volatile String status = "pending";
      volatile String message = "Ожидание разрешения Google";
      boolean consumed;
   }

   record Reply(int status, Map<String, Object> body) {
   }

   interface Transport {
      GoogleLink.Reply send(String var1, String var2, String var3, String var4, String var5) throws Exception;
   }
}
