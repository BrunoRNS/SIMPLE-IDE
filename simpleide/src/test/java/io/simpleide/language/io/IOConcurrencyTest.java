package io.simpleide.language.io;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import io.simpleide.language.io.holder.InputHolder;
import io.simpleide.language.io.holder.OutputHolder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class IOConcurrencyTest {

  private static final int MESSAGE_COUNT = 1_000;

  @Test
  void inputSafelyTransfersMessagesFromProducerToConsumer() throws Exception {
    Input input = new Input();

    assertConcurrentTransfer(input::set, input::get);
  }

  @Test
  void outputSafelyTransfersMessagesFromProducerToConsumer() throws Exception {
    Output output = new Output();

    assertConcurrentTransfer(output::set, output::get);
  }

  @Test
  void holdersExposeTheSharedInputAndOutputQueues() {
    assertThat(Input.getInstance()).isSameAs(InputHolder.INSTANCE);
    assertThat(Output.getInstance()).isSameAs(OutputHolder.INSTANCE);
  }

  private void assertConcurrentTransfer(MessagePublisher publisher, MessageReader reader)
      throws Exception {
    List<String> expected = new ArrayList<>(MESSAGE_COUNT);
    for (int index = 0; index < MESSAGE_COUNT; index++) {
      expected.add("message-" + index);
    }

    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<?> producer =
          executor.submit(
              () -> {
                start.await();
                expected.forEach(publisher::publish);
                return null;
              });
      Future<List<String>> consumer =
          executor.submit(
              () -> {
                start.await();
                List<String> received = new ArrayList<>(MESSAGE_COUNT);
                for (int index = 0; index < MESSAGE_COUNT; index++) {
                  String message = reader.read(2);
                  assertThat(message).as("message %s should arrive", index).isNotNull();
                  received.add(message);
                }
                return received;
              });

      start.countDown();
      producer.get(10, SECONDS);
      assertThat(consumer.get(10, SECONDS)).containsExactlyElementsOf(expected);
    } finally {
      executor.shutdownNow();
      assertThat(executor.awaitTermination(5, SECONDS)).isTrue();
    }
  }

  @FunctionalInterface
  private interface MessagePublisher {
    void publish(String message);
  }

  @FunctionalInterface
  private interface MessageReader {
    String read(long timeoutSeconds) throws InterruptedException;
  }
}
