package reactor.ipc.netty.stats;

import java.util.Objects;
import reactor.util.Logger;
import reactor.util.Loggers;

class MockChannelStatsListener implements ChannelStatsListener {
  private final String endpointAddress;

  private MockChannelStatsListener(String endpointAddress) {
    log.info("created {}", endpointAddress);
    this.endpointAddress = endpointAddress;
  }

  @Override
  public void onChannelActive() {
    log.info("onChannelActive");
  }

  @Override
  public void onChannelInactive(Stopwatch ticker) {
    log.info("onInactive {}", ticker);
  }

  @Override
  public void onClose() {
    log.info("onClose");
  }

  @Override
  public void onException(Throwable cause) {
    log.error("onException", cause);
  }

  @Override
  public void onWrite(long bytes) {
    log.info("onWrite {}", bytes);
  }

  @Override
  public void onRead(long bytes) {
    log.info("onRead {}", bytes);
  }

  static class Factory extends ChannelStatsListenerFactory {
    @Override
    public ChannelStatsListener newListener(String endpointAddress) {
      Objects.requireNonNull(endpointAddress, "endpointAddress cannot be null");
      return new MockChannelStatsListener(endpointAddress);
    }
  }

  private static final Logger log = Loggers.getLogger(MockChannelStatsListener.class);
}
