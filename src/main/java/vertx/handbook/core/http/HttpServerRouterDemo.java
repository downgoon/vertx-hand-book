package vertx.handbook.core.http;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;

public class HttpServerRouterDemo {

	public static void main(String[] args) {

		Vertx vertx = Vertx.vertx();
		HttpServer httpServer = vertx.createHttpServer();

		Router router = Router.router(vertx);
		
		router.route("/hello").handler(routingContext -> {
			routingContext.response().end("Hello World");
		});
		
		router.route(HttpMethod.GET, "/sayhi").handler(routingContext -> {
			String name = routingContext.request().getParam("name");
			if (name == null) {
				name = "ghost";
			}
			routingContext.response().end("Hi, " + name);	
		});
		
		
		router.route("/nohandler");
		
		httpServer.requestHandler(router::accept);

		httpServer.listen(8080);

	}

}
