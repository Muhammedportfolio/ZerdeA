import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Safe, one-way H2 -> empty Supabase import. Never updates or deletes destination records. */
public class SupabaseMigration extends Util {
   private static final List<String> DOMAIN_KINDS = List.of(
      "users", "groups", "materials", "tasks", "attempts", "audit", "chats", "chatMessages",
      "chatAttachments", "courses", "courseProgress", "tutorDialogs", "tutorMessages"
   );

   public static void main(String[] args) throws Exception {
      if (args.length != 3 || !(args[0].equals("--export") || args[0].equals("--apply"))) {
         throw new IllegalArgumentException("Использование: SupabaseMigration --export <папка-H2> <новый-файл-backup.json> | --apply <папка-H2> <файл-backup.json>");
      }
      Path sourcePath = Path.of(args[1]).toAbsolutePath().normalize();
      Path backupPath = Path.of(args[2]).toAbsolutePath().normalize();
      List<Map<String, Object>> sourceRows;
      try (Database source = new Database(sourcePath, true)) {
         sourceRows = source.allRecords();
      }
      validateDomain(sourceRows);
      showCounts("Данные H2 после проверки", sourceRows);

      if (args[0].equals("--export")) {
         Map<String, Object> backup = m("format", "ZerdeAI-Postgres-Migration-1", "created", now(), "records", sourceRows);
         Files.createDirectories(backupPath.getParent());
         Files.writeString(backupPath, Json.write(backup), java.nio.charset.StandardCharsets.UTF_8,
            java.nio.file.StandardOpenOption.CREATE_NEW, java.nio.file.StandardOpenOption.WRITE);
         System.out.println("Создана новая резервная копия (без перезаписи): " + backupPath);
         System.out.println("Файл содержит учётные данные/переписку. Храните его локально и не загружайте в GitHub.");
         return;
      }

      Map<String, Object> backup = obj(Json.parse(Files.readString(backupPath, java.nio.charset.StandardCharsets.UTF_8)));
      check(s(backup, "format").equals("ZerdeAI-Postgres-Migration-1"), "Неверный формат файла переноса");
      List<Map<String, Object>> savedRows = arr(backup.get("records")).stream().map(Util::obj).toList();
      check(savedRows.equals(sourceRows), "H2 уже отличается от резервной копии. Остановитесь, создайте новую копию и повторите dry-run.");
      migrateToEmptySupabase(savedRows);
   }

   private static void validateDomain(List<Map<String, Object>> rows) throws Exception {
      Map<String, Object> snapshot = m("format", "EduTestSuite-2");
      for (String kind : DOMAIN_KINDS) snapshot.put(kind, new ArrayList<>());
      for (Map<String, Object> row : rows) {
         String kind = req(row, "kind", 30);
         String id = req(row, "id", 60);
         Map<String, Object> body = obj(row.get("body"));
         check(req(body, "id", 60).equals(id), "ID записи " + kind + " не совпадает с ID внутри JSON");
         if (DOMAIN_KINDS.contains(kind)) list(snapshot, kind).add(body);
      }
      Path validationHome = Files.createTempDirectory("zerdeai-h2-migration-check-");
      try (Database validation = new Database(validationHome, true)) {
         validation.restore(snapshot);
      } finally {
         try (var paths = Files.walk(validationHome)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
         }
      }
   }

   private static void migrateToEmptySupabase(List<Map<String, Object>> rows) throws Exception {
      Path temporaryHome = Files.createTempDirectory("zerdeai-supabase-verify-");
      try (Database cloud = new Database(temporaryHome)) {
         check(cloud.postgres, "ZERDEAI_DB_URL/USER/PASSWORD не заданы. Supabase не подключён; импорт остановлен.");
         check(cloud.allRecords().isEmpty(), "Supabase уже содержит записи. Импорт остановлен без изменений, чтобы ничего не перезаписывать.");
         cloud.c.setAutoCommit(false);
         try {
            for (Map<String, Object> row : rows) cloud.insertNewRecord(row);
            List<Map<String, Object>> written = cloud.allRecords();
            check(written.equals(rows), "Проверка строк после вставки не совпала; откатываю транзакцию.");
            check(counts(written).equals(counts(rows)), "Количество записей по типам не совпало; откатываю транзакцию.");
            cloud.c.commit();
         } catch (Exception exception) {
            cloud.c.rollback();
            throw exception;
         } finally {
            cloud.c.setAutoCommit(true);
         }
      } finally {
         try (var paths = Files.walk(temporaryHome)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
         }
      }

      Path verifyHome = Files.createTempDirectory("zerdeai-supabase-restart-check-");
      try (Database reopened = new Database(verifyHome)) {
         List<Map<String, Object>> afterRestart = reopened.allRecords();
         check(reopened.postgres && afterRestart.equals(rows) && counts(afterRestart).equals(counts(rows)),
            "После повторного подключения проверка облачных данных не совпала. Сохраните резервную копию и проверьте доступ к Supabase.");
         System.out.println("Supabase записал данные и повторное JDBC-подключение прочитало те же записи.");
         showCounts("Проверено после повторного подключения", afterRestart);
      } finally {
         try (var paths = Files.walk(verifyHome)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
         }
      }
   }

   private static Map<String, Integer> counts(List<Map<String, Object>> rows) {
      Map<String, Integer> counts = new TreeMap<>();
      for (Map<String, Object> row : rows) counts.merge(s(row, "kind"), 1, Integer::sum);
      return counts;
   }

   private static void showCounts(String title, List<Map<String, Object>> rows) {
      System.out.println(title + " (" + rows.size() + " строк):");
      for (Map.Entry<String, Integer> entry : counts(rows).entrySet()) System.out.println("  " + entry.getKey() + ": " + entry.getValue());
   }
}
