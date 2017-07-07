#

# 附录-1：API Gateway 相关

通过``API Gateway``的，一般都会在HTTP头上，留下点痕迹，比如常见的：

- ``X-Forward-For``：用来向后端应用服务器传递请求方的源IP的。
- ``Via``：表示Gateway的信息。比如：``Via: 1.1  webcache_250_199.hexun.com:80 (squid)``。
