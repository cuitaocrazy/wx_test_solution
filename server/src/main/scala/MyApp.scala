import java.util.concurrent.atomic.AtomicInteger

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel._
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http._
import io.netty.handler.logging.{LogLevel, LoggingHandler}

object WXServer {
  val idGenerator = new AtomicInteger()
  val map = scala.collection.mutable.Map[Int, SocketChannel]()

  {
    val port = 80

    val bossEventLoopGroup = new NioEventLoopGroup(1)
    val workEventLoopGroup = new NioEventLoopGroup()

    new ServerBootstrap().group(bossEventLoopGroup, workEventLoopGroup)
      .channel(classOf[NioServerSocketChannel])
      .childHandler(new ChannelInitializer[SocketChannel] {
        override def initChannel(channel: SocketChannel): Unit = {
          val id = idGenerator.getAndIncrement()
          map.put(id, channel)

          channel.closeFuture().addListener(new ChannelFutureListener {
            override def operationComplete(future: ChannelFuture): Unit = {
              SelfServer.channel.foreach {
                c =>
                  map.remove(id)
                  c.writeAndFlush(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/close?id=" + id))
              }
            }
          })

          channel.pipeline().addLast(new HttpServerCodec)
            .addLast(new HttpObjectAggregator(1024 * 1024))
            .addLast(new SimpleChannelInboundHandler[FullHttpRequest]() {
            override def channelRead0(channelHandlerContext: ChannelHandlerContext, request: FullHttpRequest): Unit = {
              SelfServer.channel match {
                case Some(c) =>
                  request.retain()
                  request.headers().add("_id", id)
                  c.writeAndFlush(request)
                case None =>
                  val resp = new DefaultFullHttpResponse(request.getProtocolVersion, HttpResponseStatus.OK)
                  resp.headers.add(HttpHeaders.Names.CONTENT_LENGTH, 0)
                  channelHandlerContext.writeAndFlush(resp)
              }
            }
          })
        }
      })
      .bind(port)
  }
}

object SelfServer {
  @volatile
  var channel = Option[SocketChannel](null)

  {
    val port = 8010

    val bossEventLoopGroup = new NioEventLoopGroup(1)
    val workEventLoopGroup = new NioEventLoopGroup()

    new ServerBootstrap().group(bossEventLoopGroup, workEventLoopGroup)
      .channel(classOf[NioServerSocketChannel])
      .childHandler(new ChannelInitializer[SocketChannel] {
        override def initChannel(c: SocketChannel): Unit = {
          channel.foreach(_.close())
          channel = Option(c)
          c.pipeline()
            .addLast(new HttpClientCodec())
            .addLast(new LoggingHandler(LogLevel.INFO))
            .addLast(new HttpObjectAggregator(1024 * 1024))
            .addLast(new SimpleChannelInboundHandler[FullHttpResponse]() {
              override def channelRead0(channelHandlerContext: ChannelHandlerContext, response: FullHttpResponse): Unit = {
                val id = response.headers().get("_id").toInt
                WXServer.map.get(id).foreach {
                  c =>
                    response.headers().remove("_id")
                    response.retain()
                    c.writeAndFlush(response)
                }
              }
            })
        }
      })
      .bind(port)
  }
}

/**
  * Created by cuitao-pc on 16/3/23.
  */
object MyApp extends App {
  WXServer
  SelfServer
}
