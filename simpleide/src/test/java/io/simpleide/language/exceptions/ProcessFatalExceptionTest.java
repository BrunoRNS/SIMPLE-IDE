package io.simpleide.language.exceptions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.junit.jupiter.api.Test;

class ProcessFatalExceptionTest {

    @Test
    void retainsIoCause() {
        IOException cause = new IOException("launch failure");
        ProcessFatalException exception = new ProcessFatalException("could not launch", cause);

        assertSame(cause, exception.getCause());
        assertNull(exception.getProcessCause());
        assertEquals("could not launch", exception.getMessage());
    }

    @Test
    void retainsProcessCause() {
        Process process = new StubProcess();
        ProcessFatalException exception = new ProcessFatalException("process did not stop", process);

        assertSame(process, exception.getProcessCause());
        assertEquals("process did not stop", exception.getCause().getMessage());
    }

    private static final class StubProcess extends Process {
        @Override
        public OutputStream getOutputStream() {
            return OutputStream.nullOutputStream();
        }

        @Override
        public InputStream getInputStream() {
            return InputStream.nullInputStream();
        }

        @Override
        public InputStream getErrorStream() {
            return InputStream.nullInputStream();
        }

        @Override
        public int waitFor() {
            return 0;
        }

        @Override
        public int exitValue() {
            return 0;
        }

        @Override
        public void destroy() {
        }
    }
}
