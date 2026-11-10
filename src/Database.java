import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

class Database extends Util implements AutoCloseable {
   final Connection c;
   final Path home;
   final boolean postgres;
   final String provider;

   Database(Path var1) throws Exception {
      this(var1, false);
   }

   Database(Path var1, boolean forceH2) throws Exception {
      this.home = var1;
      Files.createDirectories(var1);
      String url = System.getenv("ZERDEAI_DB_URL");
      String user = System.getenv("ZERDEAI_DB_USER");
      String password = System.getenv("ZERDEAI_DB_PASSWORD");
      boolean anyPostgresSetting = !forceH2 && ((url != null && !url.isBlank()) || (user != null && !user.isBlank()) || (password != null && !password.isEmpty()));
      if (anyPostgresSetting) {
         if (url == null || user == null || password == null || url.isBlank() || user.isBlank() || password.isEmpty()) {
            throw new IllegalStateException("Для Supabase нужны все три настройки ZERDEAI_DB_URL, ZERDEAI_DB_USER и ZERDEAI_DB_PASSWORD. H2 автоматически не включён.");
         }
         if (!url.startsWith("jdbc:postgresql://") || !url.matches("(?i).*([?&])sslmode=verify-full(&.*)?$")) {
            throw new IllegalStateException("Подключение PostgreSQL должно использовать jdbc:postgresql:// и sslmode=verify-full.");
         }
         Class.forName("org.postgresql.Driver");
         this.postgres = true;
         this.provider = "Supabase";
         this.c = DriverManager.getConnection(url, user, password);
         try (Statement statement = this.c.createStatement()) {
            statement.executeQuery("SELECT kind,id,body FROM zerdeai.records LIMIT 0").close();
         } catch (Exception exception) {
            try { this.c.close(); } catch (Exception ignored) { }
            throw new IllegalStateException("Supabase доступен, но закрытая таблица zerdeai.records не найдена или роль не имеет прав. Проверьте миграцию и роль подключения.", exception);
         }
      } else {
         this.postgres = false;
         this.provider = "H2";
         this.c = DriverManager.getConnection("jdbc:h2:" + var1.resolve("database").toAbsolutePath() + ";DB_CLOSE_ON_EXIT=FALSE", "sa", "");
         try (Statement var2 = this.c.createStatement()) {
            var2.execute("CREATE TABLE IF NOT EXISTS records(kind VARCHAR(30), id VARCHAR(60), body CLOB NOT NULL, PRIMARY KEY(kind,id))");
         }
      }
   }

   private String table() { return this.postgres ? "zerdeai.records" : "records"; }

   synchronized List<Map<String, Object>> allRecords() throws Exception {
      ArrayList<Map<String, Object>> rows = new ArrayList<>();
      try (Statement statement = this.c.createStatement(); ResultSet result = statement.executeQuery(
         "SELECT kind,id," + (this.postgres ? "body::text" : "body") + " FROM " + this.table() + " ORDER BY kind,id")) {
         while (result.next()) {
            Map<String, Object> body = obj(Json.parse(result.getString(3)));
            check(s(body, "id").equals(result.getString(2)), "Идентификатор в JSON не совпадает со столбцом id");
            rows.add(m("kind", result.getString(1), "id", result.getString(2), "body", body));
         }
      }
      return rows;
   }

   synchronized void insertNewRecord(Map<String, Object> row) throws Exception {
      String kind = req(row, "kind", 30);
      String id = req(row, "id", 60);
      Map<String, Object> body = obj(row.get("body"));
      check(req(body, "id", 60).equals(id), "Идентификатор в JSON не совпадает со столбцом id");
      String sql = this.postgres
         ? "INSERT INTO zerdeai.records(kind,id,body) VALUES(?,?,?::jsonb)"
         : "INSERT INTO records(kind,id,body) VALUES(?,?,?)";
      try (PreparedStatement statement = this.c.prepareStatement(sql)) {
         statement.setString(1, kind);
         statement.setString(2, id);
         statement.setString(3, Json.write(body));
         statement.executeUpdate();
      }
   }

   synchronized List<Map<String, Object>> all(String var1) throws Exception {
      ArrayList var2 = new ArrayList();

      try (PreparedStatement var3 = this.c.prepareStatement("SELECT " + (this.postgres ? "body::text" : "body") + " FROM " + this.table() + " WHERE kind=? ORDER BY id")) {
         var3.setString(1, var1);

         try (ResultSet var4 = var3.executeQuery()) {
            while (var4.next()) {
               var2.add(obj(Json.parse(var4.getString(1))));
            }
         }
      }

      return var2;
   }

