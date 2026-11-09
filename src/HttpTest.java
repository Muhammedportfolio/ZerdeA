import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.util.Map;

class HttpTest extends Util {
   static int count;

   static void ok(boolean var0, String var1) {
      if (!var0) {
         throw new AssertionError(var1);
      } else {
         System.out.println("PASS " + var1);
         count++;
      }
   }

   public static void main(String[] var0) throws Exception {
      Server var1 = new Server(Files.createTempDirectory("suite-http-test-"), "127.0.0.1", 0);

      try {
         Map var2 = var1.db.get("users", "teacher-main");
         String var3 = "test-" + id();
         String var4 = salt();
         var2.put("salt", var4);
         var2.put("hash", hash(var3, var4));
         var1.db.put("users", var2);
         CookieManager var5 = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
         HttpClient var6 = HttpClient.newBuilder().cookieHandler(var5).build();
         String var7 = "http://127.0.0.1:" + var1.http.getAddress().getPort();
         HttpResponse var8 = var6.send(HttpRequest.newBuilder(URI.create(var7 + "/api/state")).GET().build(), BodyHandlers.ofString());
         ok(var8.statusCode() == 401, "anonymous API access blocked");
         HttpResponse var9 = var6.send(
            HttpRequest.newBuilder(URI.create(var7 + "/api/login"))
               .header("Content-Type", "application/json")
               .POST(BodyPublishers.ofString(Json.write(m("role", "teacher", "login", "Бақыт", "password", var3))))
               .build(),
            BodyHandlers.ofString()
         );
         ok(var9.statusCode() == 200, "HTTP login issues session");
         ok(var9.headers().firstValue("set-cookie").orElse("").contains("HttpOnly"), "HttpOnly session cookie");
         String var10 = s(obj(Json.parse((String)var9.body())), "csrf");
         HttpResponse var11 = var6.send(HttpRequest.newBuilder(URI.create(var7 + "/api/state")).GET().build(), BodyHandlers.ofString());
         ok(
            var11.statusCode() == 200 && !((String)var11.body()).contains("\"hash\"") && !((String)var11.body()).contains(var3),
            "authenticated state strips password data"
         );
         HttpResponse var12 = var6.send(
            HttpRequest.newBuilder(URI.create(var7 + "/api/groups/save"))
               .header("Content-Type", "application/json")
               .POST(BodyPublishers.ofString("{\"name\":\"CSRF test\"}"))
               .build(),
            BodyHandlers.ofString()
         );
         ok(var12.statusCode() == 403, "CSRF missing rejected");
         HttpResponse var13 = var6.send(
            HttpRequest.newBuilder(URI.create(var7 + "/api/groups/save"))
               .header("Content-Type", "application/json")
               .header("X-CSRF", var10)
               .header("Origin", "https://unrelated.invalid")
               .POST(BodyPublishers.ofString("{\"name\":\"CSRF test\"}"))
               .build(),
            BodyHandlers.ofString()
         );
         ok(var13.statusCode() == 403, "foreign origin rejected");
         HttpResponse var14 = var6.send(
            HttpRequest.newBuilder(URI.create(var7 + "/api/groups/save"))
               .header("Content-Type", "application/json")
               .header("X-CSRF", var10)
               .POST(BodyPublishers.ofString("{\"name\":\"Тест тобы\"}"))
               .build(),
            BodyHandlers.ofString()
         );
         ok(var14.statusCode() == 200, "valid authenticated mutation accepted");
         HttpResponse var15 = var6.send(HttpRequest.newBuilder(URI.create(var7 + "/")).GET().build(), BodyHandlers.ofString());
         ok(
            var15.statusCode() == 200 && ((String)var15.body()).contains("app.js") && var15.headers().firstValue("content-security-policy").isPresent(),
            "packaged web resources and CSP served"
         );
         System.out.println("TOTAL " + count + " HTTP checks passed");
      } finally {
         var1.stop();
      }
   }
}
