import io.netty.bootstrap.Bootstrap
import io.netty.channel._
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.http._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.io.StdIn
import scala.util.{Failure, Success, Try}

class SClient(eventGroup: NioEventLoopGroup) {
  val map = scala.collection.mutable.Map[Int, Channel]()
  val port = 8010
  val ip = "119.90.35.117"

  val serverChannel = new Bootstrap().group(eventGroup)
    .channel(classOf[NioSocketChannel])
    .handler(new ChannelInitializer[SocketChannel] {
      override def initChannel(channel: SocketChannel): Unit = {
        channel.pipeline()
          .addLast(new HttpServerCodec())
          .addLast(new HttpObjectAggregator(1024 * 1024))
          //.addLast(new LoggingHandler(LogLevel.INFO))
          .addLast(new SimpleChannelInboundHandler[FullHttpRequest]() {
            override def channelRead0(ctx: ChannelHandlerContext, request: FullHttpRequest): Unit = {
              if (request.getUri.startsWith("/close?id=")) {
                val id = request.getUri.substring(10).toInt
                map.get(id).foreach(_.close())

                ctx.writeAndFlush(createOkResp(request, id))
              } else {
                val id = request.headers().get("_id").toInt

                val clientChannel = map.getOrElse(id, createChannel(id))

                if(clientChannel == null) {
                  ctx.writeAndFlush(createOkResp(request, id))
                } else {
                  request.headers().remove("_id")
                  request.retain()
                  clientChannel.writeAndFlush(request)
                }
              }
            }
          })
      }
    }).connect(ip, port).addListener(new ChannelFutureListener {
    override def operationComplete(future: ChannelFuture): Unit = {
      println("已建立连接")
    }
  }).sync().channel()

  serverChannel.closeFuture().addListener(new ChannelFutureListener {
    override def operationComplete(future: ChannelFuture): Unit = {
      Future {
        var input = ""
        do {
          print("连接已关闭,是否重连y, n(默认y):")
          input = StdIn.readLine()
        } while(input != "" && input != "y" && input != "n")

        if(input == "n") {
          eventGroup.shutdownGracefully()
        } else {
          new SClient(eventGroup)
        }
      }
    }
  })

  def createOkResp(request: FullHttpRequest, id: Int): DefaultFullHttpResponse = {
    val resp = new DefaultFullHttpResponse(request.getProtocolVersion, HttpResponseStatus.OK)
    resp.headers().add("_id", id)
    resp.headers().add(HttpHeaders.Names.CONTENT_LENGTH, resp.content().readableBytes())
    resp
  }

  def createChannel(id: Int): Channel = Try {
    val port = 8010
    val ip = "127.0.0.1"

    val c = new Bootstrap().group(eventGroup)
      .channel(classOf[NioSocketChannel])
      .handler(new ChannelInitializer[SocketChannel] {
        override def initChannel(channel: SocketChannel): Unit = {
          channel.pipeline().addLast(new HttpClientCodec())
            .addLast(new HttpObjectAggregator(1024 * 1024))
            .addLast(new SimpleChannelInboundHandler[FullHttpResponse]() {
              override def channelRead0(ctx: ChannelHandlerContext, msg: FullHttpResponse): Unit = {
                msg.retain()
                msg.headers().add("_id", id)
                serverChannel.writeAndFlush(msg)
              }
            })
        }
      }).connect(ip, port).sync().channel()
    map.put(id, c)
    c
  } match {
    case Success(c) => c
    case Failure(e) => null
  }
}

object MyApp extends App {
  val group = new NioEventLoopGroup()
  new SClient(group)
}
