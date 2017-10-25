package reactor.ipc.netty.stats;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import reactor.ipc.netty.NettyContext;
import reactor.ipc.netty.SocketUtils;
import reactor.ipc.netty.tcp.TcpClient;
import reactor.ipc.netty.tcp.TcpServer;

public class TcpClientChannelStatsTest {
  int serverPort;

  @Before
  public void setup() throws Exception {
    serverPort = SocketUtils.findAvailableTcpPort();
  }

  @Test
  public void testHandleTcpRequests() throws InterruptedException {
    final CountDownLatch latch = new CountDownLatch(1);
    final ByteBuf body = Unpooled.wrappedBuffer("body".getBytes());
    final MockChannelStatsListener.Factory factory = new MockChannelStatsListener.Factory();
    final NettyContext server = TcpServer
        .create(serverPort)
        .newHandler((req, rep) ->
            req.receive().aggregate().flatMap(payload -> rep.sendObject(payload).then()))
        .block(Duration.ofSeconds(5));

    final NettyContext client = TcpClient.builder()
        .options(o -> o
            .port(serverPort)
            .channelStatsListenerFactory(factory))
        .build()
        .newHandler((in, out) -> {
          out.sendObject(body).then().flatMap(unit -> in.receive().aggregate()).log("tcp").subscribe(bb -> latch.countDown());
          return out.neverComplete();
        })
        .block(Duration.ofSeconds(5));

    latch.await(5, TimeUnit.SECONDS);

    client.dispose();
    server.dispose();
  }
}