   synchronized Map<String, Object> get(String var1, String var2) throws Exception {
      try (PreparedStatement var3 = this.c.prepareStatement("SELECT " + (this.postgres ? "body::text" : "body") + " FROM " + this.table() + " WHERE kind=? AND id=?")) {
         var3.setString(1, var1);
         var3.setString(2, var2);

         try (ResultSet var4 = var3.executeQuery()) {
            if (var4.next()) {
               return obj(Json.parse(var4.getString(1)));
            }
         }
      }

      throw new IllegalArgumentException("Запись не найдена: " + var1);
   }

   synchronized void put(String var1, Map<String, Object> var2) throws Exception {
      String sql = this.postgres
         ? "INSERT INTO zerdeai.records(kind,id,body) VALUES(?,?,?::jsonb) ON CONFLICT(kind,id) DO UPDATE SET body=EXCLUDED.body"
         : "MERGE INTO records(kind,id,body) KEY(kind,id) VALUES(?,?,?)";
      try (PreparedStatement var3 = this.c.prepareStatement(sql)) {
         var3.setString(1, var1);
         var3.setString(2, req(var2, "id", 60));
         var3.setString(3, Json.write(var2));
         var3.executeUpdate();
      }
   }

   synchronized void delete(String var1, String var2) throws Exception {
      try (PreparedStatement var3 = this.c.prepareStatement("DELETE FROM " + this.table() + " WHERE kind=? AND id=?")) {
         var3.setString(1, var1);
         var3.setString(2, var2);
         var3.executeUpdate();
      }
   }

   synchronized Map<String, Object> backup() throws Exception {
      Map var1 = m("format", "EduTestSuite-2", "created", now());

      for (String var3 : List.of("users", "groups", "materials", "tasks", "attempts", "audit", "chats", "chatMessages", "chatAttachments", "courses", "courseProgress", "tutorDialogs", "tutorMessages")) {
         var1.put(var3, this.all(var3));
      }

      return var1;
   }

