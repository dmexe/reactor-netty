package reactor.ipc.netty.stats;

public interface ChannelStatsListener {
  void onChannelActive();
  void onChannelInactive(Stopwatch ticker);
  void onClose();
  void onException(Throwable cause);
  void onWrite(long bytes);
  void onRead(long bytes);
}
