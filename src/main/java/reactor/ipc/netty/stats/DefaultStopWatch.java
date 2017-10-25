package reactor.ipc.netty.stats;

class DefaultStopWatch implements Stopwatch {
  private final long startedAt;

  DefaultStopWatch() {
    this.startedAt = System.nanoTime();
  }

  @Override
  public Long elapsedNanos() {
    return System.nanoTime() - startedAt;
  }

  @Override
  public String toString() {
    return "DefaultStopWatch{" + "startedAt=" + startedAt + '}';
  }
}
