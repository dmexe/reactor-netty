package reactor.ipc.netty.rules;

import io.netty.buffer.PoolArenaMetric;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocatorMetric;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

// https://github.com/netty/netty/issues/5275

public class PooledByteBufAllocatorLeaks extends TestWatcher {

  private static class LeakDetected extends RuntimeException {
    LeakDetected(String message) {
      super(message);
    }
  }

  @Override
  protected void succeeded(Description description) {
    if (PooledByteBufAllocator.defaultUseCacheForAllThreads()) {
      return;
    }

    final PooledByteBufAllocatorMetric metrics = PooledByteBufAllocator.DEFAULT.metric();

    int numDirectActiveAllocations = 0, numDirectAllocations = 0, numDirectDeallocations = 0;

    for (PoolArenaMetric arena : metrics.directArenas()) {
      numDirectActiveAllocations += arena.numActiveAllocations();
      numDirectAllocations += arena.numAllocations();
      numDirectDeallocations += arena.numDeallocations();
    }

    int numHeapActiveAllocations = 0, numHeapAllocations = 0, numHeapDeallocations = 0;

    for (PoolArenaMetric arena : metrics.heapArenas()) {
      numHeapActiveAllocations += arena.numActiveAllocations();
      numHeapAllocations += arena.numAllocations();
      numHeapDeallocations += arena.numDeallocations();
    }

    final int activeAllocations = numDirectActiveAllocations + numHeapActiveAllocations;
    if (activeAllocations > 0) {

      String sb =
          ("PooledByteBufAllocator has " + activeAllocations + " active allocations ")
              + "Heap["
              + "activeAllocations=" + numHeapAllocations
              + " allocations=" + numHeapActiveAllocations
              + " deallocations=" + numHeapDeallocations
              + "] "
              + "Direct["
              + "activeAllocations=" + numDirectActiveAllocations
              + " allocations=" + numDirectAllocations
              + " deallocations="
              + numDirectDeallocations + " ]";

      throw  new LeakDetected(sb);
    }
  }
}
