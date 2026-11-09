import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class Json {
   String s;
   int p;
   int depth;

   Json(String var1) {
      this.s = var1;
   }

   static Object parse(String var0) {
      Json var1 = new Json(var0);
      Object var2 = var1.value();
      var1.space();
      if (var1.p != var0.length()) {
         throw new IllegalArgumentException("Лишний текст JSON");
      } else {
         return var2;
      }
   }

   void space() {
      while (this.p < this.s.length() && Character.isWhitespace(this.s.charAt(this.p))) {
         this.p++;
      }
   }

   boolean take(char var1) {
      if (this.p < this.s.length() && this.s.charAt(this.p) == var1) {
         this.p++;
         return true;
      } else {
         return false;
      }
   }

   void expect(char var1) {
      if (!this.take(var1)) {
         throw new IllegalArgumentException("Ожидается " + var1);
      }
   }

   Object value() {
      this.space();
      if (++this.depth > 80) {
         throw new IllegalArgumentException("JSON слишком вложенный");
      } else {
         Object var1;
         try {
            var1 = this.read();
         } finally {
            this.depth--;
         }

         return var1;
      }
   }

   Object read() {
      if (this.p >= this.s.length()) {
         throw new IllegalArgumentException("Пустой JSON");
      } else {
         char var1 = this.s.charAt(this.p);
         if (var1 == '"') {
            return this.string();
         } else if (var1 == '{') {
            this.p++;
            LinkedHashMap var7 = new LinkedHashMap();
            this.space();
            if (this.take('}')) {
               return var7;
            } else {
               while (true) {
                  this.space();
                  String var8 = this.string();
                  this.space();
                  this.expect(':');
                  var7.put(var8, this.value());
                  this.space();
                  if (this.take('}')) {
                     return var7;
                  }

                  this.expect(',');
               }
            }
         } else if (var1 == '[') {
            this.p++;
            ArrayList var6 = new ArrayList();
            this.space();
            if (this.take(']')) {
               return var6;
            } else {
               while (true) {
                  var6.add(this.value());
                  this.space();
                  if (this.take(']')) {
                     return var6;
                  }

                  this.expect(',');
               }
            }
         } else {
            for (String var3 : List.of("true", "false", "null")) {
               if (this.s.startsWith(var3, this.p)) {
                  this.p = this.p + var3.length();
                  return var3.equals("null") ? null : var3.equals("true");
               }
            }

            int var5 = this.p;

            while (this.p < this.s.length() && "0123456789+-.eE".indexOf(this.s.charAt(this.p)) >= 0) {
               this.p++;
            }

            if (this.p == var5) {
               throw new IllegalArgumentException("Некорректный JSON");
            } else {
               try {
                  return Double.valueOf(this.s.substring(var5, this.p));
               } catch (Exception var4) {
                  throw new IllegalArgumentException("Некорректное число");
               }
            }
         }
      }
   }

   String string() {
      this.expect('"');
      StringBuilder var1 = new StringBuilder();

      while (this.p < this.s.length()) {
         char var2 = this.s.charAt(this.p++);
         if (var2 == '"') {
            return var1.toString();
         }

         if (var2 == '\\') {
            if (this.p >= this.s.length()) {
               break;
            }

            var2 = this.s.charAt(this.p++);
            switch (var2) {
               case '"':
               case '/':
               case '\\':
                  var1.append(var2);
                  break;
               case 'b':
                  var1.append('\b');
                  break;
               case 'f':
                  var1.append('\f');
                  break;
               case 'n':
                  var1.append('\n');
                  break;
               case 'r':
                  var1.append('\r');
                  break;
               case 't':
                  var1.append('\t');
                  break;
               case 'u':
                  if (this.p + 4 > this.s.length()) {
                     throw new IllegalArgumentException("JSON Unicode");
                  }

                  var1.append((char)Integer.parseInt(this.s.substring(this.p, this.p + 4), 16));
                  this.p += 4;
                  break;
               default:
                  throw new IllegalArgumentException("JSON escape");
            }
         } else {
            if (var2 < ' ') {
               throw new IllegalArgumentException("JSON control");
            }

            var1.append(var2);
         }
      }

      throw new IllegalArgumentException("Незавершённая строка");
   }

   static String write(Object var0) {
      if (var0 == null) {
         return "null";
      } else if (var0 instanceof String var8) {
         StringBuilder var9 = new StringBuilder("\"");

         for (char var6 : var8.toCharArray()) {
            switch (var6) {
               case '\t':
                  var9.append("\\t");
                  break;
               case '\n':
                  var9.append("\\n");
                  break;
               case '\r':
                  var9.append("\\r");
                  break;
               case '"':
                  var9.append("\\\"");
                  break;
               case '\\':
                  var9.append("\\\\");
                  break;
               default:
                  if (var6 < ' ') {
                     var9.append(String.format("\\u%04x", Integer.valueOf(var6)));
                  } else {
                     var9.append(var6);
                  }
            }
         }

         return var9.append('"').toString();
      } else if (var0 instanceof Number || var0 instanceof Boolean) {
         return var0.toString();
      } else if (var0 instanceof Map var7) {
         ArrayList var2 = new ArrayList();
         var7.forEach((var1x, var2x) -> var2.add(write(var1x.toString()) + ":" + write(var2x)));
         return "{" + String.join(",", var2) + "}";
      } else if (var0 instanceof Collection var1) {
         return "[" + String.join(",", var1.stream().map(Json::write).toList()) + "]";
      } else {
         throw new IllegalArgumentException("JSON type");
      }
   }
}
