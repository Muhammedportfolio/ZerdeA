import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

class Runner extends Util {
   static Map<String, Object> run(Map<String, Object> var0, String var1) throws Exception {
      check(!var1.isBlank() && var1.length() <= 30000, "Введите код (до 30000 символов)");
      String var2 = s(var0, "language");

      String var3 = switch (var2) {
         case "Python" -> "python:3.12-slim";
         case "C++" -> "gcc:14";
         default -> "eclipse-temurin:17-jdk";
      };
      Runner.Proc var4 = process(List.of("docker", "image", "inspect", var3), "", 10);
      if (var4.exit != 0) {
         throw new IllegalArgumentException("Запустите Docker Desktop и установите образ:\ndocker pull " + var3);
      } else {
         Path var44 = Files.createTempDirectory("edutest-code-");
         ArrayList var6 = new ArrayList();
         int var7 = 0;

         Map var46;
         try {
            String var8 = switch (var2) {
               case "Python" -> "main.py";
               case "C++" -> "main.cpp";
               default -> "Main.java";
            };
            Files.writeString(var44.resolve(var8), var1);

            try {
               Files.setPosixFilePermissions(var44, PosixFilePermissions.fromString("rwxr-xr-x"));
               Files.setPosixFilePermissions(var44.resolve(var8), PosixFilePermissions.fromString("rw-r--r--"));
            } catch (UnsupportedOperationException var41) {
            }

            List var9 = arr(var0.get("tests"));

            for (int var45 = 0; var45 < var9.size(); var45++) {
               Map var11 = obj(var9.get(var45));
               String var12 = "edutest-" + UUID.randomUUID();
               ArrayList var13 = new ArrayList<>(
                  List.of(
                     "docker",
                     "run",
                     "--pull=never",
                     "--rm",
                     "-i",
                     "--name",
                     var12,
                     "--network=none",
                     "--memory=256m",
                     "--cpus=1",
                     "--pids-limit=64",
                     "--cap-drop=ALL",
                     "--security-opt=no-new-privileges",
                     "--read-only",
                     "--user=65534:65534",
                     "--tmpfs",
                     "/work:rw,exec,size=32m,mode=1777",
                     "--tmpfs",
                     "/tmp:rw,size=32m,mode=1777",
                     "--mount",
                     "type=bind,src=" + var44.toAbsolutePath() + ",dst=/source,readonly",
                     var3
                  )
               );
               if (var2.equals("Python")) {
                  var13.addAll(List.of("python", "-B", "/source/main.py"));
               } else if (var2.equals("C++")) {
                  var13.addAll(List.of("sh", "-c", "g++ -std=c++17 -O0 /source/main.cpp -o /work/main && exec /work/main"));
               } else {
                  var13.addAll(List.of("java", "-Xmx96m", "/source/Main.java"));
               }

               Runner.Proc var14;
               try {
                  var14 = process(var13, s(var11, "input"), 15);
               } finally {
                  try {
                     process(List.of("docker", "rm", "-f", var12), "", 5);
                  } catch (Exception var39) {
                  }
               }

               boolean var15 = var14.exit == 0 && normalizeOutput(var14.out).equals(normalizeOutput(s(var11, "expected")));
               if (var15) {
                  var7++;
               }

               var6.add(
                  m(
                     "title",
                     "Контрольный пример " + (var45 + 1),
                     "ok",
                     var15,
                     "text",
                     "Выполнение " + var2,
                     "answer",
                     var14.out,
                     "expected",
                     s(var11, "expected"),
                     "explanation",
                     var14.exit == -1
                        ? "Превышен лимит времени или вывода."
                        : (
                           var14.exit == 0
                              ? (var15 ? "Результат совпадает." : "Результат не совпадает.")
                              : "Ошибка выполнения/компиляции (код " + var14.exit + "):\n" + var14.err
                        )
                  )
               );
            }

            var46 = m("score", var7, "total", var9.size(), "details", var6);
         } finally {
            try (Stream<Path> var19 = Files.walk(var44)) {
               for (Path var21 : var19.sorted(Comparator.reverseOrder()).toList()) {
                  Files.deleteIfExists(var21);
               }
            }
         }

         return var46;
      }
   }

   static String normalizeOutput(String var0) {
      return var0.replace("\r\n", "\n").strip();
   }

   static Runner.Proc process(List<String> var0, String var1, int var2) throws Exception {
      Process var3;
      try {
         var3 = new ProcessBuilder(var0).start();
      } catch (IOException var19) {
         throw new IllegalArgumentException("Не удалось запустить " + (String)var0.get(0) + ". Установите и запустите Docker Desktop с Linux containers.");
      }

      ByteArrayOutputStream var4 = new ByteArrayOutputStream();
      ByteArrayOutputStream var5 = new ByteArrayOutputStream();
      AtomicBoolean var6 = new AtomicBoolean();
      Thread var7 = drain(var3, var3.getInputStream(), var4, var6);
      Thread var8 = drain(var3, var3.getErrorStream(), var5, var6);

      Runner.Proc var10;
      try {
         try (OutputStream var9 = var3.getOutputStream()) {
            var9.write(var1.getBytes(StandardCharsets.UTF_8));
         } catch (IOException var21) {
         }

         boolean var23 = var3.waitFor(var2, TimeUnit.SECONDS);
         if (!var23) {
            var3.destroyForcibly();
         }

         var3.waitFor(3L, TimeUnit.SECONDS);
         var7.join(1500L);
         var8.join(1500L);
         var10 = new Runner.Proc(var23 && !var6.get() ? var3.exitValue() : -1, var4.toString(StandardCharsets.UTF_8), var5.toString(StandardCharsets.UTF_8));
      } finally {
         if (var3.isAlive()) {
            var3.destroyForcibly();
         }
      }

      return var10;
   }

   static Thread drain(Process var0, InputStream var1, ByteArrayOutputStream var2, AtomicBoolean var3) {
      Thread var4 = new Thread(() -> {
         try {
            InputStream var4x = var1;

            try {
               byte[] var5 = new byte[2048];

               int var6;
               while ((var6 = var1.read(var5)) != -1) {
                  if (var2.size() + var6 > 65536) {
                     var3.set(true);
                     var0.destroyForcibly();
                     break;
                  }

                  var2.write(var5, 0, var6);
               }
            } catch (Throwable var8) {
               if (var1 != null) {
                  try {
                     var4x.close();
                  } catch (Throwable var7) {
                     var8.addSuppressed(var7);
                  }
               }

               throw var8;
            }

            if (var1 != null) {
               var1.close();
            }
         } catch (IOException var9) {
         }
      });
      var4.setDaemon(true);
      var4.start();
      return var4;
   }

   record Proc(int exit, String out, String err) {
   }
}
