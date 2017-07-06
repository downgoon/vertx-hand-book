package vertx.handbook.core.http;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

public class NCServer {

	public static void main(String[] args) throws Exception {
		
		@SuppressWarnings("resource")
		ServerSocket server = new ServerSocket(8080);
		
		while (true) {
			Socket socket = server.accept();
			
			BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream())) ;
			String data = ""; 
			while ((data = reader.readLine()) != null) {
				System.out.println(data);
			}
		}
		
	}

}
