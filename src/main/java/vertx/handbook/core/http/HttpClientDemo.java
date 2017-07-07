package vertx.handbook.core.http;

import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;

public class HttpClientDemo {

	public static void main(String[] args) throws Exception {

		// postJsonDemo();
		
		// postJsonFluentDemo();
		
	   // postBufferChunked();
		
	  //	getSimple();
		
	//	getSimpleOptions();
		
		
		// getOnePiece();
		
		getTwoTimes();
		
	}
	
	
	static void postJsonDemo() {
		Vertx vertx = Vertx.vertx();
		HttpClient httpClient = vertx.createHttpClient();

		HttpClientRequest httpClientRequest = httpClient.request(HttpMethod.POST, 8080, "localhost",
				"/dbapi/default/employee", httpClientResponse -> {
					
					System.out.println("statusCode: " + httpClientResponse.statusCode());
					System.out.println("statusMessage: " + httpClientResponse.statusMessage());
					
					MultiMap headers = httpClientResponse.headers();
					headers.forEach(head -> {
						System.out.println(head.getKey() + ": " + head.getValue());
					});
					
					httpClientResponse.bodyHandler(buffer -> {
						System.out.println("Body: " + buffer.toString());
					});
					
				});

		httpClientRequest.putHeader("Content-Type", "application/json");

		
		httpClientRequest.end(
				new JsonObject().put("id", 100).put("name", "wangyi").put("age", 28).put("credit", 9.3).toBuffer());
	}
	
	static void postJsonFluentDemo() throws Exception {
		Vertx vertx = Vertx.vertx();
		HttpClient httpClient = vertx.createHttpClient();
				
		HttpClientRequest httpClientRequest = httpClient.post(8080, "localhost",
				"/dbapi/default/employee", httpClientResponse -> {
					
					System.out.println("response received for: " + httpClientResponse.request().absoluteURI());
					System.out.println("statusCode: " + httpClientResponse.statusCode());
					System.out.println("statusMessage: " + httpClientResponse.statusMessage());
					
					MultiMap headers = httpClientResponse.headers();
					headers.forEach(head -> {
						System.out.println(head.getKey() + ": " + head.getValue());
					});
					
					httpClientResponse.bodyHandler(buffer -> {
						System.out.println("Body: " + buffer.toString());
					});
					
				}).setChunked(true)
				.putHeader("Content-Type", "application/json");
				
		httpClientRequest.write("{\"id\": 101, \"name\": \"zhangsan\", ");
		
		Thread.sleep(1000L * 5);
		
		httpClientRequest.write("\"age\": 28, \"credit\": 9.3 }");
		
		Thread.sleep(1000L * 5);
		httpClientRequest.end("OK");

	}

	
	static void postBufferChunked() throws Exception {
		Vertx vertx = Vertx.vertx();
		HttpClient httpClient = vertx.createHttpClient();
				
		HttpClientRequest httpClientRequest = httpClient.post(8080, "localhost",
				"/nc-server", httpClientResponse -> {
					System.out.println("response received for: " + httpClientResponse.request().absoluteURI());
				}).setChunked(true)
				.putHeader("Content-Type", "application/o-stream");
				
		
		httpClientRequest.write("Hello");
		Thread.sleep(1000L * 3);

		// httpClientRequest.write(Buffer.buffer().appendInt(98).appendByte((byte)0x11));
		
		httpClientRequest.write(Buffer.buffer().appendInt(98));
		Thread.sleep(1000L * 3);
		
		httpClientRequest.end("OK");

	}
	
	static void getSimple() throws Exception {
		Vertx vertx = Vertx.vertx();
		HttpClient httpClient = vertx.createHttpClient();
				
		httpClient.getNow(8080, "localhost", "/dbapi/default/employee", httpClientResponse -> {
			System.out.println("statusCode: " + httpClientResponse.statusCode());
			
			httpClientResponse.bodyHandler(buffer -> {
				System.out.println("bodyHandler: ");
				JsonObject json = buffer.toJsonObject();
				System.out.println("json body: " + json);
			});
			
		});
	}
	
	
	static void getSimpleOptions() throws Exception {
		Vertx vertx = Vertx.vertx();
		HttpClient httpClient = vertx.createHttpClient(new HttpClientOptions().setDefaultHost("localhost").setDefaultPort(8080));
				
		httpClient.getNow("/dbapi/default/employee", httpClientResponse -> {
			System.out.println("statusCode: " + httpClientResponse.statusCode());
			
			httpClientResponse.bodyHandler(buffer -> {
				System.out.println("bodyHandler: ");
				JsonObject json = buffer.toJsonObject();
				System.out.println("json body: " + json);
			});
			
		});
	}
	
	
	static void getOnePiece() throws Exception {
		Vertx vertx = Vertx.vertx();
		HttpClient httpClient = vertx.createHttpClient(
				new HttpClientOptions()
				.setDefaultHost("localhost")
				.setDefaultPort(8080)
				.setLogActivity(true));
				
		httpClient.getNow("/dbapi/default/employee/2", httpClientResponse -> {
			System.out.println("statusCode: " + httpClientResponse.statusCode());
			
			httpClientResponse.bodyHandler(buffer -> {
				System.out.println("bodyHandler: ");
				JsonObject json = buffer.toJsonObject();
				System.out.println("json body: " + json);
			});
			
		});
	}
	
	static void getTwoTimes() throws Exception {
		Vertx vertx = Vertx.vertx();
		HttpClient httpClient = vertx.createHttpClient(
				new HttpClientOptions()
				.setDefaultHost("localhost")
				.setDefaultPort(8080)
				.setLogActivity(true));
				
		httpClient.getNow("/dbapi/default/employee/2", httpClientResponse -> {
			
			
			System.out.println("statusCode: " + httpClientResponse.statusCode());
			
			httpClientResponse.handler(buffer -> {
				System.out.println("recv data: " + buffer.toString());
			});
			
			httpClientResponse.endHandler((v) -> {
				System.out.println("finish data");
			});
			
		});
	}
}
