package vertx.handbook.core.http;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;

public class HttpServerWebsocketDemo {

	public static void main(String[] args) {

		Vertx vertx = Vertx.vertx();
		HttpServer httpServer = vertx.createHttpServer();
		
		httpServer.websocketHandler(serverWebSocket -> {
			
		});
		
		httpServer.listen(8080);

	}

}
