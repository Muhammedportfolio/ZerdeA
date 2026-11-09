import javafx.application.Application;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

public final class ZerdeDesktop25 extends DesktopWindow {
   private AdminConsole admin;

   @Override
   public void start(Stage var1) {
      super.start(var1);
      if (var1.getScene() != null) {
         if (this.local != null) {
            try {
               this.admin = new AdminConsole(this.local.db);
            } catch (Exception var11) {
               new Alert(AlertType.WARNING, "Админ-сервер не запустился: " + var11.getMessage(), new ButtonType[0]).show();
               return;
            }

            Scene var2 = var1.getScene();
            Parent var3 = var2.getRoot();
            BorderPane var4 = new BorderPane();
            var4.setCenter(var3);
            Button var5 = new Button("Программа Zerde AI");
            Button var6 = new Button("Администратор · Бақыт");
            Label var7 = new Label("Админ-панель доступна только на этом ПК");
            var7.setStyle("-fx-text-fill: #d1e4e8; -fx-font-size: 11px;");
            Region var8 = new Region();
            HBox.setHgrow(var8, Priority.ALWAYS);
            HBox var9 = new HBox(10.0, new Node[]{var5, var6, var8, var7});
            var9.setStyle("-fx-background-color: #12364b; -fx-padding: 10 14 10 14; -fx-alignment: center-left;");
            var5.setStyle("-fx-background-color: #e2f0f5; -fx-text-fill: #12364b; -fx-font-weight: bold;");
            var6.setStyle("-fx-background-color: #16876c; -fx-text-fill: white; -fx-font-weight: bold;");
            var4.setTop(var9);
            WebView var10 = new WebView();
            var5.setOnAction(var2x -> var4.setCenter(var3));
            var6.setOnAction(var2x -> {
               var4.setCenter(var10);
               if (var10.getEngine().getLocation() == null || !var10.getEngine().getLocation().startsWith("http://127.0.0.1:8082/")) {
                  var10.getEngine().load("http://127.0.0.1:8082/");
               }
            });
            var2.setRoot(var4);
         }
      }
   }

   @Override
   public void stop() {
      if (this.admin != null) {
         this.admin.stop();
         this.admin = null;
      }

      super.stop();
   }

   public static void main(String[] var0) {
      Application.launch(ZerdeDesktop25.class, var0);
   }
}
