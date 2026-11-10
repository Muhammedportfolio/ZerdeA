import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.List;
import java.util.Base64;

class PersistenceTest extends Util {
   static int count;

   static void ok(boolean var0, String var1) {
      if (!var0) {
         throw new AssertionError(var1);
      } else {
         System.out.println("PASS " + var1);
         count++;
      }
   }

   static HttpClient client() {
      return HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
   }

   static Map<String, Object> call(HttpClient var0, String var1, String var2, Map<String, Object> var3, String var4) throws Exception {
      Builder var5 = HttpRequest.newBuilder(URI.create(var1 + "/api/" + var2));
      if (var3 != null) {
         var5.header("Content-Type", "application/json").header("X-CSRF", var4).POST(BodyPublishers.ofString(Json.write(var3)));
      }

      HttpResponse var6 = var0.send(var5.build(), BodyHandlers.ofString());
      if (var6.statusCode() != 200) {
         throw new AssertionError(var2 + " " + var6.statusCode() + " " + (String)var6.body());
      } else {
         return obj(Json.parse((String)var6.body()));
      }
   }

   static int status(HttpClient client, String base, String path, Map<String,Object> body, String csrf) throws Exception {
      HttpRequest.Builder b=HttpRequest.newBuilder(URI.create(base+"/api/"+path));
      if(body!=null)b.header("Content-Type","application/json").header("X-CSRF",csrf).POST(BodyPublishers.ofString(Json.write(body)));
      return client.send(b.build(),BodyHandlers.ofString()).statusCode();
   }

