package io.simpleide.thread;

import io.simpleide.thread.nodes.NodePattern;

public final class ThreadMaster {

    public static final NodePattern ROOT = new NodePattern() {
        @Override
        public Runnable onStart() {
            return null;
        }

        @Override
        public Runnable onClose() {
            return null;
        }

        @Override
        public Integer level() {
            return 0;
        }
    };

    
}
