# http-server

<!-- toc -->

<!-- TOC depthFrom:1 depthTo:6 withLinks:1 updateOnSave:1 orderedList:0 -->

- [http-server](#http-server)
	- [使用说明](#使用说明)
	- [引言](#引言)
	- [内嵌HttpServer](#内嵌httpserver)
	- [requestHandler](#requesthandler)
		- [三种Handler写法](#三种handler写法)
		- [简易请求分发](#简易请求分发)
		- [vertx.web的Router](#vertxweb的router)
	- [websocketHandler](#websockethandler)

<!-- /TOC -->

## 使用说明

为了阅读时，能实践，请下载代码，并切换到相应的节点：

``` bash
$ git clone https://github.com/downgoon/vertx-hand-book.git
$ git checkout e1c742ddf63ba1057db2cf168a94e8317c1c9723
```

## 引言

刚接触[vertx](http://vertx.io/)的人，相信多数会被官方首页的一个超级简洁的``http-server``代码吸引：

``` java
import io.vertx.core.AbstractVerticle;
public class Server extends AbstractVerticle {
  public void start() {
    vertx.createHttpServer().requestHandler(req -> {
      req.response()
        .putHeader("content-type", "text/plain")
        .end("Hello from Vert.x!");
    }).listen(8080);
  }
}
```

本文大概讲解下这段代码。

## 内嵌HttpServer

构建一个``http-server``，只需要``vertx.core``，只需要3行：

``` java
Vertx vertx = Vertx.vertx();
HttpServer httpServer = vertx.createHttpServer();
httpServer.listen();
```

如果用``fluent``方式，更简洁：

``` java
Vertx.vertx().createHttpServer().listen();
```

我们自然会问，这样发一个http请求，它的处理结果是什么？的确，压根没有写怎么处理http请求的逻辑！
所以运行是会报错的：

``` java
Exception in thread "main" java.lang.IllegalStateException: Set request or websocket handler first
	at io.vertx.core.http.impl.HttpServerImpl.listen(HttpServerImpl.java:232)
	at io.vertx.core.http.impl.HttpServerImpl.listen(HttpServerImpl.java:207)
	at io.downgoon.httpgo.demo.HttpServerDemo.main(HttpServerDemo.java:12)
```

提示我们：
>Set request or websocket handler first （在``listen``监听端口前，请设置``requestHandler``或者``websocketHandler``）。

## requestHandler

### 三种Handler写法

关于``requestHandler``有两个方法：

``` java
/**
 * Set the request handler for the server to {@code requestHandler}. As HTTP requests are received by the server,
 * instances of {@link HttpServerRequest} will be created and passed to this handler.
 *
 * @return a reference to this, so the API can be used fluently
 */
@Fluent
HttpServer requestHandler(Handler<HttpServerRequest> handler);

/**
 * @return  the request handler
 */
@GenIgnore
Handler<HttpServerRequest> requestHandler();
```

>但这两个方法，并不是重载方法！！！它的风格彻底颠覆了``J2EE``规范，它实际上一个是``setRequestHandler``，另一个是``getRequestHandler``。

于是代码修改为：

``` java
Vertx vertx = Vertx.vertx();
HttpServer httpServer = vertx.createHttpServer();
httpServer.requestHandler(req -> {
  // do nothing
});
httpServer.listen(8080);
```		

设置了一个空处理，浏览器访问，就只能等着超时。如果我们要给一个响应，比如``Hello World``，代码如下：

``` java
httpServer.requestHandler(req -> {
			req.response().end("Hello World");
});
```

可以注意到，接口不像``J2EE``的``servlet``：

``` java
public void doGet(ServletHttpRequest req, ServletHttpResponse res) {

}
```

它的``http-response``，是作为``http-request``的属性的。

上述对HTTP请求的写法是``java8``的方式。我们也可以传统方式：

``` java
public static void main(String[] args) {

		Vertx vertx = Vertx.vertx();
		HttpServer httpServer = vertx.createHttpServer();

		httpServer.requestHandler(new HelloWorldHandler());

		httpServer.listen(8080);

	}

	public static class HelloWorldHandler implements Handler<HttpServerRequest> {
		@Override
		public void handle(HttpServerRequest request) {
			request.response().end("Hello World");
		}
	}
```

最后，我们介绍第3种写法，也是``Java8``中的，它特别像C语言中的``函数指针``，只要``签名相同``的方法（包括参数类型和数量一致，返回值类型似乎都可以不一致），就能借助``函数指针``把方法传递过去（程序=数据+计算。只有两者结合，才叫程序。很长一段时间，为了两者的结合，我们都只传递数据，但现在有一种思想，觉得数据挪动时间和带宽成本太高，应该传递计算）。第3种写法如下：

``` java
public static void main(String[] args) {

		Vertx vertx = Vertx.vertx();
		HttpServer httpServer = vertx.createHttpServer();

		HelloWorldHandler helloworld = new HelloWorldHandler();

    // 函数指针的写法
		httpServer.requestHandler(helloworld::onRequestEvent);

		httpServer.listen(8080);

	}

  // 无需实现 Handler<HttpServerRequest>接口
	public static class HelloWorldHandler  {
    // 只需要方法签名，跟Handler<HttpServerRequest>接口一致，方法名可以随便叫
		public void onRequestEvent(HttpServerRequest request) {
			request.response().end("Hello World");
		}

	}
```

刚才为什么说，返回值似乎都可以不一致呢？因为我们在``onRequestEvent``方法增加一个返回值，也是没有关系的：

``` java
public static class HelloWorldHandler  {

    // 返回值类型不再是 void， 而是 Integer
		public Integer onRequestEvent(HttpServerRequest request) {
			request.response().end("Hello World");
			return 0;
		}

	}
```

实际上，第3种写法，就是``vertx``官方拓展``Router``组件所采用的。什么是``Router``组件呢？简单说，一个``http-server``需要处理很多HTTP请求，需要按请求路径做分发处理，类似``servlet``的``web.xml``。

### 简易请求分发

我们可以写一个``DispatcherHandler``，来做请求分发，类似``niginx``基于``location``来分发，为了简单，我们只支持``严格匹配``。``DispatcherHandler.java``分发代码如下（详见``HttpServerDispatcherDemo.java``）：

``` java
public static class DispatcherHandler {

		private Map<String, Handler<HttpServerRequest>> mapper = new HashMap<String, Handler<HttpServerRequest>>();

    // 用来给server增加location处理
		public DispatcherHandler location(String location, Handler<HttpServerRequest> handler) {
			mapper.put(location, handler);
			return this;
		}

    // 依据路径严格匹配，做请求分发
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
```

有了这个请求分发器，我们可以给``http-server``增加类似``nginx``的``location``处理：

``` java
DispatcherHandler dispatcherHandler = new DispatcherHandler();

dispatcherHandler.location("/hello", req -> {
    req.response().end("Hello World");
});

dispatcherHandler.location("/sayhi", req -> {
    String name = (req.getParam("name") == null ? "ghost" : req.getParam("name"));
    req.response().end("Hi, " + name);
});

// 通过函数指针，用dispatcherHandler来完全接管http-server的请求处理
httpServer.requestHandler(dispatcherHandler::dispatch);
```

我们看一下测试结果：

``` bash
$ curl -i http://localhost:8080/
HTTP/1.1 404 Not Found
Content-Length: 18

location not found%

$ curl -i http://localhost:8080/hello
HTTP/1.1 200 OK
Content-Length: 11

Hello World%

$ curl -i http://localhost:8080/sayhi\?name\=wangyi
HTTP/1.1 200 OK
Content-Length: 10

Hi, wangyi
```

可以看到上述分发代码正常工作了，如果我们要支持``RESTful``呢？比如除了基于路径，还需要基于方法来做分发。在``vertx.core``以外的拓展包``vertx.web``里面，有``Router``类，专门支持。它比我们刚才的分发要强大许多。

### vertx.web的Router

为了使用``Router``，我们需要引入：

``` xml
<dependency>
	<groupId>io.vertx</groupId>
	<artifactId>vertx-web</artifactId>
	<version>3.4.2</version>
</dependency>
```

接着我们引入``Router``代码（详见``HttpServerRouterDemo.java``）：

``` java
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;

public class HttpServerRouterDemo {

	public static void main(String[] args) {

		Vertx vertx = Vertx.vertx();
		HttpServer httpServer = vertx.createHttpServer();

    // 创建一个分发器
		Router router = Router.router(vertx);

    // 接管http-server的请求处理
    httpServer.requestHandler(router::accept);

		httpServer.listen(8080);
	}

}
```

首先我们会发现``Router``是拓展包里面的（包名是``io.vertx.ext.web.Router``），不是``core``包里面的。如上代码访问时，出现默认的处理器404：

``` bash
$ curl -i http://localhost:8080/
HTTP/1.1 404 Not Found
content-type: text/html; charset=utf-8
Content-Length: 53

<html><body><h1>Resource not found</h1></body></html>
```

按前面``DispatcherHandler``的功能需求，用``Router``来实现：

``` java
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
```

代码可读性很强，这里说几点编码风格的事：

- ``routingContext``: 依然不是``servlet``那种，把请求和响应作为两个参数分离传递，而是被封装到``routingContext``里面，它可以取出``request``，``response``，还能取出上下文。
- ``router.route(path).handler()``: 长期以来Java风格应该是``router.addRoute(path, handler)``，而这里``router.route()``就表示``addRoute()``了。再有handler不再是一个映射Value，而是作为route的一个属性。

如果我们只增加``route``，但是不注册``handler``呢？比如：

``` java
router.route("/nohandler");
```

这样的代码就好比传统风格下的：

``` java
router.addRoute("/nohandler", null);
```

因此还是请求不到，报告``404 NOT FOUND``。

## websocketHandler
