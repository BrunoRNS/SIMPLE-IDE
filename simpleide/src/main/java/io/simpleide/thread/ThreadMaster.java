package io.simpleide.thread;

import io.simpleide.thread.nodes.NodePattern;

public final class ThreadMaster extends AbstractThreadMaster {

    private ThreadMaster() {
        super(ThreadMaster.ROOT);
    }

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

        @Override
        public boolean isEDT() {
            return false;
        }
    };

    public static ThreadMaster getInstance() {
        return Holder.INSTANCE;
    }

    private static final class Holder {
        private static final ThreadMaster INSTANCE = new ThreadMaster();
    }
}
