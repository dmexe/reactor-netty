package reactor.ipc.netty.stats;

class NoopChannelStatsListener implements ChannelStatsListener {

  static final ChannelStatsListener INSTANCE = new NoopChannelStatsListener();

  @Override
  public void onChannelActive() {
  }

  @Override
  public void onChannelInactive(Stopwatch ticker) {
  }

  @Override
  public void onClose() {
  }

  @Override
  public void onException(Throwable cause) {
  }

  @Override
  public void onWrite(long bytes) {
  }

  @Override
  public void onRead(long bytes) {
  }
}
