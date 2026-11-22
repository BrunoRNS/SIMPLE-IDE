package io.simpleide.api;

import java.awt.BorderLayout;
import java.awt.Font;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingWorker;

/**
 * Memory-bounded, paginated read-only view for large UTF-8 text files.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
public final class LargeTextViewer extends JPanel implements AutoCloseable {

	private static final int PAGE_BYTES = 256 * 1024;

	private final Path path;

	private final long size;

	private final JTextArea text = new JTextArea();

	private final JLabel status = new JLabel();

	private final JButton previous = new JButton("Previous");

	private final JButton next = new JButton("Next");

	private final AtomicLong loadGeneration = new AtomicLong();

	private long page;

	private volatile boolean closed;

	/**
	 * Creates a read-only text viewer that loads one bounded page at a time.
	 * @param path UTF-8 text file
	 * @param size file size in bytes
	 */
	public LargeTextViewer(Path path, long size) {
		super(new BorderLayout(0, 6));
		this.path = path.toAbsolutePath().normalize();
		this.size = Math.max(0, size);
		text.setEditable(false);
		text.setFont(new Font(Font.MONOSPACED, Font.PLAIN, text.getFont().getSize()));
		JPanel header = new JPanel(new BorderLayout());
		header.add(new JLabel("Large text file — read-only paged view"), BorderLayout.WEST);
		header.add(status, BorderLayout.EAST);
		JPanel navigation = new JPanel();
		previous.addActionListener(event -> showPage(page - 1));
		next.addActionListener(event -> showPage(page + 1));
		navigation.add(previous);
		navigation.add(next);
		add(header, BorderLayout.NORTH);
		add(new JScrollPane(text), BorderLayout.CENTER);
		add(navigation, BorderLayout.SOUTH);
		showPage(0);
	}

	/** Returns the zero-based page currently displayed. */
	public long currentPage() {
		return page;
	}

	/** Returns the number of bytes retained in the page buffer. */
	public int pageCapacityBytes() {
		return PAGE_BYTES;
	}

	private void showPage(long requestedPage) {
		if (closed) {
			return;
		}
		long pageCount = Math.max(1, size == 0 ? 1 : 1 + (size - 1) / PAGE_BYTES);
		page = Math.max(0, Math.min(requestedPage, pageCount - 1));
		long selectedPage = page;
		long generation = loadGeneration.incrementAndGet();
		status.setText("Loading page " + (page + 1) + " of " + pageCount + "...");
		previous.setEnabled(false);
		next.setEnabled(false);
		new SwingWorker<String, Void>() {
			@Override
			protected String doInBackground() throws IOException {
				return readPage(selectedPage);
			}

			@Override
			protected void done() {
				if (closed || generation != loadGeneration.get()) {
					return;
				}
				try {
					text.setText(get());
					text.setCaretPosition(0);
					status.setText("Page " + (selectedPage + 1) + " of " + pageCount);
					previous.setEnabled(selectedPage > 0);
					next.setEnabled(selectedPage + 1 < pageCount);
				}
				catch (InterruptedException error) {
					Thread.currentThread().interrupt();
					showReadError(selectedPage, error);
				}
				catch (ExecutionException error) {
					showReadError(selectedPage, error.getCause());
				}
			}
		}.execute();
	}

	private String readPage(long pageNumber) throws IOException {
		long start = pageNumber * PAGE_BYTES;
		int length = (int) Math.min(PAGE_BYTES + 4L, Math.max(0, size - start));
		ByteBuffer buffer = ByteBuffer.allocate(length);
		try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
			channel.position(start);
			while (buffer.hasRemaining() && channel.read(buffer) != -1) {
				// Read only this page; the buffer is capped at PAGE_BYTES.
			}
		}
		int bytesRead = buffer.position();
		int startOffset = 0;
		if (pageNumber > 0) {
			while (startOffset < bytesRead && isContinuation(buffer.array()[startOffset])) {
				startOffset++;
			}
		}
		int endOffset = Math.min(PAGE_BYTES, bytesRead);
		if (endOffset < bytesRead) {
			int codePointStart = endOffset - 1;
			while (codePointStart >= startOffset && isContinuation(buffer.array()[codePointStart])) {
				codePointStart--;
			}
			if (codePointStart >= startOffset) {
				int expectedLength = utf8Length(buffer.array()[codePointStart]);
				if (codePointStart + expectedLength > endOffset) {
					endOffset = Math.min(bytesRead, codePointStart + expectedLength);
				}
			}
		}
		return new String(buffer.array(), startOffset, Math.max(0, endOffset - startOffset), StandardCharsets.UTF_8);
	}

	private static boolean isContinuation(byte value) {
		return (value & 0xC0) == 0x80;
	}

	private static int utf8Length(byte firstByte) {
		int value = firstByte & 0xff;
		if ((value & 0x80) == 0) {
			return 1;
		}
		if ((value & 0xE0) == 0xC0) {
			return 2;
		}
		if ((value & 0xF0) == 0xE0) {
			return 3;
		}
		return 4;
	}

	private void showReadError(long failedPage, Throwable error) {
		text.setText("");
		status.setText("Unable to read page: " + error.getMessage());
		previous.setEnabled(failedPage > 0);
		long pageCount = Math.max(1, size == 0 ? 1 : 1 + (size - 1) / PAGE_BYTES);
		next.setEnabled(failedPage + 1 < pageCount);
	}

	@Override
	public void close() {
		closed = true;
		loadGeneration.incrementAndGet();
		Runnable release = () -> text.setText("");
		if (javax.swing.SwingUtilities.isEventDispatchThread()) {
			release.run();
		}
		else {
			javax.swing.SwingUtilities.invokeLater(release);
		}
	}

}
