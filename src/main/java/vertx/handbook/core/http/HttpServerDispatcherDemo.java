package vertx.handbook.core.http;

import java.util.HashMap;
import java.util.Map;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;

public class HttpServerDispatcherDemo {

	public static void main(String[] args) {

		Vertx vertx = Vertx.vertx();
		HttpServer httpServer = vertx.createHttpServer();

		
		DispatcherHandler dispatcherHandler = new DispatcherHandler();
		
		dispatcherHandler.location("/hello", req -> {
			req.response().end("Hello World");
		});
		
		dispatcherHandler.location("/sayhi", req -> {
			String name = (req.getParam("name") == null ? "ghost" : req.getParam("name"));
			req.response().end("Hi, " + name);
		});

		httpServer.requestHandler(dispatcherHandler::dispatch);

		httpServer.listen(8080);

	}

	public static class DispatcherHandler {

		private Map<String, Handler<HttpServerRequest>> mapper = new HashMap<String, Handler<HttpServerRequest>>();
		
		public DispatcherHandler location(String location, Handler<HttpServerRequest> handler) {
			mapper.put(location, handler);
			return this;
		}

		public void dispatch(HttpServerRequest request) {
			Handler<HttpServerRequest> handler = mapper.get(request.path());
			if (handler == null) {
				request.response().setStatusCode(404);
				request.response().end("location not found");
			} else {
				handler.handle(request);
			}
		}

	}

}
