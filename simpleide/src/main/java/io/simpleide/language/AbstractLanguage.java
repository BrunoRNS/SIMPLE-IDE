package io.simpleide.language;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simpleide.language.exceptions.ProcessAlreadyExistsException;
import io.simpleide.language.exceptions.ProcessFatalException;
import io.simpleide.language.io.Output;
import io.simpleide.language.io.holder.InputHolder;
import io.simpleide.thread.ThreadMaster;
import io.simpleide.thread.nodes.runnable.RunnableNode;

public abstract class AbstractLanguage implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractLanguage.class);
    
    protected ProcessBuilder pb;
    protected volatile Process p;
    protected volatile boolean stopped = false;
    private volatile RunnableNode inputForwarder;

    protected String langFriendly = null;
    protected String langConstant = null;

    public String getLangFriendly() { return this.langFriendly; }
    public String getLangConstant() { return this.langConstant; }

    @Override
    public void run() {
        if (pb == null) {
            LOGGER.error("[IDE] Cannot run language process because its command was not configured.");
            return;
        }

        synchronized (this) {
            if (this.p != null && this.p.isAlive()) {
                throw new ProcessAlreadyExistsException(
                    "Impossible to Run the script while still running another", this.p);
            }
        }
        
        try {
            pb.redirectErrorStream(true); 
            
            p = pb.start();
            startInputForwarder(p);

            p.onExit().thenAccept(this.getHandler());

            try (Scanner scanner = new Scanner(p.getInputStream())) {
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine();
                    Output.getInstance().set(line);
                    LOGGER.info("[OUTPUT] {}", line);
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

    private void startInputForwarder(Process process) {
        RunnableNode forwarder = new RunnableNode(() -> {
            try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(process.getOutputStream())
            )) {
                while (process.isAlive()) {
                    String line = InputHolder.INSTANCE.get(1);
                    if (line != null) {
                        writer.write(line);
                        writer.newLine();
                        writer.flush();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException e) {
                if (process.isAlive()) {
                    LOGGER.debug("[IDE] Could not forward input to the language process.", e);
                }
            }
        });
        inputForwarder = forwarder;
        ThreadMaster threadMaster = ThreadMaster.getInstance();
        threadMaster.initialize();
        threadMaster.register(forwarder);
    }

    protected RunnableNode startManaged() {
        ThreadMaster threadMaster = ThreadMaster.getInstance();
        threadMaster.initialize();
        RunnableNode node = new RunnableNode(this);
        threadMaster.register(node);
        return node;
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
                    LOGGER.info("[IDE] Process manually interrupted by the user.");
                    return;
                }
                LOGGER.error("[OUTPUT] RUNTIME ERROR: {}", exitCode);
                LOGGER.error("[Runtime Error] The developer's program broke with code: {}", exitCode);
            } else {
                LOGGER.info("[IDE] Process completed successfully.");
            }
        } finally {
            RunnableNode forwarder = inputForwarder;
            inputForwarder = null;
            if (forwarder != null) {
                ThreadMaster.getInstance().removeNodeCascade(forwarder);
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
            LOGGER.debug("[Debug] The interrupt thread was itself interrupted.", e);
            Thread.currentThread().interrupt();
        }

    }

}
