package io.simpleide.api.internal;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;

import javax.swing.table.AbstractTableModel;

/**
 * Internal editable byte-table model.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
public final class HexByteTableModel extends AbstractTableModel {

	private final HexByteStore store;

	private final Runnable refreshAscii;

	private final Runnable changed;

	public HexByteTableModel(HexByteStore store, Runnable refreshAscii, Runnable changed) {
		this.store = store;
		this.refreshAscii = refreshAscii;
		this.changed = changed;
	}

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
		refreshAscii.run();
		changed.run();
	}

}
