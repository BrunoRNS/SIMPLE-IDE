package io.simpleide.api;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumnModel;

import io.simpleide.ui.ThemePalette;

/**
 * Read/write hexadecimal editor with an ASCII interpretation beside each byte row. A
 * path-backed editor reads and caches only a few file chunks at a time.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
public final class HexEditor extends JPanel implements AutoCloseable {

	private static final int CHUNK_SIZE = 64 * 1024;

	private static final int MAX_CACHED_CHUNKS = 4;

	private static final int MAX_ARRAY_BYTES = 16 * 1024 * 1024;

	private static final int MAX_CLIPBOARD_CHARS = 1024 * 1024;

	private static final int MAX_COPY_ROWS = 20_000;

	private final ByteStore store;

	private final ByteModel model;

	private final AsciiModel asciiModel;

	private final JTable table;

	private final JTable asciiTable;

	private Runnable changeListener = () -> {
	};

	/**
	 * Opens a file using bounded, on-demand chunk reads.
	 * @param path file to read
	 * @throws IOException if the file cannot be opened
	 */
	public HexEditor(Path path) throws IOException {
		this(new FileStore(Objects.requireNonNull(path, "path")));
	}

	/**
	 * Opens a byte array in the editable hex table.
	 * @param content bytes to edit
	 */
	public HexEditor(byte[] content) {
		this(new MemoryStore(content));
	}

	private HexEditor(ByteStore store) {
		super(new BorderLayout());
		this.store = store;
		model = new ByteModel();
		asciiModel = new AsciiModel();
		table = new JTable(model);
		asciiTable = new JTable(asciiModel);
		table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		table.setCellSelectionEnabled(true);
		table.setFillsViewportHeight(true);
		table.setDefaultRenderer(Object.class, new HexCellRenderer());
		asciiTable.setFillsViewportHeight(true);
		asciiTable.setRowSelectionAllowed(false);
		asciiTable.setCellSelectionEnabled(false);
		asciiTable.setFocusable(false);
		asciiTable.setTableHeader(null);
		asciiTable.setDefaultRenderer(Object.class, new AsciiCellRenderer());
		asciiTable.setRowHeight(table.getRowHeight());

		TableColumnModel columns = table.getColumnModel();
		columns.getColumn(0).setPreferredWidth(130);
		columns.getColumn(0).setCellRenderer(new OffsetRenderer());
		for (int column = 1; column < columns.getColumnCount(); column++) {
			columns.getColumn(column).setPreferredWidth(38);
		}
		asciiTable.getColumnModel().getColumn(0).setPreferredWidth(190);

		JPanel bytesPanel = new JPanel(new BorderLayout());
		bytesPanel.add(new JLabel("Offset / Hex"), BorderLayout.NORTH);
		JScrollPane bytesScroll = new JScrollPane(table);
		bytesPanel.add(bytesScroll, BorderLayout.CENTER);

		JPanel asciiPanel = new JPanel(new BorderLayout());
		asciiPanel.add(new JLabel("ASCII"), BorderLayout.NORTH);
		JScrollPane asciiScroll = new JScrollPane(asciiTable);
		asciiScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		asciiScroll.getVerticalScrollBar().setModel(bytesScroll.getVerticalScrollBar().getModel());
		asciiPanel.add(asciiScroll, BorderLayout.CENTER);

		javax.swing.JSplitPane split = new javax.swing.JSplitPane(javax.swing.JSplitPane.HORIZONTAL_SPLIT, bytesPanel,
				asciiPanel);
		split.setResizeWeight(0.8);
		add(split, BorderLayout.CENTER);

		JPanel controls = new JPanel();
		controls.add(new JButton(new AbstractAction("Copy") {
			@Override
			public void actionPerformed(ActionEvent event) {
				copySelection();
			}
		}));
		controls.add(new JButton(new AbstractAction("Paste") {
			@Override
			public void actionPerformed(ActionEvent event) {
				pasteSelection();
			}
		}));
		add(controls, BorderLayout.NORTH);
		table.getInputMap().put(KeyStroke.getKeyStroke("control C"), "hex.copy");
		table.getActionMap().put("hex.copy", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent event) {
				copySelection();
			}
		});
		table.getInputMap().put(KeyStroke.getKeyStroke("control V"), "hex.paste");
		table.getActionMap().put("hex.paste", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent event) {
				pasteSelection();
			}
		});
	}

	/** Installs a listener invoked when the byte content changes. */
	public void setChangeListener(Runnable listener) {
		changeListener = Objects.requireNonNull(listener, "listener");
	}

	/** Returns the file size without reading its contents. */
	public long byteCount() {
		return store.size();
	}

	/** Returns a byte at the given zero-based file offset. */
	public int getByte(long offset) {
		checkOffset(offset);
		try {
			return store.getByte(offset);
		}
		catch (IOException error) {
			throw new UncheckedIOException("Unable to read byte at offset " + offset, error);
		}
	}

	/** Returns a copy of the edited bytes, only when the file is at most 16 MiB. */
	public byte[] bytes() {
		if (store.size() > MAX_ARRAY_BYTES) {
			throw new IllegalStateException("File is too large to return as one byte array; use getByte or save.");
		}
		try {
			byte[] content = new byte[(int) store.size()];
			for (int index = 0; index < content.length; index++) {
				content[index] = (byte) store.getByte(index);
			}
			return content;
		}
		catch (IOException error) {
			throw new UncheckedIOException("Unable to read edited bytes.", error);
		}
	}

	/** Replaces one byte. */
	public void setByte(int offset, int value) {
		setByte((long) offset, value);
	}

	/**
	 * Replaces one byte.
	 * @param offset zero-based byte offset
	 * @param value unsigned byte value from 0 through 255
	 */
	public void setByte(long offset, int value) {
		checkOffset(offset);
		if (value < 0 || value > 255) {
			throw new IllegalArgumentException("Byte values must be between 0 and 255.");
		}
		long row = offset / 16;
		int column = (int) (offset % 16) + 1;
		if (row > Integer.MAX_VALUE) {
			throw new IndexOutOfBoundsException("Byte offset is outside the visible table range: " + offset);
		}
		model.setValueAt(String.format("%02X", value), (int) row, column);
	}

	/** Returns whether the byte content differs from its last saved value. */
	public boolean isModified() {
		return store.isModified();
	}

	/** Applies application theme colors to the byte and ASCII tables. */
	public void applyTheme(ThemePalette palette) {
		Objects.requireNonNull(palette, "palette");
		setBackground(palette.background());
		setForeground(palette.foreground());
		for (JTable view : new JTable[] { table, asciiTable }) {
			view.setBackground(palette.background());
			view.setForeground(palette.foreground());
			view.setSelectionBackground(palette.selectionBackground());
			view.setSelectionForeground(palette.selectionForeground());
			view.setGridColor(palette.border());
			if (view.getTableHeader() != null) {
				view.getTableHeader().setBackground(palette.headerBackground());
				view.getTableHeader().setForeground(palette.headerForeground());
			}
		}
	}

	/** Writes the edited bytes and clears the modified state. */
	public void save(Path path) throws IOException {
		store.save(Objects.requireNonNull(path, "path"));
		changeListener.run();
	}

	/** Copies selected byte cells as bounded, space-separated hexadecimal text. */
	public void copySelection() {
		int[] columns = table.getSelectedColumns();
		int firstRow = table.getSelectionModel().getMinSelectionIndex();
		int lastRow = table.getSelectionModel().getMaxSelectionIndex();
		if (firstRow < 0 || columns.length == 0) {
			return;
		}
		StringBuilder selected = new StringBuilder();
		int finalRow = (int) Math.min(lastRow, (long) firstRow + MAX_COPY_ROWS - 1);
		boolean copiedRow = false;
		for (int row = firstRow; row <= finalRow && selected.length() < MAX_CLIPBOARD_CHARS; row++) {
			boolean copiedCell = false;
			for (int column : columns) {
				if (column == 0 || !table.isCellSelected(row, column)) {
					continue;
				}
				String value = Objects.toString(model.getValueAt(row, column));
				int separatorLength = copiedCell || copiedRow ? 1 : 0;
				if (selected.length() + separatorLength + value.length() > MAX_CLIPBOARD_CHARS) {
					break;
				}
				if (copiedCell) {
					selected.append(' ');
				}
				else if (copiedRow) {
					selected.append('\n');
				}
				selected.append(value);
				copiedCell = true;
			}
			copiedRow |= copiedCell;
		}
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(selected.toString()), null);
	}

	/**
	 * Pastes hexadecimal byte tokens from the clipboard starting at the selected cell.
	 */
	public void pasteSelection() {
		try {
			String text = (String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
			if (text.length() > MAX_CLIPBOARD_CHARS) {
				throw new IllegalArgumentException("Clipboard content exceeds the 1 MiB paste limit.");
			}
			int row = Math.max(0, table.getSelectedRow());
			int column = Math.max(1, table.getSelectedColumn());
			for (String token : text.trim().split("\\s+")) {
				if (token.isEmpty()) {
					continue;
				}
				if (row >= model.getRowCount()) {
					break;
				}
				model.setValueAt(token, row, column);
				if (++column > 16) {
					column = 1;
					row++;
				}
			}
		}
		catch (UnsupportedFlavorException | IOException error) {
			throw new IllegalStateException("Unable to paste hexadecimal bytes.", error);
		}
	}

	@Override
	public void close() throws IOException {
		store.close();
	}

	private void checkOffset(long offset) {
		if (offset < 0 || offset >= store.size()) {
			throw new IndexOutOfBoundsException("Byte offset is outside the file: " + offset);
		}
	}

	private final class ByteModel extends AbstractTableModel {

		@Override
		public int getRowCount() {
			long rows = store.size() == 0 ? 0 : 1 + (store.size() - 1) / 16;
			return (int) Math.min(Integer.MAX_VALUE, rows);
		}

		@Override
		public int getColumnCount() {
			return 17;
		}

		@Override
		public String getColumnName(int column) {
			return column == 0 ? "Offset" : String.format("%02X", column - 1);
		}

		@Override
		public Object getValueAt(int row, int column) {
			long offset = (long) row * 16;
			if (column == 0) {
				return String.format("%016X", offset);
			}
			long index = offset + column - 1;
			if (index >= store.size()) {
				return "";
			}
			try {
				return String.format("%02X", store.getByte(index));
			}
			catch (IOException error) {
				return "??";
			}
		}

		@Override
		public boolean isCellEditable(int row, int column) {
			return column > 0 && (long) row * 16 + column - 1 < store.size();
		}

		@Override
		public void setValueAt(Object value, int row, int column) {
			if (!isCellEditable(row, column)) {
				return;
			}
			String hex = Objects.toString(value, "").trim();
			if (!hex.matches("(?i)[0-9a-f]{1,2}")) {
				throw new IllegalArgumentException("Enter one or two hexadecimal digits.");
			}
			long offset = (long) row * 16 + column - 1;
			try {
				if (store.getByte(offset) == Integer.parseInt(hex, 16)) {
					return;
				}
				store.setByte(offset, Integer.parseInt(hex, 16));
			}
			catch (IOException error) {
				throw new UncheckedIOException("Unable to edit byte at offset " + offset, error);
			}
			fireTableCellUpdated(row, column);
			asciiModel.fireTableRowsUpdated(row, row);
			changeListener.run();
		}

	}

	private final class AsciiModel extends AbstractTableModel {

		@Override
		public int getRowCount() {
			return model.getRowCount();
		}

		@Override
		public int getColumnCount() {
			return 1;
		}

		@Override
		public Object getValueAt(int row, int column) {
			StringBuilder text = new StringBuilder(16);
			long start = (long) row * 16;
			for (int index = 0; index < 16 && start + index < store.size(); index++) {
				try {
					int value = store.getByte(start + index);
					text.append(value >= 32 && value <= 126 ? (char) value : '.');
				}
				catch (IOException error) {
					text.append('?');
				}
			}
			return text.toString();
		}

	}

	private interface ByteStore extends AutoCloseable {

		long size();

		int getByte(long offset) throws IOException;

		void setByte(long offset, int value) throws IOException;

		boolean isModified();

		void save(Path path) throws IOException;

		@Override
		void close() throws IOException;

	}

	private static final class MemoryStore implements ByteStore {

		private final byte[] original;

		private final byte[] data;

		private boolean modified;

		private MemoryStore(byte[] content) {
			original = Objects.requireNonNull(content, "content").clone();
			data = original.clone();
		}

		@Override
		public long size() {
			return data.length;
		}

		@Override
		public int getByte(long offset) {
			return data[(int) offset] & 0xff;
		}

		@Override
		public void setByte(long offset, int value) {
			data[(int) offset] = (byte) value;
			modified = !Arrays.equals(data, original);
		}

		@Override
		public boolean isModified() {
			return modified;
		}

		@Override
		public void save(Path path) throws IOException {
			Files.write(path, data);
			System.arraycopy(data, 0, original, 0, data.length);
			modified = false;
		}

		@Override
		public void close() {
		}

	}

	private static final class FileStore implements ByteStore {

		private Path source;

		private FileChannel sourceChannel;

		private final long size;

		private final LinkedHashMap<Long, Chunk> chunks = new LinkedHashMap<>(8, 0.75f, true);

		private Path overlayPath;

		private Path dirtyPath;

		private FileChannel overlayChannel;

		private FileChannel dirtyChannel;

		private boolean modified;

		private FileStore(Path path) throws IOException {
			source = path.toAbsolutePath().normalize();
			size = Files.size(source);
			sourceChannel = FileChannel.open(source, StandardOpenOption.READ);
		}

		@Override
		public long size() {
			return size;
		}

		@Override
		public synchronized int getByte(long offset) throws IOException {
			Chunk chunk = chunk(offset / CHUNK_SIZE);
			return chunk.data[(int) (offset % CHUNK_SIZE)] & 0xff;
		}

		@Override
		public synchronized void setByte(long offset, int value) throws IOException {
			Chunk chunk = chunk(offset / CHUNK_SIZE);
			chunk.data[(int) (offset % CHUNK_SIZE)] = (byte) value;
			chunk.dirty = true;
			modified = true;
		}

		@Override
		public synchronized boolean isModified() {
			return modified;
		}

		@Override
		public synchronized void save(Path targetPath) throws IOException {
			Path target = targetPath.toAbsolutePath().normalize();
			for (Chunk chunk : chunks.values()) {
				flush(chunk);
			}
			if (!target.equals(source)) {
				Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
			}
			if (dirtyChannel != null) {
				try (FileChannel destination = FileChannel.open(target, StandardOpenOption.WRITE)) {
					long pageCount = size == 0 ? 0 : 1 + (size - 1) / CHUNK_SIZE;
					copyDirtyPages(destination, pageCount);
				}
			}
			if (sourceChannel != null) {
				sourceChannel.close();
			}
			source = target;
			sourceChannel = FileChannel.open(source, StandardOpenOption.READ);
			clearOverlay();
			chunks.clear();
			modified = false;
		}

		@Override
		public synchronized void close() throws IOException {
			if (sourceChannel != null) {
				sourceChannel.close();
			}
			clearOverlay();
			chunks.clear();
		}

		private Chunk chunk(long page) throws IOException {
			Chunk chunk = chunks.get(page);
			if (chunk == null) {
				long start = page * CHUNK_SIZE;
				int length = (int) Math.min(CHUNK_SIZE, size - start);
				byte[] data = new byte[length];
				ByteBuffer buffer = ByteBuffer.wrap(data);
				FileChannel channel = isDirty(page) ? overlayChannel : sourceChannel;
				if (channel != null) {
					readFully(channel, buffer, start);
				}
				chunk = new Chunk(page, data);
				chunks.put(page, chunk);
				if (chunks.size() > MAX_CACHED_CHUNKS) {
					Iterator<Map.Entry<Long, Chunk>> iterator = chunks.entrySet().iterator();
					Chunk eldest = iterator.next().getValue();
					flush(eldest);
					iterator.remove();
				}
			}
			return chunk;
		}

		private void flush(Chunk chunk) throws IOException {
			if (!chunk.dirty) {
				return;
			}
			ensureOverlay();
			long start = chunk.page * CHUNK_SIZE;
			writeFully(overlayChannel, ByteBuffer.wrap(chunk.data), start);
			markDirty(chunk.page);
			chunk.dirty = false;
		}

		private boolean isDirty(long page) throws IOException {
			if (dirtyChannel == null) {
				return false;
			}
			long markerOffset = page / 8;
			ByteBuffer marker = ByteBuffer.allocate(1);
			readFully(dirtyChannel, marker, markerOffset);
			return (marker.array()[0] & (1 << (page % 8))) != 0;
		}

		private void markDirty(long page) throws IOException {
			long markerOffset = page / 8;
			ByteBuffer marker = ByteBuffer.allocate(1);
			readFully(dirtyChannel, marker, markerOffset);
			byte value = (byte) (marker.array()[0] | (1 << (page % 8)));
			writeFully(dirtyChannel, ByteBuffer.wrap(new byte[] { value }), markerOffset);
		}

		private void copyDirtyPages(FileChannel destination, long pageCount) throws IOException {
			ByteBuffer markers = ByteBuffer.allocate(8192);
			ByteBuffer pageData = ByteBuffer.allocate(CHUNK_SIZE);
			long markerCount = pageCount == 0 ? 0 : 1 + (pageCount - 1) / 8;
			for (long markerOffset = 0; markerOffset < markerCount;) {
				int markerLength = (int) Math.min(markers.capacity(), markerCount - markerOffset);
				markers.clear();
				markers.limit(markerLength);
				readFully(dirtyChannel, markers, markerOffset);
				for (int index = 0; index < markerLength; index++) {
					int dirtyBits = markers.array()[index] & 0xff;
					while (dirtyBits != 0) {
						int bit = Integer.numberOfTrailingZeros(dirtyBits);
						long page = (markerOffset + index) * 8 + bit;
						long start = page * CHUNK_SIZE;
						int length = (int) Math.min(CHUNK_SIZE, size - start);
						pageData.clear();
						pageData.limit(length);
						readFully(overlayChannel, pageData, start);
						pageData.flip();
						writeFully(destination, pageData, start);
						dirtyBits &= dirtyBits - 1;
					}
				}
				markerOffset += markerLength;
			}
		}

		private void ensureOverlay() throws IOException {
			if (overlayChannel != null) {
				return;
			}
			overlayPath = Files.createTempFile("simpleide-hex-edits-", ".bin");
			dirtyPath = Files.createTempFile("simpleide-hex-dirty-", ".map");
			overlayChannel = FileChannel.open(overlayPath, StandardOpenOption.READ, StandardOpenOption.WRITE);
			dirtyChannel = FileChannel.open(dirtyPath, StandardOpenOption.READ, StandardOpenOption.WRITE);
		}

		private void clearOverlay() throws IOException {
			if (overlayChannel != null) {
				overlayChannel.close();
				dirtyChannel.close();
				overlayChannel = null;
				dirtyChannel = null;
				Files.deleteIfExists(overlayPath);
				Files.deleteIfExists(dirtyPath);
				overlayPath = null;
				dirtyPath = null;
			}
		}

		private static void readFully(FileChannel channel, ByteBuffer buffer, long position) throws IOException {
			while (buffer.hasRemaining()) {
				int read = channel.read(buffer, position + buffer.position());
				if (read < 0) {
					break;
				}
				if (read == 0) {
					Thread.onSpinWait();
				}
			}
		}

		private static void writeFully(FileChannel channel, ByteBuffer buffer, long position) throws IOException {
			while (buffer.hasRemaining()) {
				int written = channel.write(buffer, position + buffer.position());
				if (written == 0) {
					Thread.onSpinWait();
				}
			}
		}

	}

	private static final class Chunk {

		private final long page;

		private final byte[] data;

		private boolean dirty;

		private Chunk(long page, byte[] data) {
			this.page = page;
			this.data = data;
		}

	}

	private static class HexCellRenderer extends DefaultTableCellRenderer {

		@Override
		public Component getTableCellRendererComponent(JTable source, Object value, boolean selected, boolean focused,
				int row, int column) {
			super.getTableCellRendererComponent(source, value, selected, focused, row, column);
			setHorizontalAlignment(CENTER);
			return this;
		}

	}

	private static final class OffsetRenderer extends HexCellRenderer {

		@Override
		public Dimension getPreferredSize() {
			Dimension size = super.getPreferredSize();
			size.width = Math.max(size.width, 130);
			return size;
		}

	}

	private static final class AsciiCellRenderer extends DefaultTableCellRenderer {

		@Override
		public Component getTableCellRendererComponent(JTable source, Object value, boolean selected, boolean focused,
				int row, int column) {
			super.getTableCellRendererComponent(source, value, selected, focused, row, column);
			setFont(new Font(Font.MONOSPACED, Font.PLAIN, source.getFont().getSize()));
			return this;
		}

	}

}
