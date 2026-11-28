package io.simpleide.api.internal;

import java.io.IOException;

import javax.swing.table.AbstractTableModel;

/**
 * Internal ASCII interpretation model paired with the byte table.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
public final class HexAsciiTableModel extends AbstractTableModel {

	private final HexByteStore store;

	public HexAsciiTableModel(HexByteStore store) {
		this.store = store;
	}

	@Override
	public int getRowCount() {
		long rows = store.size() == 0 ? 0 : 1 + (store.size() - 1) / 16;
		return (int) Math.min(Integer.MAX_VALUE, rows);
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
