import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

class Util {
   static Map<String, Object> m(Object... var0) {
      LinkedHashMap var1 = new LinkedHashMap();

      for (byte var2 = 0; var2 < var0.length; var2 += 2) {
         var1.put((String)var0[var2], var0[var2 + 1]);
      }

      return var1;
   }

   static Map<String, Object> obj(Object var0) {
      if (!(var0 instanceof Map)) {
         throw new IllegalArgumentException("Ожидается объект");
      } else {
         return (Map<String, Object>)var0;
      }
   }

   static List<Object> arr(Object var0) {
      if (!(var0 instanceof List)) {
         throw new IllegalArgumentException("Ожидается список");
      } else {
         return (List<Object>)var0;
      }
   }

   static List<Object> list(Map<String, Object> var0, String var1) {
      return arr(var0.getOrDefault(var1, new ArrayList()));
   }

   static String s(Map<String, Object> var0, String var1) {
      return var0.get(var1) instanceof String var2 ? var2 : "";
   }

   static boolean yes(Map<String, Object> var0, String var1) {
      return Boolean.TRUE.equals(var0.get(var1));
   }

   static int n(Object var0) {
      if (var0 instanceof Number var1 && var1.doubleValue() == var1.intValue()) {
         return var1.intValue();
      } else {
         throw new IllegalArgumentException("Ожидается целое число");
      }
   }

   static long lng(Object var0) {
      return var0 instanceof Number var1 ? var1.longValue() : 0L;
   }

   static String req(Map<String, Object> var0, String var1, int var2) {
      String var3 = s(var0, var1).strip();
      if (!var3.isEmpty() && var3.length() <= var2) {
         return var3;
      } else {
         throw new IllegalArgumentException("Проверьте поле: " + var1);
      }
   }

   static void check(boolean var0, String var1) {
      if (!var0) {
         throw new IllegalArgumentException(var1);
      }
   }

   static String id() {
      return UUID.randomUUID().toString();
   }

   static long now() {
      return System.currentTimeMillis();
   }

   static Map<String, Object> copy(Map<String, Object> var0) {
      return obj(Json.parse(Json.write(var0)));
   }

   static String normalize(String var0) {
      return var0.strip().toLowerCase(Locale.ROOT);
   }

   static String hash(String var0, String var1) throws Exception {
      PBEKeySpec var2 = new PBEKeySpec(var0.toCharArray(), Base64.getDecoder().decode(var1), 210000, 256);

      String var3;
      try {
         var3 = Base64.getEncoder().encodeToString(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(var2).getEncoded());
      } finally {
         var2.clearPassword();
      }

      return var3;
   }

   static String salt() {
      byte[] var0 = new byte[24];
      new SecureRandom().nextBytes(var0);
      return Base64.getEncoder().encodeToString(var0);
   }

   static boolean password(Map<String, Object> var0, String var1) throws Exception {
      return MessageDigest.isEqual(hash(var1, s(var0, "salt")).getBytes(StandardCharsets.UTF_8), s(var0, "hash").getBytes(StandardCharsets.UTF_8));
   }

   static Map<String, Object> publicUser(Map<String, Object> var0) {
      Map var1 = copy(var0);
      var1.remove("salt");
      var1.remove("hash");
      return var1;
   }

   static void teacher(Map<String, Object> var0) {
      check(s(var0, "role").equals("teacher"), "Доступ только преподавателю");
   }
}
