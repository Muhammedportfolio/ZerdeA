import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName;

class SuiteTest extends Util {
   static int count;

   static void ok(boolean var0, String var1) {
      if (!var0) {
         throw new AssertionError(var1);
      } else {
         count++;
         System.out.println("PASS " + var1);
      }
   }

   static void rejects(SuiteTest.Op var0, String var1) throws Exception {
      try {
         var0.call();
         throw new AssertionError(var1 + " unexpectedly accepted");
      } catch (IllegalArgumentException var3) {
         ok(true, var1);
      }
   }

   static Map<String, Object> task() {
      return m(
         "title",
         "Циклы",
         "subject",
         "Программирование",
         "topic",
         "Java",
         "language",
         "Java",
         "contentLanguage",
         "ru",
         "level",
         "Лёгкий",
         "type",
         "quiz",
         "material",
         "",
         "published",
         true,
         "settings",
         m("minutes", 30, "attempts", 2, "deadline", 0, "shuffle", true, "reveal", "immediate", "groups", List.of(), "students", List.of()),
         "body",
         m(
            "questions",
            List.of(
               m(
                  "text",
                  "Сколько будет 2 + 2?",
                  "options",
                  List.of("3", "4", "5", "6"),
                  "correct",
                  1,
                  "points",
                  3,
                  "explanation",
                  "Сумма равна 4.",
                  "ref",
                  "",
                  "quote",
                  ""
               ),
               m(
                  "text",
                  "Сколько будет 3 + 3?",
                  "options",
                  List.of("3", "4", "5", "6"),
                  "correct",
                  3,
                  "points",
                  2,
                  "explanation",
                  "Сумма равна 6.",
                  "ref",
                  "",
                  "quote",
                  ""
               )
            )
         )
      );
   }

