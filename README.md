# wx_test_solution

用作外网的代理，方便callback服务端以及应用本地化调试

## 结构

### Server

微信回调服务端，已经部署到117服务器

### Client

微信回调桥接客户端，需要在本地启动

## 使用

### mvn命令打包
```bash
mvn package
```
### idea mvn打包
导入项目到idea，在idea的Maven Projects工具条里：wx_test_solution->Lifecycle->package双击打包

## 本地使用
打包后，在client/target中将使用：client-1.0-SNAPSHOT.jar 和lib/目录。然后在控制台运行一下命令即可启动。
```bash
java -jar client-1.0-SNAPSHOT.jar
```

默认client连接到callback server的端口是8010，这个可以通过client项目修改MyApp.scala的第47行代码达到你期望的端口

## 局限性
这个桥只支持一个callback server：当A启动client后，B也启动client，那么server会关闭A的client
