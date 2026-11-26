package io.simpleide.git;

import java.util.Locale;

/**
 * Builds an English explanation for Git authentication failures.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
public final class GitAuthMessage {

	private GitAuthMessage() {
	}

	/**
	 * Determines whether a Git error indicates missing authentication.
	 * @param output command output and error text
	 * @return true when authentication is likely the failure cause
	 */
	public static boolean isAuthenticationFailure(String output) {
		if (output == null) {
			return false;
		}
		String text = output.toLowerCase(Locale.ROOT);
		return text.contains("authentication failed") || text.contains("permission denied")
				|| text.contains("could not read username") || text.contains("no credential")
				|| text.contains("unable to read key") || text.contains("terminal prompts disabled")
				|| text.contains("publickey");
	}

	/**
	 * Determines whether a Git command tried to ask the user for input.
	 * @param output command output and error text
	 * @return true when the command required interactive input
	 */
	public static boolean isInteractionFailure(String output) {
		if (output == null) {
			return false;
		}
		String text = output.toLowerCase(Locale.ROOT);
		return text.contains("terminal prompts disabled") || text.contains("could not read username")
				|| text.contains("could not read password") || text.contains("could not read from prompt")
				|| text.contains("askpass") || text.contains("batch mode") || text.contains("stdin: is not a tty")
				|| text.contains("no such device or address")
				|| text.contains("are you sure you want to continue connecting")
				|| text.contains("host key verification failed");
	}

	/**
	 * Returns the guidance popup text for a command that required user input.
	 * @param operation operation that failed
	 * @return English explanation and setup commands
	 */
	public static String messageFor(String operation) {
		return "Git could not authenticate while trying to " + operation + ".\n\n"
				+ "Configure credentials outside the application:\n" + "  gh auth login\n" + "  gh auth setup-git\n\n"
				+ "Alternatively, configure SSH for the Git CLI and add an SSH key to your Git service.\n\n"
				+ "This application does not perform login or store credentials.";
	}

	/**
	 * Returns the guidance popup text for a command stopped because it wanted input.
	 * @param operation operation that failed
	 * @return English explanation and setup commands
	 */
	public static String interactionMessageFor(String operation) {
		return "Git tried to request input from the user while trying to " + operation
				+ ", so the operation was stopped instead of waiting for input.\n\n"
				+ "This application never accepts interactive Git prompts. Configure credentials, SSH keys or\n"
				+ "commit identity outside the application:\n" + "  gh auth login\n" + "  gh auth setup-git\n\n"
				+ "Alternatively, configure SSH for the Git CLI and add an SSH key to your Git service.";
	}

}
