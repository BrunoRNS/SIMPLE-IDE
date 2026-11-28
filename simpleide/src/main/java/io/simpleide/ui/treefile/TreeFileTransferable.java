package io.simpleide.ui.treefile;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.util.List;

/**
 * @author BrunoRNS
 * @since v1.1.0
 */
final class TreeFileTransferable implements Transferable {

	static final DataFlavor TREE_FILE_FLAVOR = new DataFlavor(TreeFile.class, "TreeFile");

	private final List<TreeFile> files;

	TreeFileTransferable(List<TreeFile> files) {
		this.files = files;
	}

	@Override
	public DataFlavor[] getTransferDataFlavors() {
		return new DataFlavor[] { TREE_FILE_FLAVOR };
	}

	@Override
	public boolean isDataFlavorSupported(DataFlavor flavor) {
		return TREE_FILE_FLAVOR.equals(flavor);
	}

	@Override
	public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
		if (!isDataFlavorSupported(flavor)) {
			throw new UnsupportedFlavorException(flavor);
		}
		return files;
	}

}
