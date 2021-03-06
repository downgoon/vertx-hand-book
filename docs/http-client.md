# http-client

从使用功能需求来说，对``http-client``的需求大概是：

- 无包体``GET``： 最简单，最高频的场景是发送一个HTTP请求，GET方法，只有路径和查询参数，没有包体。
- 带包头：发送HTTP请求前，包头需要做些设置，比如``BasicAuth``，比如``POST``方法，比如``COOKIE``等。
- 带包体：现在``RESTful API``盛行，时常需要``POST {JSON Object}``到服务器。

但是，我们讲解的时候，为了方便先全局了解``vertx``关于``http-client``的API设计，我们从更为一般的需求谈起（也就是第3条“带包体”的）。

为了测试方便，需要有一个``RESTful API``的服务器，大家不妨可以下载 [autorest4db](https://github.com/downgoon/autorest4db)，它能快速构建一个带数据库操作的``RESTful``服务器。

## post json

### ``end``方式发送请求

我们要做的第一个实验是，提交一个JSON，生成一个员工对象，``curl``命令表达如下：

``` bash
$ curl -X POST -i -d '{"id": 99, "name": "wangyi", "age": 28, "credit": 9.3 }' -H "Content-Type: application/json" http://localhost:8080/dbapi/default/employee
HTTP/1.1 200 OK
Server: autorest4db
Content-Type: application/json;charset=UTF-8
Content-Length: 47

{"id":99,"name":"wangyi","age":28,"credit":9.3}%
```

这个请求用``vertx``表达（详细代码见``HttpClientDemo.java``的``postJsonDemo``方法）：

``` java
Vertx vertx = Vertx.vertx();
// 构建 HttpClient，相当于创建了一个浏览器
HttpClient httpClient = vertx.createHttpClient();

// 在浏览器上创建一个HTTP请求
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

// 设置HTTP头
httpClientRequest.putHeader("Content-Type", "application/json");

// 发送请求
httpClientRequest.end(
    new JsonObject().put("id", 100).put("name", "wangyi").put("age", 28).put("credit", 9.3).toBuffer());
```

上述代码的运行后，服务端收到一条日志：

``` text
jvm 1    | 2017-07-06 19:09:15,553 DEBUG AutoRestMain(40) - comming request: http://localhost:8080/dbapi/default/employee
```

客户端的日志是：

``` text
statusCode: 200
statusMessage: OK
Server: autorest4db
Content-Type: application/json;charset=UTF-8
Content-Length: 48
Body: {"id":100,"name":"wangyi","age":28,"credit":9.3}
```

可以看到，上述代码风格上略微有点让人不太习惯。
问个问题，上述代码的``发送请求``的逻辑是哪？``处理响应``的逻辑又是呢？
很别扭的是，发送请求，不叫``xxx.send()``，而叫``xxx.end()``。处理响应，``vertx``是出了名的异步处理，所以响应处理是以回调的形式表达的，逻辑是``httpClientResponse -> {...}``。可即便这样，为啥API不是这种呢？

``` java
httpClientRequest.send(json.toBuffer(), httpClientResponse -> {
  System.out.println("statusCode: " + httpClientResponse.statusCode());
});
```

先不着急回答，我们再看看接下来的``write``方式发送请求。

### ``write``方式/``chunked``发送请求

这次我们除了用``write``方式发送请求，还用了``fluent``编码风格（详细代码见``HttpClientDemo.java``的``postJsonFluentDemo``方法）：

``` java
Vertx vertx = Vertx.vertx();
HttpClient httpClient = vertx.createHttpClient();

httpClient.post(8080, "localhost",
    "/dbapi/default/employee", httpClientResponse -> {

      // 批注-1：响应回调时，关联原始请求
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

    }).putHeader("Content-Type", "application/json")
    // 批注-2：不再用end方法发送，改用write方法发送
    .write("{\"id\": 101, \"name\": \"zhangsan\", ")
    // 批注-3：一个完整的JSON，分两次write发送
    .write("\"age\": 28, \"credit\": 9.3 }");

```

上面代码不同之处体现在注释中的3个批注中。运行代码，报错了：

``` java
Exception in thread "main" java.lang.IllegalStateException: You must set the Content-Length header to be the total size of the message body BEFORE sending any data if you are not using HTTP chunked encoding.
	at io.vertx.core.http.impl.HttpClientRequestImpl.write(HttpClientRequestImpl.java:836)
	at io.vertx.core.http.impl.HttpClientRequestImpl.write(HttpClientRequestImpl.java:226)
	at io.vertx.core.http.impl.HttpClientRequestImpl.write(HttpClientRequestImpl.java:236)
	at io.vertx.core.http.impl.HttpClientRequestImpl.write(HttpClientRequestImpl.java:51)
	at vertx.handbook.core.http.HttpClientDemo.postJsonFluentDemo(HttpClientDemo.java:69)
	at vertx.handbook.core.http.HttpClientDemo.main(HttpClientDemo.java:16)
```

重要提示信息：
>You must set the ``Content-Length`` header to be the total size of the message body BEFORE sending any data if you are not using HTTP ``chunked`` encoding.

提示信息说的是，当你给服务器发送HTTP请求时，如果这个请求携带了包体，那么你要么得在头部设置``Content-Length``以明确表明包体部分的字节数；要么你得设置``chunked``（表示流的形式，包体部分是一块一块的）。

真的是这样么？那为什么在上一个实验里面，我们既没有设置``chunked``，也没有设置``Content-Length``呀，但没有提示异常。那是如果你没用``write``方式发送，只用``end(String message)``的时候，``end``方法会自动帮我们计算``Content-Length``。

为了一个JSON分两次写，我们采用设置``chunked``方式：

``` java
httpClient.post(..., httpClientResponse -> {

}).setChunked(true)  // 设置chunked方式，在执行write之前
    .putHeader("Content-Type", "application/json")
    .write("{\"id\": 101, \"name\": \"zhangsan\", ")
    .write("\"age\": 28, \"credit\": 9.3 }");
```

如此之后再运行，请求的确发出去了，因为服务器端有日志显示请求已到。但是客户端迟迟没有响应。官方是这么解释的：

>When you’re writing to a request, the first call to ``write`` will result in the **request headers** being written out to the wire.
>
>The actual write is ``asynchronous`` and might not occur until some time after the call has returned.

第一个``write``会触发写操作（指向网络发送），但是只能确定的是HTTP头会被发送出去，至于包体部分，就不确定了，因为包体是``异步``的，既然是``chunked``，就是分块的发送。那对分块发送有没有类似``flush``的机制呢？有！那就是``end()``。只要在尾巴上追加``end()``，就能快速得到响应了：

``` java
httpClient.post(..., httpClientResponse -> {

}).setChunked(true)  // 设置chunked方式，在执行write之前
    .putHeader("Content-Type", "application/json")
    .write("{\"id\": 101, \"name\": \"zhangsan\", ")
    .write("\"age\": 28, \"credit\": 9.3 }")
    .end();  // 相当于给异步写一个flush()操作，强制写到远程去
```

### chunked 实验

如果不写``end()``，我们想看看``chunked``具体情况，可以用``nc -l 8080``做个简单服务器（如果您是``windows``环境，用``nc``命令行不方便，请用项目中的``NCServer.java``代码），观察写入情况。为了更方便观察，我们改写下代码，每隔5秒钟，发送一个``chunk``：

``` java
httpClientRequest.write("{\"id\": 101, \"name\": \"zhangsan\", ");

Thread.sleep(1000L * 5);

httpClientRequest.write("\"age\": 28, \"credit\": 9.3 }");

Thread.sleep(1000L * 5);
httpClientRequest.end("OK");
```

服务端收到的内容：

``` bash
$ nc -l 8080
POST /dbapi/default/employee HTTP/1.1
Content-Type: application/json
Host: localhost:8080
transfer-encoding: chunked

20
{"id": 101, "name": "zhangsan",
1a
"age": 28, "credit": 9.3 }
2
OK
0


```

服务器收到了4个块，其中最后一个块是空块，字节数为0，紧跟着一个空行表示结束。``chunked``协议规定：每个块在发送时，都有一个长度头，接着是具体内容。如果我们换成非字符的数据呢？换个二进制数据：

``` java
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

    // 写入二进制数据
		httpClientRequest.write(Buffer.buffer().appendInt(98));
		Thread.sleep(1000L * 3);

		httpClientRequest.end("OK");

	}
```

nc接收的是：

```
$ nc -l 8080
POST /nc-server HTTP/1.1
Content-Type: application/o-stream
Host: localhost:8080
transfer-encoding: chunked

5
Hello
4   注释：这个4表示Int是4个字节，下面的b表示binary-data的意思
b
2
OK
0

```

### chunked 协议

``chunked``（``分块传输``）是``HTTP/1.1``引入的。``chunked``协议最初的设计目的是：服务器可以逐渐得给浏览器推送数据，浏览器能逐渐的渲染网页。这个有点流媒体的感觉，视频不需要等全部下载完了才能播放，而是来了一点视频，就播放一点视频。

通常，HTTP 应答消息中发送的数据是整个发送的，``Content-Length`` 消息头字段表示数据的长度。数据的长度很重要，因为客户端需要知道哪里是应答消息的结束，以及后续应答消息的开始。

``Content-Length``的弊端就是：数据得一次性准备好，数据量大一点，就麻烦了。

使用分块传输编码，数据分解成一系列数据块，并以一个或多个块发送，这样服务器可以发送数据而 **不需要预先知道发送内容的总大小（什么时候想结束了就发送一个'空块'就可以了）**。通常数据块的大小是一致的，但也不总是这种情况。

>简单说，``chunked``就是网页领域的``流媒体``，它像流视频一样，不用等视频全部下载完就可以播放，下载一点，播放一点。浏览器渲染网页也是一样，收到一点``html``内容，就渲染一点``html``内容。

**分块传输** 的应用场景：

- 预先不知道``Content-Length``： 服务器发送响应时，上不知道``Content-Length``，需要按 **分块传输**。
- 最后填写HTTP头：响应体如果采用``chunked``传输，同时在HTTP头要对响应体做``摘要签名``，那么麻烦问题来了，数据是流式的，只有最后才知道所有的内容，进而才能算出``摘要签名``，所以``chunked``协议下，**HTTP也可以事后追加**。可是从前面抓包的情况看，``chunked-size\r\nchunked-data``的结构看，似乎没法再写HTTP头了呀？查下``chunked``协议：

![](assets/img-http-chunked.png)

>可以看到，在最后一个``chunked``（chunked-size为0的）后面，有一个可选的拓展头，即使这个拓展头没有，最后必须以空行结束（所以，``chunked``并不是以0号块结束，而是以空行结束）。

另外，除了服务端向客户端推送数据可以``chunked``外，客户端向服务端也是可以``chunked``的。

那什么样的场景下，不知道``Content-Length``，还要最后算``摘要签名``呢？用的最多大的是 **压缩传输**。

>在压缩的情形中，分块编码(``chunked``)有利于 **一边进行压缩一边发送数据** ，而不是先完成压缩过程以得知压缩后数据的大小。

看一个``gzip``压缩的抓包：

``` http
HTTP/1.1 200 OK
Date: Wed, 06 Jul 2016 06:59:55 GMT
Server: Apache
Accept-Ranges: bytes
Transfer-Encoding: chunked
Content-Type: text/html
Content-Encoding: gzip
Age: 35
X-Via: 1.1 daodianxinxiazai58:88 (Cdn Cache Server V2.0), 1.1 yzdx147:1 (Cdn
Cache Server V2.0)
Connection: keep-alive

a
....k.|W..
166
..OO.0...&~..;........]..(F=V.A3.X..~z...-.l8......y....).?....,....j..h .6
....s.~.>..mZ .8/..,.)B.G.`"Dq.P].f=0..Q..d.....h......8....F..y......q.....4
{F..M.A.*..a.rAra.... .n>.D
..o@.`^.....!@ $...p...%a\D..K.. .d{2...UnF,C[....T.....c....V...."%.`U......?
D....#..K..<.....D.e....IFK0.<...)]K.V/eK.Qz...^....t...S6...m...^..CK.XRU?m..
.........Z..#Uik......
0
```

其中``Transfer-Encoding: chunked``表明按分块传输，``Content-Encoding: gzip``表示内容是压缩形式。这个抓包，还不全面，传输内容最后没有数字签名。因此，在``chunked``上，看不到拓展头。


## 编码风格

前面提到了，编码风格上，``vertx``是：请求还没发，就预先设置好处理。

``` java
httpClient.post(..., httpClientResponse -> {

}).setChunked(true)  // 设置chunked方式，在执行write之前
    .putHeader("Content-Type", "application/json")
    .write("{\"id\": 101, \"name\": \"zhangsan\", ")
    .write("\"age\": 28, \"credit\": 9.3 }")
    .end();  // 相当于给异步写一个flush()操作，强制写到远程去
```

而不是，发的同时，设置：

``` java
httpClientRequest.send(json.toBuffer(), httpClientResponse -> {
  System.out.println("statusCode: " + httpClientResponse.statusCode());
});
```

当然它也允许，我们事后设置（但一定是发送之前，只不过可以创建请求之后）：

``` java
HttpClientRequest request = client.post("some-uri");
request.handler(response -> {
  System.out.println("Received response with status code " + response.statusCode());
});
```


## quick get

如果只是一个不用带包体的GET请求，可以直接发送并对响应进行处理。这个代码非常类似``JavaScript``：

``` java
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
```

如果是服务器对服务器的请求，多数情况下，都是访问一个机器和端口，还可以设置到默认参数里面：

``` java
HttpClient httpClient = vertx.createHttpClient(
    new HttpClientOptions()
          .setDefaultHost("localhost")
          .setDefaultPort(8080));

		httpClient.getNow("/dbapi/default/employee", httpClientResponse -> {
      ....
    });
```

这里接收响应，用的是``httpClientResponse.bodyHandler()``。刚才我们前面谈到``chunked``了，那么数据如果分块到达怎么办？再者``Java NIO``都是基于事件的，TCP流一个逻辑完整的报文是分两次到达的，``httpClientResponse.bodyHandler()``会触发两次，还是一次呢？

关于这点官方文档明确说了：``httpClientResponse.bodyHandler()``只会触发一次，而且触发的时候，已经把各个分块整到一起了（``in one piece``）。我们看官方具体描述。

### BodyHandler

``` java
/**
 * Convenience method for receiving the entire request body in one piece.
 * <p>
 * This saves you having to manually set a dataHandler and an endHandler and append the chunks of the body until
 * the whole body received. Don't use this if your request body is large - you could potentially run out of RAM.
 *
 * @param bodyHandler This handler will be called after all the body has been received
 */
@Fluent
HttpClientResponse bodyHandler(Handler<Buffer> bodyHandler);
```

强调点是：响应数据是``in one piece``的，即使网络上分两次或多次到达。框架给我们做了这个工作，当然我们也可以自己做这个工作：

- ``dataHandler``： 每次网络上到达一点数据，``dataHandler``会被触发一次。
- ``endHandler``: 最后一次到达时，会触发``endHandler``。

什么时候，需要自己处理多次到达呢？数据量大的时候，一次性处理所有内存不够，另外分次处理或能提供实时性，有点类似流媒体。

我们来构建一个HTTP服务器，它将一个JSON Body通过两次发送，完整代码见 [[MiniHttpdPartial.java](src/main/java/vertx/handbook/core/http/MiniHttpdPartial.java)，其中片段：

``` java
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
```

上述代码，发送完第1部分后，睡眠了3分钟，再发送第2片段。

服务端的日志：

```
java MiniHttpdPartial
wait for a new connection ...
got a connection
GET /dbapi/default/employee/2 HTTP/1.1
Host: localhost:8080

partial header sent, wait 2 sec ...
part#1 sent, wait 3 sec ...
part#2 sent, last ok
Response Sent OK
```

客户端使用``bodyHandler``的结果：

``` java
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
```

尽管，JSON在服务端是分两次发送的，但是客户端的``bodyHandler``的回调是等最后一段都到达的时候，才触发的。
如果我们不用``bodyHandler``，而改用``dataHandler``和``endHandler``又会怎样呢？

### dataHandler 与 endHandler

找遍了``API``没有找到``dataHandler``，原来直接就是 ``handler``，而且是在超类里面：

``` java
public interface ReadStream<T> extends StreamBase {

  /**
   * Set a data handler. As data is read, the handler will be called with the data.
   *
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  ReadStream<T> handler(@Nullable Handler<T> handler);

}

```

我们的服务端依然是 [MiniHttpdPartial.java](src/main/java/vertx/handbook/core/http/MiniHttpdPartial.java)。
但我们客户端代码，是通过``dataHandler``和``endHandler``来获取包体：

``` java
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
```

运行结果是：

``` bash
statusCode: 200
recv data: {"id":2,"name":"laoer",
recv data: "age":30,"credit":null}
finish data
```

结果显示，一个完整的JSON会分两次到达。显然，对于普通的小数据，直接用``bodyHandler``就好，它会让我们降低开发负担。


# 参考资料

- [HTTP简介](https://mp.weixin.qq.com/s?__biz=MzI5NjAxODQyMg==&mid=2676479748&idx=1&sn=6baa37343d61e24d38661ecbcfde2d4f&mpshare=1&scene=1&srcid=0706VLyHbzUTYobWEbZ6cFhV&pass_ticket=1TqifluyM83yuwcJTCnnhMxYh1gSoZ7Xb0LgDOEUQwQ%3D#rd)： 内含``chunked``协议描述。
- [HTTP 协议常用的三种数据格式](https://mp.weixin.qq.com/s?__biz=MjM5NTEwMTAwNg==&mid=2650209502&idx=2&sn=1fdf51bd673e8f37fb7db588f5140f76&chksm=befffcff898875e9ddd93e8c4ac08c4e2a0f3e51c35931176e243259dae84bc0bdcb60ee0fbb&mpshare=1&scene=1&srcid=07068Jufkf31zbPLwZ8Dtqps&pass_ticket=1TqifluyM83yuwcJTCnnhMxYh1gSoZ7Xb0LgDOEUQwQ%3D#rd)：``gzip``压缩、``chunked``传输和``MultiPart``上传
