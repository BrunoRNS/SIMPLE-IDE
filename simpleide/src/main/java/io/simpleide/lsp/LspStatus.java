package io.simpleide.lsp;

public record LspStatus(String id, String serverName, State state) {
    public enum State {
        CHECKING_VERSION,
        DOWNLOADING,
        EXTRACTING,
        INSTALLING,
        STARTING,
        RUNNING,
        FAILED,
        IDLE
    }
}