   public static void main(String[] var0) throws Exception {
      Path var1 = Files.createTempDirectory("suite-test-");

      String var3;
      try (Database var4 = new Database(var1)) {
         Service var5 = new Service(var4);
         Map var6 = var4.get("users", "teacher-main");
         String var7 = "qa-" + id();
         String var8 = salt();
         var6.put("salt", var8);
         var6.put("hash", hash(var7, var8));
         var4.put("users", var6);
         ok(
            s(var5.login(m("role", "teacher", "login", "Бақыт", "password", var7)), "name").equals("Бақыт Мұхаммедәлі қайырұлы"),
            "teacher full name and Cyrillic login"
         );
         Map var9 = obj(var5.groups(var6, m("name", "АЖ-401")));
         Map var10 = obj(var5.groups(var6, m("name", "АЖ-402")));
         Map var11 = var5.usersSave(var6, m("login", "Иванов", "name", "Иванов Иван Иванович", "password", "qa-student-one", "group", var9.get("id")));
         Map var12 = var5.usersSave(var6, m("login", "Иванов", "name", "Иванов Пётр Петрович", "password", "qa-student-two", "group", var10.get("id")));
         var3 = s(var11, "id");
         ok(!s(var11, "id").equals(s(var12, "id")), "duplicate surname with distinct passwords");
         ok(
            s(var5.login(m("role", "student", "login", "Иванов", "password", "qa-student-two")), "id").equals(s(var12, "id")),
            "login resolves correct duplicate surname"
         );
         rejects(
            () -> var5.usersSave(var6, m("login", "Иванов", "name", "Duplicate", "password", "qa-student-two", "group", "")),
            "duplicate complete credential pair rejected"
         );
         rejects(() -> var5.usersSave(var11, m("login", "x", "name", "x", "password", "qa-student-new", "group", "")), "student cannot create accounts");
         rejects(() -> var5.login(m("role", "teacher", "login", "Иванов", "password", "qa-student-two")), "role gate");
         Map var13 = task();
         obj(var13.get("settings")).put("groups", List.of(var9.get("id")));
         Map var14 = var5.saveTask(var6, var13);
         ok(list(var5.state(var11), "tasks").size() == 1 && list(var5.state(var12), "tasks").isEmpty(), "group assignment isolation");
         ok(!obj(list(var5.state(var11), "tasks").get(0)).containsKey("body"), "student catalog hides answers and tests");
         rejects(() -> var5.start(var12, m("id", var14.get("id"))), "unassigned student cannot start");
         Map var15 = var5.start(var11, m("id", var14.get("id")));
         ok(
            list(obj(var15.get("taskView")), "questions")
               .stream()
               .map(Util::obj)
               .noneMatch(var0x -> var0x.containsKey("correct") || var0x.containsKey("explanation")),
            "attempt strips correct answers"
         );
         String var16 = s(var15, "id");
         var5.autosave(var11, m("id", var16, "revision", 0, "answers", List.of(1, 0)));
         rejects(() -> var5.autosave(var11, m("id", var16, "revision", 0, "answers", List.of(0, 0))), "stale revision rejected");
         rejects(() -> var5.getAttempt(var12, var16), "other student attempt inaccessible");
         Map var17 = var5.submit(var11, m("id", var16, "revision", 1, "answers", List.of(1, 0)));
         ok(n(var17.get("score")) == 3 && n(var17.get("total")) == 5, "weighted grading");
         Map var18 = var5.submit(var11, m("id", var16, "answers", List.of(0, 0)));
         ok(n(var18.get("score")) == 3, "duplicate submission idempotent");
         Map draftVersion = copy(var14);
         obj(list(obj(draftVersion.get("body")), "questions").get(0)).put("correct", 0);
         Map var19 = var5.saveTask(var6, draftVersion);
         ok(n(var19.get("version")) == 2 && !s(var19, "id").equals(s(var14, "id")), "published edit creates version 2");
         ok(n(var4.get("attempts", var16).get("score")) == 3, "old scores stay tied to old version");
         ok(!yes(var4.get("tasks", s(var14, "id")), "active"), "old published version archived");
         Map var20 = obj(var5.correctGrade(var6, m("id", var16, "score", 4, "reason", "Частичный балл за объяснение")));
         ok(
            n(var20.get("originalScore")) == 3 && n(var20.get("score")) == 4 && list(var20, "history").size() == 1,
            "grade override preserves original and audit"
         );
         rejects(() -> var5.correctGrade(var11, m("id", var16, "score", 5, "reason", "x")), "student cannot alter grade");
         Map var21 = var5.start(var11, m("id", var19.get("id")));
         Map var22 = var4.get("attempts", s(var21, "id"));
         var22.put("expires", now() - 1L);
         var4.put("attempts", var22);
         var5.expire();
         ok(s(var4.get("attempts", s(var21, "id")), "status").equals("submitted"), "server timeout auto-submits saved answers");
         rejects(() -> var5.start(var11, m("id", var19.get("id"))), "attempt limit spans task versions");
         rejects(() -> var5.trashTask(var11, m("id", var19.get("id")), false), "student cannot trash tasks");
         var5.trashTask(var6, m("id", var19.get("id")), false);
         ok(!var5.available(var11, var4.get("tasks", s(var19, "id"))) && yes(var4.get("tasks", s(var19, "id")), "trashed"), "trash hides task from student");
         rejects(() -> var5.saveTask(var6, var19), "trashed task cannot be edited");
         ok(n(var4.get("attempts", var16).get("score")) == 4, "trash preserves grades");
         var5.trashTask(var6, m("id", var19.get("id")), true);
         ok(!yes(var4.get("tasks", s(var19, "id")), "active") && !yes(var4.get("tasks", s(var19, "id")), "trashed"), "restore does not republish");
         Map var24 = task();
         obj(var24.get("settings")).put("reveal", "after");
         obj(var24.get("settings")).put("deadline", now() + 600000L);
         Map var25 = var5.saveTask(var6, var24);
         Map var26 = var5.start(var11, m("id", var25.get("id")));
         Map var27 = var5.submit(var11, m("id", var26.get("id"), "revision", 0, "answers", List.of(1, 3)));
         ok(!var27.containsKey("details") && !yes(var27, "reveal"), "answers withheld until deadline");
         Map var28 = var4.get("tasks", s(var25, "id"));
         obj(var28.get("settings")).put("deadline", now() - 1L);
         var4.put("tasks", var28);
         ok(var5.resultPublic(var11, var4.get("attempts", s(var26, "id"))).containsKey("details"), "answers released after deadline");
         PDDocument var30 = new PDDocument();

         byte[] var29;
         try {
            PDPage var31 = new PDPage();
            var30.addPage(var31);
            PDPageContentStream var32 = new PDPageContentStream(var30, var31);

            try {
               var32.beginText();
               var32.setFont(new PDType1Font(FontName.HELVETICA), 12.0F);
               var32.newLineAtOffset(40.0F, 700.0F);
               var32.showText("Java loop repeats statements.");
               var32.endText();
            } catch (Throwable var49) {
               try {
                  var32.close();
               } catch (Throwable var47) {
                  var49.addSuppressed(var47);
               }

               throw var49;
            }

            var32.close();
            ByteArrayOutputStream var58 = new ByteArrayOutputStream();
            var30.save(var58);
            var29 = var58.toByteArray();
         } catch (Throwable var50) {
            try {
               var30.close();
            } catch (Throwable var46) {
               var50.addSuppressed(var46);
            }

            throw var50;
         }

         var30.close();
         List var56 = Documents.extract("course.pdf", var29);
         ok(s(obj(var56.get(0)), "ref").equals("p1") && s(obj(var56.get(0)), "text").contains("Java loop"), "PDF text extracted with page reference");
         ByteArrayOutputStream var57 = new ByteArrayOutputStream();

         try (ZipOutputStream var59 = new ZipOutputStream(var57)) {
            var59.putNextEntry(new ZipEntry("word/document.xml"));
            var59.write(
               "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body><w:p><w:r><w:t>Цикл повторяет действия.</w:t></w:r></w:p></w:body></w:document>"
                  .getBytes(StandardCharsets.UTF_8)
            );
            var59.closeEntry();
         }

         ok(s(obj(Documents.extract("course.docx", var57.toByteArray()).get(0)), "text").contains("Цикл"), "DOCX text extracted");
         Map var60 = obj(
            var5.upload(
               var6, m("filename", "course.pdf", "data", Base64.getEncoder().encodeToString(var29), "title", "Основы", "subject", "Java", "topic", "Циклы")
            )
         );
         ok(!var60.containsKey("data") && var4.get("materials", s(var60, "id")).containsKey("data"), "material stored with original and extracted pages");
         Map var33 = task();
         var33.put("material", var60.get("id"));
         Map var34 = obj(list(obj(var33.get("body")), "questions").get(0));
         var34.put("ref", "p1");
         var34.put("quote", "Java loop repeats statements.");
         var5.saveTask(var6, var33);
         var34.put("quote", "Invented source");
         rejects(() -> var5.saveTask(var6, var33), "fabricated quote rejected");
         BufferedImage var35 = new BufferedImage(400, 200, 1);
         ByteArrayOutputStream var36 = new ByteArrayOutputStream();
         ImageIO.write(var35, "png", var36);
         Map var37 = var5.profile(var11, m("avatar", Base64.getEncoder().encodeToString(var36.toByteArray())));
         ok(s(var37, "avatar").startsWith("data:image/jpeg;base64,"), "profile photo normalized");
         var5.profile(var11, m("avatar", "", "language", "kk"));
         ok(s(var5.user(s(var11, "id")), "avatar").isBlank() && s(var5.user(s(var11, "id")), "language").equals("kk"), "photo deletion and Kazakh preference");
         var5.usersSave(var6, m("id", var11.get("id"), "login", "Иванов", "name", var11.get("name"), "password", "qa-reset-pass", "group", var9.get("id")));
         rejects(() -> var5.login(m("role", "student", "login", "Иванов", "password", "qa-student-one")), "old password invalid after teacher reset");
         ok(s(var5.login(m("role", "student", "login", "Иванов", "password", "qa-reset-pass")), "id").equals(s(var11, "id")), "new password after reset works");
         var5.ai.configure(s(var6, "id"), m("provider", "openai", "model", "gpt-4o-mini", "key", "test-only-placeholder"));
         ok(!var5.ai.publicConfig(s(var6, "id")).containsKey("key"), "AI keys not returned to browser");
         Map var2 = var4.backup();
         ok(!Json.write(var2).contains("test-only-placeholder"), "API keys excluded from backup");
         byte[] var38 = Excel.export(List.of(var20));
         Files.write(var1.resolve("results.xlsx"), var38);

         try (ZipInputStream var39 = new ZipInputStream(new ByteArrayInputStream(var38))) {
            int var40 = 0;

            while (var39.getNextEntry() != null) {
               var40++;
            }

            ok(var40 == 5, "Excel file has valid OOXML package parts");
         }

         Map var61 = copy(var2);
         obj(list(var61, "attempts").get(0)).put("task", "missing");
         rejects(() -> var4.restore(var61), "invalid backup references rejected before mutation");
         ok(var4.all("attempts").size() == 3, "failed restore leaves database intact");
         var4.restore(var2);
         ok(var4.all("materials").size() == 1 && var4.all("users").size() == 3, "backup restore preserves users materials and attempts");
      }

      try (Database var54 = new Database(var1)) {
         ok(var54.all("attempts").size() == 3 && var54.get("users", var3) != null, "H2 survives restart");
      }

      System.out.println("TOTAL " + count + " checks passed");
   }

   interface Op {
      void call() throws Exception;
   }
}
