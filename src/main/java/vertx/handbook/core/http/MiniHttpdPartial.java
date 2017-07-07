package vertx.handbook.core.http;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class MiniHttpdPartial {

	public static void main(String[] args) throws Exception {
		
		@SuppressWarnings("resource")
		ServerSocket server = new ServerSocket(8080);
		
		while (true) {
			System.out.println("wait for a new connection ...");
			Socket socket = server.accept();
			System.out.println("got a connection");
			
			BufferedReader reader = null;
			BufferedWriter writer = null;
			
			try {
				
				reader = new BufferedReader(new InputStreamReader(socket.getInputStream())) ;
				String data = ""; 
				while ((data = reader.readLine()) != null) {
					System.out.println(data);
					if ("".equalsIgnoreCase(data)) {
						break; // empty line
					}
				}
				
				writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
				
				writer.write("HTTP/1.1 200 OK\r\n");
				writer.write("Server: autorest4db\r\n");
				writer.flush();
				
				System.out.println("partial header sent, wait 2 sec ...");
				Thread.sleep(1000L * 2);
				
				
				writer.write("Content-Type: application/json;charset=UTF-8\r\n");
				writer.write("Content-Length: 46\r\n");
				writer.write("\r\n");
				
				String part1 = "{\"id\":2,\"name\":\"laoer\",";
				String part2= "\"age\":30,\"credit\":null}";
				
				writer.write(part1);
				writer.flush();
				
				System.out.println("part#1 sent, wait 3 sec ...");
				Thread.sleep(1000L * 3);
				
				writer.write(part2);
				System.out.println("part#2 sent, last ok");
				
				writer.flush();
				
			} finally {
				System.out.println("Response Sent OK");
				
				if (reader != null) {
					reader.close();
				}
				if (writer != null) {
					writer.close();
				}
			}
			
		}
		
	}

}
