import com.sun.net.httpserver.HttpServer;
import java.net.*;
public class LaunchTest {
 public static void main(String[] args)throws Exception {
  try(ServerSocket reserved=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))) {
   int p=reserved.getLocalPort();
   if(SafeLaunch.available(p))throw new AssertionError("occupied");
   if(SafeLaunch.selectPort(p)==p)throw new AssertionError("collision fallback");
  }
  HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/",e->{byte[] b="<title>Zerde AI</title><script src='/app.js'></script>".getBytes();e.sendResponseHeaders(200,b.length);e.getResponseBody().write(b);e.close();});server.start();
  try {int p=server.getAddress().getPort();if(SafeLaunch.selectPort(p)!=-p)throw new AssertionError("reconnect");}finally{server.stop(0);}
  try(ServerSocket s=new ServerSocket(0)){int p=s.getLocalPort();s.close();if(SafeLaunch.selectPort(p)!=p)throw new AssertionError("fresh launch");}
  System.out.println("PASS: fresh launch, occupied-port fallback, existing-Zerde reconnect");
 }
}
