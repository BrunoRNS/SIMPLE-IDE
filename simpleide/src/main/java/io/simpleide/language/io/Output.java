package io.simpleide.language.io;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import io.simpleide.language.io.holder.OutputHolder;

public class Output {

    private final BlockingQueue<String> queue = new LinkedBlockingQueue<>();

    public void set(String text) {
        if (text != null) {
            queue.offer(text);
        }
    }

    public String get(long waitSeconds) throws InterruptedException {
        return queue.poll(waitSeconds, TimeUnit.SECONDS);
    }

    public static Output getInstance() {
        return OutputHolder.INSTANCE;
    }
    
}
