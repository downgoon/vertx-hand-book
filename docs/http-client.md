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