   synchronized void restore(Map<String, Object> var1) throws Exception {
      check(s(var1, "format").equals("EduTestSuite-2"), "Это не резервная копия EduTest Suite 2");
      List<String> var2 = List.of("users", "groups", "materials", "tasks", "attempts", "audit", "chats", "chatMessages", "chatAttachments", "courses", "courseProgress", "tutorDialogs", "tutorMessages");

      for (String var4 : var2) {
         HashSet var5 = new HashSet();

         for (Object var7 : list(var1, var4)) {
            Map var8 = obj(var7);
            check(var5.add(req(var8, "id", 60)), "Повторяющийся ID в копии");
         }
      }

      check(
         list(var1, "users")
            .stream()
            .map(Util::obj)
            .anyMatch(var0 -> s((Map<String, Object>)var0, "role").equals("teacher") && yes((Map<String, Object>)var0, "active")),
         "В копии нет активного преподавателя"
      );
      HashSet var19 = new HashSet();
      HashSet var20 = new HashSet();
      HashSet var21 = new HashSet();

      for (Object var28 : list(var1, "users")) {
         Map var33 = obj(var28);
         var19.add(req(var33, "id", 60));
         req(var33, "login", 80);
         req(var33, "name", 160);
         check(List.of("teacher", "student").contains(s(var33, "role")), "Неверная роль в копии");
         check(
            Base64.getDecoder().decode(req(var33, "salt", 100)).length == 24 && Base64.getDecoder().decode(req(var33, "hash", 200)).length == 32,
            "Неверный хеш пароля в копии"
         );
      }

      for (Object var29 : list(var1, "groups")) {
         var20.add(s(obj(var29), "id"));
      }

      for (Object var30 : list(var1, "tasks")) {
         Map var34 = obj(var30);
         var21.add(s(var34, "id"));
         check(var19.contains(s(var34, "owner")), "Нет владельца задания в копии");
         obj(var34.get("body"));
         obj(var34.get("settings"));
      }

      for (Object var31 : list(var1, "attempts")) {
         Map var35 = obj(var31);
         check(var19.contains(s(var35, "user")) && var19.contains(s(var35, "owner")) && var21.contains(s(var35, "task")), "Нарушены связи попыток в копии");
      }

      HashSet courseIds=new HashSet(), chatIds=new HashSet(), attachmentIds=new HashSet();
      for(Object value:list(var1,"courses")) {
         Map course=obj(value); String owner=req(course,"owner",60); check(var19.contains(owner),"Нет владельца курса в копии");
         for(Object group:list(course,"groups")) check(var20.contains(String.valueOf(group)),"Курс назначен отсутствующей группе");
         for(Object student:list(course,"students")) check(var19.contains(String.valueOf(student)),"Курс назначен отсутствующему студенту");
         for(Object section:list(course,"sections")){ Map sec=obj(section); req(sec,"id",100); for(Object lesson:list(sec,"lessons")){Map l=obj(lesson);req(l,"id",100);req(l,"title",180);} }
         courseIds.add(req(course,"id",60));
      }
      for(Object value:list(var1,"chats")){
         Map chat=obj(value); String teacher=req(chat,"teacher",60),student=req(chat,"student",60);
         check(var19.contains(teacher)&&var19.contains(student),"Диалог ссылается на отсутствующего пользователя");
         check(s(obj(list(var1,"users").stream().filter(u->s(obj(u),"id").equals(teacher)).findFirst().orElseThrow()),"role").equals("teacher")&&s(obj(list(var1,"users").stream().filter(u->s(obj(u),"id").equals(student)).findFirst().orElseThrow()),"role").equals("student"),"Неверные участники диалога");
         chatIds.add(req(chat,"id",60));
      }
      for(Object value:list(var1,"chatMessages")){
         Map msg=obj(value); check(chatIds.contains(s(msg,"chat")),"Сообщение ссылается на отсутствующий диалог");
         Map chat=obj(list(var1,"chats").stream().filter(c->s(obj(c),"id").equals(s(msg,"chat"))).findFirst().orElseThrow());
         check(s(msg,"from").equals(s(chat,"teacher"))||s(msg,"from").equals(s(chat,"student")),"Отправитель сообщения не состоит в диалоге");
      }
      for(Object value:list(var1,"chatAttachments")){
         Map attachment=obj(value); String chatId=req(attachment,"chat",60), owner=req(attachment,"from",60);
         check(chatIds.contains(chatId)&&var19.contains(owner),"Вложение ссылается на отсутствующий диалог или пользователя");
         Map chat=obj(list(var1,"chats").stream().filter(c->s(obj(c),"id").equals(chatId)).findFirst().orElseThrow());
         check(owner.equals(s(chat,"teacher"))||owner.equals(s(chat,"student")),"Вложение принадлежит постороннему пользователю");
         attachmentIds.add(req(attachment,"id",60));
      }
      for(Object value:list(var1,"chatMessages")){
         Map msg=obj(value);
         for(Object attachmentId:list(msg,"attachments")){
            String id=String.valueOf(attachmentId);
            check(attachmentIds.contains(id),"Сообщение ссылается на отсутствующее вложение");
            Map attachment=obj(list(var1,"chatAttachments").stream().filter(a->s(obj(a),"id").equals(id)).findFirst().orElseThrow());
            check(s(attachment,"chat").equals(s(msg,"chat"))&&s(attachment,"from").equals(s(msg,"from")),"Вложение прикреплено к чужому сообщению");
         }
      }
      for(Object value:list(var1,"courseProgress")){Map p=obj(value);check(var19.contains(s(p,"user"))&&courseIds.contains(s(p,"course")),"Прогресс ссылается на отсутствующего пользователя или курс");}
      HashSet dialogIds=new HashSet(); for(Object value:list(var1,"tutorDialogs")){Map d=obj(value);check(var19.contains(s(d,"user")),"Диалог тьютора ссылается на отсутствующего пользователя");dialogIds.add(s(d,"id"));}
      for(Object value:list(var1,"tutorMessages")){Map msg=obj(value);check(var19.contains(s(msg,"user"))&&dialogIds.contains(s(msg,"dialog")),"Сообщение тьютора ссылается на отсутствующий диалог или пользователя");}

      this.c.setAutoCommit(false);

      try {
         try (Statement var26 = this.c.createStatement()) {
            var26.executeUpdate("DELETE FROM " + this.table());
         }

         for (String var32 : var2) {
            for (Object var9 : list(var1, var32)) {
               this.put(var32, obj(var9));
            }
         }

         this.c.commit();
      } catch (Exception var17) {
         this.c.rollback();
         throw var17;
      } finally {
         this.c.setAutoCommit(true);
      }
   }

   @Override
   public synchronized void close() throws Exception {
      this.c.close();
   }
}
