package vertx.handbook.core.http;


import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;

public class HttpServerWebSocket {

	public static void main(String[] args) throws Exception {
		Vertx vertx = Vertx.vertx();
		vertx.createHttpServer().websocketHandler(serverWebSocket -> {
			serverWebSocket.writeBinaryMessage(Buffer.buffer().appendInt(45));
			serverWebSocket.writeTextMessage("Hello, WebSocket");
		});
	}

}
