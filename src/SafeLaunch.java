import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.channels.*;
import java.nio.file.*;
import javax.swing.JOptionPane;

public final class SafeLaunch {
 static boolean isZerde(int port) {
  try {
   HttpURLConnection c=(HttpURLConnection)new URL("http://127.0.0.1:"+port+"/").openConnection();
   c.setConnectTimeout(600); c.setReadTimeout(600); c.setInstanceFollowRedirects(false);
   try { String body=new String(c.getInputStream().readNBytes(8192),StandardCharsets.UTF_8);
    return body.contains("<title>Zerde AI</title>") && body.contains("/app.js");
   } finally { c.disconnect(); }
  } catch(Exception e) { return false; }
 }
 static boolean available(int port) {
  try(ServerSocket s=new ServerSocket()) { s.setReuseAddress(false);s.bind(new InetSocketAddress("127.0.0.1",port));return true; }
  catch(Exception e){return false;}
 }
 static int selectPort(int preferred) {
  if(isZerde(preferred)) return -preferred;
  for(int p=18080;p<18100;p++) if(isZerde(p)) return -p;
  if(available(preferred)) return preferred;
  for(int p=18080;p<18100;p++) if(available(p)) return p;
  throw new IllegalStateException("Нет свободного порта для Zerde AI. Закройте лишние экземпляры программы.");
 }
 public static void main(String[] args) {
  try {
   Path home=Path.of(System.getProperty("user.home"),"EduTestSuite2");Files.createDirectories(home);
   try(FileChannel ch=FileChannel.open(home.resolve("desktop-launch.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE)) {
    // Serialize startup decisions, without terminating other applications.
    FileLock lock=null;
    for(int i=0;i<100 && lock==null;i++) {lock=ch.tryLock(); if(lock==null) Thread.sleep(200);}
    if(lock==null) throw new IllegalStateException("Zerde AI уже запускается. Попробуйте ещё раз через несколько секунд.");
    final FileLock held=lock;
    int p=selectPort(Integer.getInteger("edutest.port",8080));
    if(p<0) {
     System.setProperty("edutest.url","http://127.0.0.1:"+(-p)+"/");held.release();
    } else {
     System.setProperty("edutest.port",Integer.toString(p));
     Thread release=new Thread(()->{try {for(int i=0;i<150;i++){if(isZerde(p)) break;Thread.sleep(200);}if(held.isValid())held.release();}catch(Exception ignored){}},"startup-lock");release.setDaemon(true);release.start();
    }
    Class.forName("ZerdeDesktop25").getMethod("main",String[].class).invoke(null,(Object)args);
   }
  } catch(Exception e) {JOptionPane.showMessageDialog(null,"Не удалось открыть Zerde AI: "+e.getMessage(),"Zerde AI",JOptionPane.ERROR_MESSAGE);}
 }
}
