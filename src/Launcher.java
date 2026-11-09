import java.net.URI;
import java.util.List;
import javafx.application.Application;
import javax.swing.JOptionPane;

public class Launcher {
   public static void main(String[] var0) {
      if (var0.length > 0 && var0[0].equals("--connect")) {
         String var1 = JOptionPane.showInputDialog(null, "Адрес общего сервера (например http://192.168.1.10:8080):", "http://localhost:8080");
         if (var1 == null) {
            return;
         }

         try {
            URI var2 = URI.create(var1.strip());
            if (!List.of("http", "https").contains(var2.getScheme()) || var2.getHost() == null || var2.getUserInfo() != null) {
               throw new IllegalArgumentException();
            }

            System.setProperty("edutest.url", var2.toString());
         } catch (Exception var3) {
            JOptionPane.showMessageDialog(null, "Введите корректный адрес http:// или https://");
            return;
         }
      }

      Application.launch(DesktopWindow.class, var0);
   }
}
