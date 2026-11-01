package io.simpleide.language;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import io.simpleide.language.exceptions.ProcessAlreadyExistsException;
import io.simpleide.language.exceptions.ProcessFatalException;

public abstract class AbstractLanguage implements Runnable {
    
    protected ProcessBuilder pb;
    protected volatile Process p;
    protected volatile boolean stopped = false;
    
    protected final StringBuilder systemLog = new StringBuilder();
    protected final StringBuilder userLog = new StringBuilder();

    protected String input = "";
    protected String langFriendly = null;
    protected String langConstant = null;
    
    private File currentTempInputFile = null;

    public String getLangFriendly() { return this.langFriendly; }
    public String getLangConstant() { return this.langConstant; }
    public void setInput(String s) { input = s; }
    
    public synchronized void appendInput(String s) {
        input += "\n" + s;
    }

    @Override
    public void run() {

        synchronized (this) {
            if (this.p != null && this.p.isAlive()) {
                throw new ProcessAlreadyExistsException(
                    "Impossible to Run the script while still running another", this.p);
            }
        }
        
        try {
            if (input != null && !input.isBlank()) {
                currentTempInputFile = File.createTempFile(
                    UUID.randomUUID().toString().replace("-", ""),
                    ".in"
                );
                currentTempInputFile.deleteOnExit();
                Files.writeString(currentTempInputFile.toPath(), input);
                pb.redirectInput(currentTempInputFile);
            }

            pb.redirectErrorStream(true); 
            
            p = pb.start();
            
            p.onExit().thenAccept(this.getHandler());

            try (Scanner scanner = new Scanner(p.getInputStream())) {
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine();
                    addUserLog(line);
                }
            } catch (Exception e) {
                if (!stopped) {
                    throw new ProcessFatalException("Process Did not Finish Successfully during stream reading", new IOException(e));
                }
            }

        } catch (IOException e) {
            throw new ProcessFatalException("Process Did not Finish Successfully at startup", e);
        }
    }

    protected synchronized void addUserLog(String s) {
        userLog.append("[OUTPUT] ").append(s).append('\n');
    }

    protected synchronized void addSystemLog(String s) {
        systemLog.append(s).append('\n');
    }

    public synchronized String getUserLog() { return userLog.toString(); }
    public synchronized String getSystemLog() { return systemLog.toString(); }

    protected Thread getThread() {
        Thread t = new Thread(this);
        t.setDaemon(true);
        return t;
    }

    protected Consumer<Process> getHandler() {
        return new InternalExitHandler();
    }

    protected final class InternalExitHandler implements Consumer<Process> {
        @Override
        public final void accept(Process t) {
            handleExit(t);
        }
    }

    protected void handleExit(Process t) {
        try {
            int exitCode = t.exitValue();

            if (exitCode != 0) {
                if (stopped) {
                    addSystemLog("[IDE] Process manually interrupted by the user.");
                    return;
                }
                addUserLog("RUNTIME ERROR: " + exitCode);
                addSystemLog("[Runtime Error] The developer's program broke with code: " + exitCode);
            } else {
                addSystemLog("[IDE] Process completed successfully.");
            }
        } finally {
            if (currentTempInputFile != null && currentTempInputFile.exists()) {
                currentTempInputFile.delete();
            }
            this.p = null;
            this.stopped = false;
        }
    }

    public void forceStop() throws ProcessFatalException {
        Process process = this.p;
        if (process == null || !process.isAlive()) {
            return;
        }

        this.stopped = true;
        process.destroyForcibly();

        try {
            boolean killed = process.waitFor(3, TimeUnit.SECONDS);
            if (!killed) {
                throw new ProcessFatalException("The process refused to die within 3 seconds.", process);
            }
        } catch (InterruptedException e) {
            addSystemLog("[Debug] The interrupt thread was itself interrupted.");
            Thread.currentThread().interrupt();
        }

    }

}

