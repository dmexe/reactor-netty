package reactor.ipc.netty.stats;

public interface Stopwatch {
  Long elapsedNanos();

  static Stopwatch createStarted() {
    return new DefaultStopWatch();
  }
}