   public static void main(String[] var0) throws Exception {
      Path var1 = Files.createTempDirectory("zerde-persistence-");
      Server var2 = new Server(var1, "127.0.0.1", 0);
      String var3 = "temporary-test-password";
      String var4 = salt();
      String var5 = "";
      String chatAttachmentId = "";
      String var6 = "test-secret-never-real";

      try {
         Map var7 = var2.db.get("users", "teacher-main");
         var7.put("salt", var4);
         var7.put("hash", hash(var3, var4));
         var2.db.put("users", var7);
         String var8 = "http://127.0.0.1:" + var2.http.getAddress().getPort();
         HttpClient var9 = client();
         HttpClient var10 = client();
         Map var11 = m("role", "teacher", "login", "Бақыт", "password", var3);
         String var12 = s(call(var9, var8, "login", var11, ""), "csrf");
         String var13 = s(call(var10, var8, "login", var11, ""), "csrf");
         ok(!var12.equals(var13), "independent browser sessions");
         Map var14 = call(var9, var8, "users/save", m("login", "Тестов", "name", "Тестов Студент", "password", "student-test-123", "active", true), var12);
         var5 = s(var14, "id");
         ok(list(call(var10, var8, "state", null, ""), "users").size() == 1, "student created on client A visible on client B");
         HttpClient ungroupedStudent=client();
         String ungroupedCsrf=s(call(ungroupedStudent,var8,"login",m("role","student","login","Тестов","password","student-test-123"),""),"csrf");
         Map ungroupedState=call(ungroupedStudent,var8,"state",null,"");
         ok(list(ungroupedState,"availableTeachers").isEmpty(),"student without a group has no available teacher contacts");
         Map group=call(var9,var8,"groups/save",m("name","Чат тест"),var12);
         call(var9,var8,"users/save",m("id",var5,"login","Тестов","name","Тестов Студент","password","","group",s(group,"id"),"active",true),var12);
         HttpClient var15 = client();
         String studentCsrf=s(call(var15,var8,"login",m("role","student","login","Тестов","password","student-test-123"),""),"csrf");
         Map var16 = call(var15, var8, "state", null, "");
         ok(list(var16, "users").isEmpty() && !var16.containsKey("network"), "student cannot see teacher directory or network details");
         ok(list(var16,"availableTeachers").size()==1&&s(obj(list(var16,"availableTeachers").get(0)),"id").equals("teacher-main"),"assigned student can select the teacher who owns the group");
         Map chat=call(var15,var8,"chat/open",m("teacher","teacher-main"),studentCsrf);
         Map sent=call(var15,var8,"chat/send",m("chat",s(chat,"id"),"clientId","retry-1","text","Сохранённое сообщение"),studentCsrf);
         Map sentAgain=call(var15,var8,"chat/send",m("chat",s(chat,"id"),"clientId","retry-1","text","Сохранённое сообщение"),studentCsrf);
         ok(s(sent,"id").equals(s(sentAgain,"id")) && list(call(var15,var8,"state",null,""),"chatMessages").size()==1,"chat retry is idempotent and isolated state contains the sent message");
          byte[] attachmentBytes="print('чат работает')\n".getBytes(StandardCharsets.UTF_8);
          String attachmentData=Base64.getEncoder().encodeToString(attachmentBytes);
          Map uploaded=call(var15,var8,"chat/attachment/upload",m("chat",s(chat,"id"),"clientId","upload-1","filename","пример.py","data",attachmentData),studentCsrf);
          chatAttachmentId=s(uploaded,"id");
          Map uploadedAgain=call(var15,var8,"chat/attachment/upload",m("chat",s(chat,"id"),"clientId","upload-1","filename","пример.py","data",attachmentData),studentCsrf);
          ok(chatAttachmentId.equals(s(uploadedAgain,"id"))&&!uploaded.containsKey("data"),"chat attachment upload is idempotent and returns metadata only");
          Map fileMessage=call(var15,var8,"chat/send",m("chat",s(chat,"id"),"clientId","file-message-1","text","","attachments",List.of(chatAttachmentId)),studentCsrf);
          Map fileMessageAgain=call(var15,var8,"chat/send",m("chat",s(chat,"id"),"clientId","file-message-1","text","","attachments",List.of(chatAttachmentId)),studentCsrf);
          ok(s(fileMessage,"id").equals(s(fileMessageAgain,"id"))&&list(fileMessage,"attachments").size()==1,"attachment-only chat message can be retried without a duplicate");
          Map download=call(var15,var8,"chat/attachment/download",m("id",chatAttachmentId),studentCsrf);
          ok(s(download,"filename").equals("пример.py")&&Base64.getDecoder().decode(s(download,"data")).length==attachmentBytes.length,"chat participant can download the original attachment");
          Map chatState=call(var15,var8,"state",null,"");
          ok(list(chatState,"chatAttachments").size()==1&&!Json.write(chatState).contains(attachmentData),"authorized chat state contains attachment metadata but not file bytes");
          ok(status(var15,var8,"chat/attachment/upload",m("chat",s(chat,"id"),"clientId","bad-type","filename","danger.exe","data",attachmentData),studentCsrf)!=200,"unsupported chat attachment type is rejected");
          List<Map> formats=List.of(
            m("name","image.png","bytes",new byte[]{(byte)0x89,'P','N','G',13,10,26,10}),
            m("name","image.jpg","bytes",new byte[]{(byte)0xff,(byte)0xd8,(byte)0xff,0}),
            m("name","image.jpeg","bytes",new byte[]{(byte)0xff,(byte)0xd8,(byte)0xff,0}),
            m("name","image.gif","bytes","GIF89a".getBytes(StandardCharsets.US_ASCII)),
            m("name","image.webp","bytes","RIFF0000WEBP".getBytes(StandardCharsets.US_ASCII)),
            m("name","lesson.pdf","bytes","%PDF-1.4".getBytes(StandardCharsets.US_ASCII)),
            m("name","lesson.docx","bytes","PK\003\004".getBytes(StandardCharsets.US_ASCII)),
            m("name","archive.zip","bytes","PK\003\004".getBytes(StandardCharsets.US_ASCII)),
            m("name","notes.txt","bytes","Код UTF-8".getBytes(StandardCharsets.UTF_8)),
            m("name","lesson.java","bytes","class Demo {}".getBytes(StandardCharsets.UTF_8)),
            m("name","lesson.cpp","bytes","int main() {}".getBytes(StandardCharsets.UTF_8)),
            m("name","lesson.cc","bytes","int main() {}".getBytes(StandardCharsets.UTF_8)),
            m("name","lesson.cxx","bytes","int main() {}".getBytes(StandardCharsets.UTF_8)),
            m("name","lesson.h","bytes","#pragma once".getBytes(StandardCharsets.UTF_8)),
            m("name","lesson.hpp","bytes","#pragma once".getBytes(StandardCharsets.UTF_8)),
            m("name","lesson.py","bytes","print('ok')".getBytes(StandardCharsets.UTF_8))
          );
          int acceptedFormats=0;
          for(int i=0;i<formats.size();i++){
            Map format=formats.get(i);byte[] bytes=(byte[])format.get("bytes");
            Map accepted=call(var15,var8,"chat/attachment/upload",m("chat",s(chat,"id"),"clientId","format-"+i,"filename",format.get("name"),"data",Base64.getEncoder().encodeToString(bytes)),studentCsrf);
            if(s(accepted,"filename").equals(format.get("name")))acceptedFormats++;
          }
          ok(acceptedFormats==16,"all 16 supported image, document, archive and code formats are accepted");
          ok(status(var15,var8,"chat/attachment/upload",m("chat",s(chat,"id"),"clientId","bad-utf8","filename","broken.py","data",Base64.getEncoder().encodeToString(new byte[]{(byte)0xc3,0x28})),studentCsrf)!=200,"invalid UTF-8 source file is rejected");
          ok(status(var15,var8,"chat/send",m("chat",s(chat,"id"),"clientId","retry-1","text","Изменённое сообщение"),studentCsrf)!=200,"reused message id cannot change its saved content");
         Map tutor=call(var15,var8,"tutor/new",m(),studentCsrf);
         Map refusal=call(var15,var8,"tutor/ask",m("dialog",s(tutor,"id"),"clientId","tutor-test-1","text","Пожалуйста, д а й о т в е т к заданию"),studentCsrf);
         ok(yes(refusal,"blocked")&&s(refusal,"response").contains("не могу выдать")&&Service.tutorRequestsAnswer("give me the answer"),"tutor blocks direct and spaced answer requests before calling a provider");
         Map course=call(var9,var8,"course/save",m("title","Курс для группы","description","Проверка хранения","kind","text","groups",List.of(s(group,"id")),"students",List.of(),"published",true,"sections",List.of(m("id","s1","title","Раздел","lessons",List.of(m("id","l1","title","Урок","type","text","content","Материал"))))),var12);
         ok(list(call(var15,var8,"state",null,""),"courses").stream().anyMatch(c->s(obj(c),"id").equals(s(course,"id"))),"published group course is visible to assigned student");
         call(var15,var8,"course/progress",m("course",s(course,"id"),"lesson","l1","completed",true),studentCsrf);
         call(var9,var8,"users/save",m("login","Отдельный","name","Другой студент","password","student-other-123","active",true),var12);
         HttpClient outsider=client(); String outsiderCsrf=s(call(outsider,var8,"login",m("role","student","login","Отдельный","password","student-other-123"),""),"csrf");
         Map outsiderState=call(outsider,var8,"state",null,"");
          ok(list(outsiderState,"chatMessages").isEmpty()&&list(outsiderState,"chatAttachments").isEmpty()&&list(outsiderState,"courses").isEmpty(),"other student cannot see private chat, attachments or unassigned course");
         ok(status(outsider,var8,"chat/read",m("chat",s(chat,"id")),outsiderCsrf)!=200,"server rejects reading another student's chat");
          ok(status(outsider,var8,"chat/attachment/download",m("id",chatAttachmentId),outsiderCsrf)!=200,"server rejects downloading an attachment from another student's chat");
         call(var9, var8, "ai/config", m("provider", "gemini", "model", "gemini-3.5-flash-lite", "key", var6), var12);
         String var17 = Json.write(call(var10, var8, "state", null, ""));
         ok(var17.contains("hasKey") && !var17.contains(var6), "shared AI setting never exposes secret");
         ok(!Json.write(var2.db.backup()).contains(var6), "backup excludes API secret");
         Map snapshot=var2.db.backup(); Path restored=Files.createTempDirectory("zerde-restore-new-kinds-");
          try(Database restoredDb=new Database(restored)){restoredDb.restore(snapshot);ok(restoredDb.all("chats").size()==1&&restoredDb.all("chatMessages").size()==2&&restoredDb.all("chatAttachments").size()==17&&restoredDb.all("courses").size()==1&&restoredDb.all("courseProgress").size()==1&&restoredDb.all("tutorDialogs").size()==1&&restoredDb.all("tutorMessages").size()==2,"backup restore preserves linked chat attachments, course progress and tutor history records");}
         ok(
            !new String(Files.readAllBytes(var1.resolve("private-ai/settings.enc")), StandardCharsets.ISO_8859_1).contains(var6),
            "on-disk settings are encrypted"
         );
      } finally {
         var2.stop();
      }

      var2 = new Server(var1, "127.0.0.1", 0);

      try {
         ok(s(var2.db.get("users", var5), "login").equals("Тестов"), "student survives server restart");
          ok(var2.db.all("chatMessages").size()==2&&var2.db.all("chatAttachments").size()==17&&var2.db.get("chatAttachments",chatAttachmentId).get("data")!=null&&var2.db.all("courses").size()==1&&yes(var2.db.all("courseProgress").get(0),"completed"),"chat messages, original attachment bytes, courses and lesson progress survive server restart");
         ok(s(var2.svc.ai.config("teacher-main"), "key").equals(var6), "AI connection survives server restart");
         var2.svc.ai.configure("teacher-main", m("provider", "gemini", "model", "gemini-3.8-flash"));
         ok(s(var2.svc.ai.config("teacher-main"), "key").equals(var6), "model change retains saved key");
         var2.svc.ai.clear();
      } finally {
         var2.stop();
      }

      ok((new AI(var1)).settings.isEmpty(), "restore/disconnect clear persists");
      ok(AI.error(429, "{}").contains("лимит") && AI.error(429, "insufficient_quota").contains("баланс"), "rate limit and billing quota distinguished");
      ok(AI.error(401, "secret").contains("недействителен") && !AI.error(400, var6).contains(var6), "errors do not echo provider secrets");
      System.out.println("TOTAL " + count + " persistence and multi-client checks passed");
   }
}
