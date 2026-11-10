import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class SupabaseMigrationTest extends Util {
   private static int checks;

   public static void main(String[] args) throws Exception {
      Path root = Files.createTempDirectory("zerdeai-migration-test-");
      Path h2Home = root.resolve("source");
      Path backup = root.resolve("safe copy.migration-backup.json");
      try {
         try (Database source = new Database(h2Home, true)) {
            new Service(source); // seeds the normal initial teacher in the temporary database only
            source.put("zerde-admin", m("id", "root", "login", "temporary-admin", "salt", "test", "hash", "test"));
         }
         SupabaseMigration.main(new String[]{"--export", h2Home.toString(), backup.toString()});
         Map<String, Object> snapshot = obj(Json.parse(Files.readString(backup)));
         ok(s(snapshot, "format").equals("ZerdeAI-Postgres-Migration-1"), "backup format created");
         boolean adminIncluded = arr(snapshot.get("records")).stream().map(Util::obj)
            .anyMatch(row -> s(row, "kind").equals("zerde-admin") && s(row, "id").equals("root"));
         ok(adminIncluded, "non-domain admin account included in full record export");

         try {
            SupabaseMigration.main(new String[]{"--apply", h2Home.toString(), backup.toString()});
            throw new AssertionError("apply should fail closed without a PostgreSQL connection");
         } catch (IllegalArgumentException expected) {
            ok(expected.getMessage().contains("Supabase не подключён"), "apply refuses to fall back to H2");
         }
         try {
            SupabaseMigration.main(new String[]{"--export", h2Home.toString(), backup.toString()});
            throw new AssertionError("export should not overwrite an existing backup");
         } catch (java.nio.file.FileAlreadyExistsException expected) {
            ok(true, "existing backup is not overwritten");
         }
         System.out.println("TOTAL " + checks + " Supabase migration checks passed");
      } finally {
         try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
         }
      }
   }

   private static void ok(boolean condition, String name) {
      checks++;
      if (!condition) throw new AssertionError(name);
      System.out.println("PASS " + name);
   }
}
