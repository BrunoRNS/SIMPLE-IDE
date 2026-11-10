package io.simpleide.lsp.dowload;

import java.util.List;

public class LspServer {

    private String id;
    private String version;
    private String name;
    private String languageId;

    private List<String> os;
    private List<String> arch;
    private String minimumVersion;

    private String command;
    private List<String> arguments;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLanguageId() {
        return languageId;
    }

    public void setLanguageId(String languageId) {
        this.languageId = languageId;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public List<String> getArguments() {
        return arguments;
    }

    public void setArguments(List<String> arguments) {
        this.arguments = arguments;
    }

    public List<String> getOs() {
        return os;
    }

    public void setOs(List<String> os) {
        this.os = os;
    }

    public List<String> getArch() {
        return arch;
    }

    public void setArch(List<String> arch) {
        this.arch = arch;
    }

    public String getMinimumVersion() {
        return minimumVersion;
    }

    public void setMinimumVersion(String minimumVersion) {
        this.minimumVersion = minimumVersion;
    }
}
