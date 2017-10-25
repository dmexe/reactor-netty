package reactor.ipc.netty.channel;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.FileRegion;
import io.netty.util.AttributeKey;
import java.util.Objects;
import reactor.ipc.netty.stats.ChannelStatsListener;
import reactor.ipc.netty.stats.Stopwatch;

@Sharable
public class ChannelStatsHandler extends ChannelDuplexHandler {
  private static final AttributeKey<Stopwatch> STOPWATCH_KEY =
      AttributeKey.valueOf(Stopwatch.class, "stopWatch");

  private final ChannelStatsListener listener;

  public ChannelStatsHandler(ChannelStatsListener listener) {
    this.listener = Objects.requireNonNull(listener, "listener cannot be null");
  }

  @Override
  public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
    ctx.channel().attr(STOPWATCH_KEY).set(Stopwatch.createStarted());

    super.handlerAdded(ctx);
  }

  @Override
  public void channelActive(ChannelHandlerContext ctx) throws Exception {
    listener.onChannelActive();

    super.channelActive(ctx);
  }

  @Override
  public void channelInactive(ChannelHandlerContext ctx) throws Exception {
    final Stopwatch stopWatch = ctx.channel().attr(STOPWATCH_KEY).getAndSet(null);
    if (stopWatch != null) {
      listener.onChannelInactive(stopWatch);
    }

    super.channelInactive(ctx);
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
    listener.onException(cause);

    super.exceptionCaught(ctx, cause);
  }

  @Override
  public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
    final Stopwatch stopWatch = ctx.channel().attr(STOPWATCH_KEY).getAndSet(null);
    if (stopWatch != null) {
      listener.onChannelInactive(stopWatch);
    }
    listener.onClose();

    super.close(ctx, promise);
  }

  @Override
  public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
    if (msg instanceof ByteBuf) {
      final ByteBuf bytes = (ByteBuf) msg;
      listener.onWrite(bytes.readableBytes());
    } else if (msg instanceof FileRegion) {
      final FileRegion file = (FileRegion) msg;
      listener.onWrite(file.count());
    }

    super.write(ctx, msg, promise);
  }

  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
    if (msg instanceof ByteBuf) {
      final ByteBuf bytes = (ByteBuf) msg;
      listener.onRead(bytes.readableBytes());
    }

    super.channelRead(ctx, msg);
  }
}