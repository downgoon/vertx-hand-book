package vertx.handbook.core.http;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class MiniSockProxy {

	public static void main(String[] args) throws Exception {
		
		String backendHost = "localhost";
		int backendPort = 8080;
		
		@SuppressWarnings("resource")
		ServerSocket server = new ServerSocket(8888);

		while (true) {
			System.out.println("wait for a new connection ...");
			Socket c_sock = server.accept(); // client socket
			System.out.println("got a connection");

			DataOutputStream c_dos = null;
			
			Socket f_sock = null;
			DataInputStream f_dis = null;
			
			DataOutputStream f_dos_agent = null;
			DataInputStream c_dis_agent = null;
			
			try {
				f_sock = new Socket(backendHost, backendPort); // forward socket
				
				final DataInputStream c_dis = new DataInputStream(c_sock.getInputStream());
				c_dis_agent = c_dis;
				c_dos = new DataOutputStream(c_sock.getOutputStream());
				
				f_dis = new DataInputStream(f_sock.getInputStream());
				
				final DataOutputStream f_dos  = new DataOutputStream(f_sock.getOutputStream());
				f_dos_agent = f_dos;
				
				new Thread(new Runnable() {
					
					@Override
					public void run() {
						try {
							
							byte[] req_buf = new byte[64];
							int req_size = -1; 
							while ((req_size = c_dis.read(req_buf)) != -1) { // forward request
								System.out.println("request forward >> " + new String(req_buf, 0, req_size));
								f_dos.write(req_buf, 0, req_size);
							}
							
						} catch (Exception e) {
							System.err.println("forward error: " + e.getMessage());
							e.printStackTrace();
							
						} 
						
					}
					
				}, "forward").start();;
				
				
				byte[] res_buf = new byte[64];
				int res_size = -1; 
				while ((res_size = f_dis.read(res_buf)) != -1) { // forward response
					System.out.println("response forward << " + new String(res_buf, 0, res_size));
					c_dos.write(res_buf, 0, res_size);
				}
				
				
			} finally {
				
				if (f_dos_agent != null) {
					f_dos_agent.close();
				}
				if (c_dis_agent != null) {
					c_dis_agent.close();
				}
				
				if (c_dos != null) {
					c_dos.close();
				}
				if (f_dis != null) {
					f_dis.close();
				}
				
				if (f_sock != null) {
					f_sock.close();
				}
			}
			
			
		}
	}

}
