package io.simpleide.thread;

import io.simpleide.thread.nodes.NodePattern;

public final class ThreadMasterHolder {

  public static final ThreadMaster INSTANCE =
      new ThreadMaster(
          new NodePattern() {
            @Override
            public Runnable onStart() {
              return () -> {};
            }

            @Override
            public Runnable onClose() {
              return null;
            }

            @Override
            public Integer level() {
              return 0;
            }

            @Override
            public boolean isEDT() {
              return true;
            }
          });
}
