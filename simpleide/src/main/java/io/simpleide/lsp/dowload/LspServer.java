package io.simpleide.lsp.dowload;

import java.util.List;

import io.simpleide.language.config.LangPlatform;

public class LspServer {

    private String id;
    private String version;
    private String name;
    private String languageId;

    private List<String> os;
    private List<String> arch;
    private String minimumVersion;
    private String url;
    private String command;
    private List<String> arguments;

    public LspServer(String id, String version, String name, String languageId, List<String> os, List<String> arch,
            String minimumVersion, String url, String command, List<String> arguments) {
        this.id = id;
        this.version = version;
        this.name = name;
        this.languageId = languageId;
        this.os = os;
        this.arch = arch;
        this.minimumVersion = minimumVersion;
        this.url = url;
        this.command = command;
        this.arguments = arguments;
    }

    public boolean isSupportedOnCurrentPlatform() {
        LangPlatform current = LangPlatform.getCurrentPlatform();

        if (current == LangPlatform.UNKNOWN) {
            return false;
        }

        boolean osSupported = os != null && os.stream()
                .anyMatch(supportedOs -> supportedOs.equalsIgnoreCase(current.getOs()));

        boolean archSupported = arch != null && arch.stream()
                .anyMatch(supportedArch -> supportedArch.equalsIgnoreCase(current.getArchitecture()));

        if (!archSupported && arch != null) {
            archSupported = arch.stream().anyMatch(supportedArch -> {
                String normalized = supportedArch.toLowerCase();
                if (current.getArchitecture().equals("x64")) {
                    return normalized.contains("x86_64") || normalized.contains("x64");
                }
                if (current.getArchitecture().equals("aarch64")) {
                    return normalized.contains("arm64") || normalized.contains("aarch64");
                }
                return false;
            });
        }

        return osSupported && archSupported;
    }

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

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}
