import java.awt.Desktop;
import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Set;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Worker.State;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.image.Image;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import netscape.javascript.JSObject;

public class DesktopWindow extends Application {
   DesktopWindow.Bridge bridge;
   Server local;
   Tunnel tunnel;

   public void start(Stage var1) {
      String var2 = System.getProperty("edutest.url", "");
      if (var2.isBlank()) {
         int var3 = Integer.getInteger("edutest.port", 8080);
         var2 = "http://localhost:" + var3;

         try {
            this.local = new Server(
               Path.of(System.getProperty("edutest.home", Path.of(System.getProperty("user.home"), "EduTestSuite2").toString())),
               System.getProperty("edutest.host", "127.0.0.1"),
               var3
            );
         } catch (Exception var7) {
            new Alert(
                  AlertType.ERROR,
                  "Не удалось запустить сервер: " + var7.getMessage() + "\nЗакройте другой экземпляр или подключитесь через CONNECT.bat.",
                  new ButtonType[0]
               )
               .showAndWait();
            Platform.exit();
            return;
         }
      }

      var1.getIcons().add(new Image(DesktopWindow.class.getResourceAsStream("/assets/icon.png")));
      WebView var8 = new WebView();
      var8.getEngine().setJavaScriptEnabled(true);
      var8.getEngine().setConfirmHandler(var0 -> {
         Alert var1x = new Alert(AlertType.CONFIRMATION, var0, new ButtonType[]{ButtonType.YES, ButtonType.NO});
         return var1x.showAndWait().orElse(ButtonType.NO) == ButtonType.YES;
      });
      var8.getEngine().setOnAlert(var0 -> new Alert(AlertType.INFORMATION, (String)var0.getData(), new ButtonType[0]).showAndWait());
      var8.getEngine().setCreatePopupHandler(var0 -> {
         WebView var1x = new WebView();
         Stage var2x = new Stage();
         var2x.setTitle("Zerde AI");
         var2x.setScene(new Scene(var1x, 900.0, 700.0));
         var2x.show();
         return var1x.getEngine();
      });
      BorderPane var4 = new BorderPane(var8);
      if (this.local != null) {
         try {
            Path var5 = Path.of(DesktopWindow.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (!Files.isDirectory(var5)) {
               var5 = var5.getParent();
            }

            this.tunnel = new Tunnel(Tunnel.command(var5, this.local.http.getAddress().getPort()), var0 -> {});
            Runtime.getRuntime().addShutdownHook(new Thread(() -> this.tunnel.close(), "zerde-tunnel-stop"));
            this.tunnel.start();
         } catch (Exception var6) {
            System.err.println("Tunnel initialization failed: " + var6.getMessage());
         }
      }

      var1.setTitle("Zerde AI");
      var1.setScene(new Scene(var4, 1240.0, 840.0));
      var1.setOnCloseRequest(var0 -> Platform.exit());
      var1.setMinWidth(860.0);
      var1.setMinHeight(620.0);
      var1.show();
      this.bridge = new DesktopWindow.Bridge(var1, this);
      var8.getEngine().getLoadWorker().stateProperty().addListener((var2x, var3x, var4x) -> {
         if (var4x == State.SUCCEEDED) {
            ((JSObject)var8.getEngine().executeScript("window")).setMember("nativeBridge", this.bridge);
            var8.getEngine().executeScript("if(window.nativeOnlineReady)window.nativeOnlineReady()");
         }
      });
      var8.getEngine().load(var2);
   }

   public void stop() {
      if (this.tunnel != null) {
         this.tunnel.close();
      }

      if (this.local != null) {
         try {
            this.local.stop();
         } catch (Exception var2) {
         }
      }

      System.exit(0);
   }

   public static void main(String[] var0) {
      launch(var0);
   }

   public static class Bridge {
      final Stage stage;
      final DesktopWindow owner;

      Bridge(Stage var1, DesktopWindow var2) {
         this.stage = var1;
         this.owner = var2;
      }

      public boolean hasOnlineSite() {
         return this.owner.local != null;
      }

      public String onlineState() {
         return this.owner.tunnel == null ? "ERROR" : this.owner.tunnel.snapshot().state().name();
      }

      public String onlineUrl() {
         return this.owner.tunnel == null ? "" : this.owner.tunnel.snapshot().url();
      }

      public String onlineMessage() {
         return this.owner.tunnel == null ? "Не удалось подготовить туннель." : this.owner.tunnel.snapshot().message();
      }

      public void retryOnline() {
         if (this.owner.tunnel != null) {
            this.owner.tunnel.start();
         }
      }

      public void copyOnline() {
         if (this.owner.tunnel != null) {
            Tunnel.View var1 = this.owner.tunnel.snapshot();
            if (var1.state() == Tunnel.State.READY) {
               ClipboardContent var2 = new ClipboardContent();
               var2.putString(var1.url());
               Clipboard.getSystemClipboard().setContent(var2);
            }
         }
      }

      public void openExternal(String var1) {
         try {
            URI var2 = URI.create(var1);
            if (!"https".equals(var2.getScheme())
               || !Set.of(
                     "accounts.google.com",
                     "console.cloud.google.com",
                     "aistudio.google.com",
                     "ai.google.dev",
                     "platform.openai.com",
                     "developers.openai.com",
                     "platform.claude.com",
                     "ollama.com"
                  )
                  .contains(var2.getHost())) {
               return;
            }

            Desktop.getDesktop().browse(var2);
         } catch (Exception var3) {
            Platform.runLater(() -> new Alert(AlertType.INFORMATION, "Откройте в браузере: " + var1, new ButtonType[0]).showAndWait());
         }
      }

      public void save(String var1, String var2) {
         Platform.runLater(() -> {
            try {
               if (var2.length() > 150000000) {
                  throw new IllegalArgumentException("Файл слишком большой");
               }

               FileChooser var3 = new FileChooser();
               var3.setInitialFileName(Path.of(var1).getFileName().toString());
               File var4 = var3.showSaveDialog(this.stage);
               if (var4 != null) {
                  Files.write(var4.toPath(), Base64.getDecoder().decode(var2));
               }
            } catch (Exception var5) {
               new Alert(AlertType.ERROR, "Не удалось сохранить файл: " + var5.getMessage(), new ButtonType[0]).showAndWait();
            }
         });
      }
   }
}
