package io.simpleide.api;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Objects;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumnModel;

import io.simpleide.api.internal.HexAsciiTableModel;
import io.simpleide.api.internal.HexByteStore;
import io.simpleide.api.internal.HexByteStores;
import io.simpleide.api.internal.HexByteTableModel;
import io.simpleide.thread.nodes.edt.listeners.SwingActionNode;
import io.simpleide.ui.ThemePalette;

/**
 * Read/write hexadecimal editor with an ASCII interpretation beside each byte row. A
 * path-backed editor reads and caches only a few file chunks at a time.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
public final class HexEditor extends JPanel implements AutoCloseable {

	private static final int MAX_ARRAY_BYTES = 16 * 1024 * 1024;

	private static final int MAX_CLIPBOARD_CHARS = 1024 * 1024;

	private static final int MAX_COPY_ROWS = 20_000;

	private final HexByteStore store;

	private final HexByteTableModel model;

	private final HexAsciiTableModel asciiModel;

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
		this(HexByteStores.file(Objects.requireNonNull(path, "path")));
	}

	/**
	 * Opens a byte array in the editable hex table.
	 * @param content bytes to edit
	 */
	public HexEditor(byte[] content) {
		this(HexByteStores.memory(content));
	}

	private HexEditor(HexByteStore store) {
		super(new BorderLayout());
		this.store = store;
		asciiModel = new HexAsciiTableModel(store);
		model = new HexByteTableModel(store, () -> asciiModel.fireTableDataChanged(), () -> changeListener.run());
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
		controls.add(new JButton(new SwingActionNode("Copy", this::copySelection)));
		controls.add(new JButton(new SwingActionNode("Paste", this::pasteSelection)));
		add(controls, BorderLayout.NORTH);
		table.getInputMap().put(KeyStroke.getKeyStroke("control C"), "hex.copy");
		table.getActionMap().put("hex.copy", new SwingActionNode("Copy", this::copySelection));
		table.getInputMap().put(KeyStroke.getKeyStroke("control V"), "hex.paste");
		table.getActionMap().put("hex.paste", new SwingActionNode("Paste", this::pasteSelection));
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
