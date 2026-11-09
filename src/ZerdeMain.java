import java.nio.file.Path;

public class ZerdeMain {
   public static void main(String[] var0) throws Exception {
      Path var1 = Path.of(System.getProperty("edutest.home", System.getProperty("user.home") + "/EduTestSuite2"));
      String var2 = System.getProperty("edutest.host", "127.0.0.1");
      int var3 = Integer.getInteger("edutest.port", 8080);
      Server var4 = new Server(var1, var2, var3);

      AdminConsole var5;
      try {
         var5 = new AdminConsole(var4.db);
      } catch (Exception var7) {
         var4.stop();
         throw var7;
      }

      Runtime.getRuntime().addShutdownHook(new Thread(() -> {
         try {
            var5.stop();
         } catch (Exception var4x) {
         }

         try {
            var4.stop();
         } catch (Exception var3x) {
         }
      }));
      System.out.println("Zerde AI: http://localhost:" + var3 + "/ ; Администратор: http://localhost:8082/");
   }
}
