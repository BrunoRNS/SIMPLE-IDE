package io.simpleide.thread;

import io.simpleide.thread.nodes.NodePattern;

/**
 * Holder for the lazily initialized thread manager singleton.
 *
 * <p>
 * The instance is created when the class is first loaded. The root node runs on the Swing
 * Event Dispatch Thread at level zero, as required by the thread tree validation rules.
 *
 * @author BrunoRNS
 * @since v1.0.0beta
 */
public final class ThreadMasterHolder {

	public static final ThreadMaster INSTANCE = new ThreadMaster(new NodePattern() {
		@Override
		public Runnable onStart() {
			return () -> {
			};
		}

		@Override
		public Runnable onClose() {
			return null;
		}

		@Override
		public Integer level() {
			return 0;
		}

		@Override
		public boolean isEDT() {
			return true;
		}
	});

}
