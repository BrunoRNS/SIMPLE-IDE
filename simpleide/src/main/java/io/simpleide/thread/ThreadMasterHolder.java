package io.simpleide.thread;


public final class ThreadMasterHolder {

    public static final ThreadMaster INSTANCE = ThreadMaster.getInstance();

    private ThreadMasterHolder() {}

}
