package reactor.ipc.netty.stats;

import java.util.ServiceLoader;
import javax.annotation.Nullable;

public abstract class ChannelStatsListenerFactory {
  private static final ServiceLoader<ChannelStatsListenerFactory> serviceLoader =
      ServiceLoader.load(ChannelStatsListenerFactory.class);

  @SuppressWarnings("WeakerAccess")
  public abstract ChannelStatsListener newListener(String endpointAddress);

  @Nullable
  public static ChannelStatsListenerFactory getDefaultFactory() {
    //noinspection LoopStatementThatDoesntLoop
    for (ChannelStatsListenerFactory factory : serviceLoader) {
      return factory;
    }

    return null;
  }
}