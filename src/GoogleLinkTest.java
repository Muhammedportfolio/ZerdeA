import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

class GoogleLinkTest extends Util {
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
      Path var1 = Files.createTempDirectory("zerde-google-test-");
      AI var2 = new AI(var1);
      AtomicInteger var3 = new AtomicInteger();
      ArrayList var4 = new ArrayList();
      AtomicBoolean var5 = new AtomicBoolean(false);
      GoogleLink.Transport var6 = (var3x, var4x, var5x, var6x, var7x) -> {
         var4.add(var3x);
         if (!var3x.equals("https://oauth2.googleapis.com/token")) {
            if (var3x.endsWith("/keyString")) {
               return new GoogleLink.Reply(var5.get() ? 200 : 404, var5.get() ? m("keyString", "fake-gemini-key") : m());
            } else if (var4x.equals("POST")) {
               var3.incrementAndGet();
               ok(var6x.contains("generativelanguage.googleapis.com"), "key restricted to Gemini API");
               return new GoogleLink.Reply(200, m("name", "operations/test-1"));
            } else {
               var5.set(true);
               return new GoogleLink.Reply(200, m("done", true));
            }
         } else {
            ok(var6x.contains("code_verifier=") && var6x.contains("redirect_uri="), "PKCE verifier sent to token endpoint");
            return new GoogleLink.Reply(200, m("access_token", "temporary-access"));
         }
      };
      GoogleLink var7 = new GoogleLink(var2, var6);

      try {
         boolean var8 = false;

         try {
            var7.start("teacher");
         } catch (IllegalArgumentException var22) {
            var8 = true;
         }

         ok(var8, "unconfigured OAuth cannot start");
         var7.configure(
            "teacher",
            m(
               "projectNumber",
               "1234567890",
               "credentials",
               m("installed", m("client_id", "123-test.apps.googleusercontent.com", "client_secret", "fake-client-secret"))
            )
         );
         Map var9 = var7.start("teacher");
         URI var10 = URI.create(s(var9, "url"));
         Map var11 = GoogleLink.query(var10.getRawQuery());
         ok(var10.getHost().equals("accounts.google.com") && ((String)var11.get("code_challenge_method")).equals("S256"), "Google authorization uses PKCE S256");
         ok(((String)var11.get("redirect_uri")).startsWith("http://127.0.0.1:"), "callback only binds loopback");
         HttpClient var12 = HttpClient.newHttpClient();
         String var13 = (String)var11.get("redirect_uri");
         HttpResponse var14 = var12.send(HttpRequest.newBuilder(URI.create(var13 + "?state=wrong&code=fake")).build(), BodyHandlers.ofString());
         ok(var14.statusCode() == 400 && var4.isEmpty(), "invalid state rejected before external requests");
         HttpResponse var15 = var12.send(
            HttpRequest.newBuilder(URI.create(var13 + "?state=" + (String)var11.get("state") + "&code=fake")).build(), BodyHandlers.ofString()
         );
         ok(var15.statusCode() == 200 && s(var7.status("teacher"), "status").equals("connected"), "OAuth callback completes with mocked Google");
         ok(s(var2.config("teacher"), "key").equals("fake-gemini-key"), "key automatically saved to Gemini settings");
         ok(
            !((String)var15.body()).contains("fake-gemini-key") && !Json.write(var7.status("teacher")).contains("fake-client-secret"),
            "callback and public status do not expose credentials"
         );
         var7.start("teacher");
         GoogleLink.Flow var16 = var7.flows.get("teacher");
         var7.complete(var16, "fake-again");
         ok(var3.get() == 1, "reconnect reuses same key without duplicate creation");
         var7.cancel("teacher");
         ok(!var7.flows.containsKey("teacher"), "cancel closes pending authorization");
         AI var17 = new AI(var1);
         ok(s(var17.config("teacher"), "key").equals("fake-gemini-key"), "resulting Gemini key survives restart");
         ok(!Json.write(var17.settings).contains("temporary-access"), "OAuth access token is not persisted");
         var7.start("teacher");
         var16 = var7.flows.get("teacher");
         HttpResponse var18 = var12.send(
            HttpRequest.newBuilder(URI.create(var16.redirect + "?state=" + var16.state + "&error=access_denied")).build(), BodyHandlers.ofString()
         );
         ok(s(var7.status("teacher"), "status").equals("cancelled"), "Google permission denial handled without retry");
         ok(var3.get() == 1, "denial does not create keys");
      } finally {
         var7.close();
      }

      System.out.println("TOTAL " + count + " Google OAuth checks passed (mock provider)");
   }
}
