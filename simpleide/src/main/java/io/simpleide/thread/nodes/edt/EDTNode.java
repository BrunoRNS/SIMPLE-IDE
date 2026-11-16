package io.simpleide.thread.nodes.edt;

import io.simpleide.thread.ThreadMaster;
import io.simpleide.thread.nodes.Node;
import io.simpleide.thread.nodes.NodePattern;

/**
 * Thread tree node that always executes on the Swing Event Dispatch Thread.
 *
 * <p>
 * The node is rooted at the thread manager's root, reports level one and returns
 * {@code true} from {@link #isEDT()}. Subclasses such as the listener adapters in
 * {@code io.simpleide.thread.nodes.edt.listeners} inherit this behavior.
 *
 * @author BrunoRNS
 * @since v1.0.0beta
 */
public class EDTNode extends Node {

	public EDTNode() {
		super(ThreadMaster.getInstance().getROOT());
	}

	public EDTNode(NodePattern actual) {
		super(ThreadMaster.getInstance().getROOT(), actual);
	}

	public EDTNode(Runnable onStart) {
		super(ThreadMaster.getInstance().getROOT(), onStart);
	}

	public EDTNode(Runnable onStart, Runnable onClose) {
		super(ThreadMaster.getInstance().getROOT(), onStart, onClose);
	}

	@Override
	public Runnable onStart() {
		return this.start;
	}

	@Override
	public Runnable onClose() {
		return this.close;
	}

	@Override
	public Integer level() {
		return 1;
	}

	@Override
	public boolean isEDT() {
		return true;
	}

}
