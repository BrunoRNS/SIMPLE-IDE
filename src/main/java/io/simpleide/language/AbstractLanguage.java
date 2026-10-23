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

public abstract class AbstractLanguage 
implements Runnable
{
    
    protected ProcessBuilder pb;
    protected volatile Process p;
    protected volatile boolean stopped = false;
    
    protected final StringBuffer systemLog = new StringBuffer();
    protected final StringBuffer userLog = new StringBuffer();

    protected String input = "";

    protected String langFriendly = null;
    protected String langConstant = null;

    public String getLangFriendly() {
        return this.langFriendly;
    }

    public String getLangConstant() {
        return this.langConstant;
    }

    public void setInput(String s) {
        input = s;
    }

    public void appendInput(String s) {
        input += "\n" + s;
    }

    public void run() {
        if (this.p != null) {
            throw new ProcessAlreadyExistsException(
                "Impossible to Run the script while still running another",
                this.p);
        }
        try {

            File f = null;

            if (input != null && !input.isEmpty() && !input.isBlank()) {

                f = File.createTempFile(
                    UUID.randomUUID().toString().replace("-", ""),
                    ".in"
                );
                Files.writeString(f.toPath(), input);

            }

            if (f != null) {
                pb.redirectInput(f);
            }

            pb.redirectErrorStream(true); 
            p = pb.start();
            p.onExit().thenAccept(this.getHandler());

            try (final Scanner scanner = new Scanner(
                p.getInputStream()
            )) {
                
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine();
                    addUserLog(line);
                }

            } catch (Exception e) {
                
                throw new ProcessFatalException(
                    "Process Did not Finish Succesfully",
                    new IOException(e)
                );

            }


        } catch (IOException e) {

            throw new ProcessFatalException(
                "Process Did not Finish Succesfully",
                e
            );

        }
    }

    protected void addUserLog(String s) {
        userLog.append(s).append('\n');
    }

    protected void addSystemLog(String s) {
        systemLog.append(s).append('\n');
    }

    public Thread getThread() {
        return new Thread(this);
    }

    protected Consumer<Process> getHandler() {
        return new InternalExitHandler();
    }

    protected final class InternalExitHandler 
    implements Consumer<Process> 
    {
        @Override
        public final void accept(Process t) {
            handleExit(t);
        }

    }

    protected void handleExit(Process t) {
        int exitCode = t.exitValue();

        this.p = null;

        if (exitCode != 0) {
            if (stopped) {
                stopped = false;
                addSystemLog("[IDE] Process manually interrupted by the user.\n");
                return;
            }

            addUserLog("RUNTIME ERROR: " + exitCode);
            addSystemLog("[Runtime Error] The developer's program broke with code: " + exitCode + "\n");
            
        } else {
            addSystemLog("[IDE] Process completed successfully.\n");
        }

        this.p = null;

    }

    public void forceStop() throws ProcessFatalException {
        if (this.p == null || !this.p.isAlive()) {
            return;
        }

        this.stopped = true;
        this.p.destroyForcibly();

        try {
            boolean killed = this.p.waitFor(3, TimeUnit.SECONDS);
            
            if (!killed) {
                throw new ProcessFatalException("The process refused to die within 3 seconds.", this.p);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            addSystemLog("[Debug] The interrupt thread was itself interrupted.\n");
        }
    
    }

    public String getUserLog() { return userLog.toString(); }
    public String getSystemLog() { return systemLog.toString(); }

}
