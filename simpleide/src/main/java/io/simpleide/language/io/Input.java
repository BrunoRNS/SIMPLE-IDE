package io.simpleide.language.io;

import io.simpleide.language.io.holder.InputHolder;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class Input {

  private final BlockingQueue<String> queue = new LinkedBlockingQueue<>();

  public void set(String text) {
    if (text != null) {
      queue.offer(text);
    }
  }

  public String get(long waitSeconds) throws InterruptedException {
    return queue.poll(waitSeconds, TimeUnit.SECONDS);
  }

  public static Input getInstance() {
    return InputHolder.INSTANCE;
  }
}
