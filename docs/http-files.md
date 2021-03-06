# HTTP 文件传输

HTTP关于文件上传的内容，牵涉的技术点，有4个：

- ``MultiPart``上传
- 压缩：``gzip``和``zlib``
- ``chunked``
- 断点续传：``Range``


## 断点续传

``断点续传``的意思是什么呢?我通过浏览器从Web服务器下载一个文件，文件很大，比如100M，刚下到50M的时候，网络闪断，TCP连接断开了。怎么办呢？如果浏览器能告诉服务器说，我已经下载了50M了，请把后面的50M给我就好。

这个类似，现在``断点播放``，现在主流视频网站都支持从第i秒开始播放，比如 http://example.tv/12345#30，表达的语义是 从编号为12345的视频的第30秒开始播放。

**请求头**：

``` http
Range: bytes={range_from}-{range_to}
```
它表示客户端向服务器说，请把从 第 {range_from} 字节到 {range_to} 的数据发送给我。

>该头表示从后端 HTTP 服务器取数据，开始偏移位置为 {range_from}，结束偏移位置为 {range_to}，其中偏移位置下标从 0 开始；如果省略了 {range_to} 则表示从指定的开始位置 {range_from} 至数据结尾。如：Range: bytes=1024-2048  其表示读取从偏移位置 1024 至 2028 的数据，而 Range: bytes=1024- 则表示读取从偏移位置 1024 至数据结尾的数据。

**响应头**：

``` http
Content-Range: bytes {range_from}-{range_to}/{total_length}
```

>其中 ``{range_from}`` 和 ``{range_to}`` 分别代表当前从服务端返回的数据的起始偏移位置（下标从 0 开始），这是一个双向闭区间范围，而 ``total_length`` 则指定了整个数据的总长度，此时 HTTP 响应头中的 Content-Length 如果存在，则其值表示当前返回的数据块（由 {range_from} 和 {range_to} 指定的数据区间）的长度。该长度内的数据包括 {range_from} 和 {range_to} 两个位置的数据。


那么有个问题，断点状态怎么保存？是在服务端保存呢？还是客户端保存？

如果在服务端保存，服务端得给客户端发一个ID，服务端记住每个ID的每个文件下载到了什么程度。这个会话追踪，本质上跟``session``的概念是一样的。

如果客户端保存，客户端需要知道总长度是多少。这样把已经保存到，跟总长度一对比，就知道剩余的应该从哪个点下载了。

## 压缩传输

### 常见Web压缩

``` http
GET /abc HTTP/1.1
Accept-Encoding: gzip, deflate
```

浏览器告诉服务器支持 ``gzip`` 和 ``deflate`` 两种数据格式，服务器收到这种请求之后，会进行 ``gzip`` 或 ``deflate`` 压缩（一般都是返回 ``gzip`` 格式的数据）。

它们底层大概都是用``zlib``的算法，只是封装格式不同。压缩效率多高呢？对纯文本而言，可以 **减少** 70% 的容量。

### 会话过程

Web 服务器处理数据压缩的过程

- Web服务器接收到浏览器的HTTP请求后，检查浏览器是否支持HTTP压缩（Accept-Encoding 信息）；
- 如果浏览器支持HTTP压缩，Web服务器检查请求文件的后缀名；
- 如果请求文件是HTML、CSS等静态文件，Web服务器到压缩缓冲目录中检查是否已经存在请求文件的最新压缩文件；
- 如果请求文件的压缩文件不存在，Web服务器向浏览器返回未压缩的请求文件，并在压缩缓冲目录中存放请求文件的压缩文件；
- 如果请求文件的最新压缩文件已经存在，则直接返回请求文件的压缩文件；
- 如果请求文件是 **动态文件** ，Web服务器动态压缩内容并返回浏览器，压缩内容不存放到压缩缓存目录中。注意：动态内容的压缩，一般要配套``chunked``机制。

# TODO

- [ ] 断点追踪：客户端 vs 服务端
