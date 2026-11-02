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

        @Override
        public boolean isEDT() {
            throw new UnsupportedOperationException("Not supported yet.");
        }
    };

    public static ThreadMaster getInstance() {
        return ThreadMasterHolder.INSTANCE;
    }

    public void register(NodePattern node) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    
}
