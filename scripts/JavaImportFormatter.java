import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Applies and validates the repository's Java import grouping.
 *
 * <p>
 * Usage: {@code java scripts/JavaImportFormatter.java --apply} or
 * {@code java scripts/JavaImportFormatter.java --check}.
 */
public final class JavaImportFormatter {

	private static final Pattern IMPORT = Pattern
		.compile("^\\s*import\\s+(static\\s+)?([^;\\s]+)\\s*;\\s*(//.*)?$");

	private JavaImportFormatter() {
	}

	public static void main(String[] args) throws IOException {
		if (args.length < 1 || args.length > 2) {
			usage();
			System.exit(2);
		}
		boolean apply = switch (args[0]) {
			case "--apply" -> true;
			case "--check" -> false;
			default -> {
				usage();
				yield false;
			}
		};
		if (!args[0].equals("--apply") && !args[0].equals("--check")) {
			System.exit(2);
		}

		Path root = (args.length == 2 ? Path.of(args[1]) : Path.of(".")).toAbsolutePath().normalize();
		List<Path> files;
		try (var paths = Files.walk(root)) {
			files = paths.filter(Files::isRegularFile)
				.filter(JavaImportFormatter::isJavaSource)
				.filter(path -> !isExcluded(root.relativize(path)))
				.sorted()
				.toList();
		}

		List<String> failures = new ArrayList<>();
		int changed = 0;
		for (Path file : files) {
			try {
				String source = Files.readString(file, StandardCharsets.UTF_8);
				FormatResult result = format(source);
				if (result.error() != null) {
					failures.add(root.relativize(file) + ": " + result.error());
				}
				else if (!source.equals(result.source())) {
					if (apply) {
						Files.writeString(file, result.source(), StandardCharsets.UTF_8);
						changed++;
					}
					else {
						failures.add(root.relativize(file) + ": imports are not in the required order");
					}
				}
			}
			catch (IOException exception) {
				failures.add(root.relativize(file) + ": " + exception.getMessage());
			}
		}

		if (apply && changed > 0) {
			System.out.println("Reordered imports in " + changed + " Java file(s).");
		}
		for (String failure : failures) {
			System.err.println(failure);
		}
		if (!failures.isEmpty()) {
			System.exit(1);
		}
		System.out.println(apply ? "Java imports are formatted." : "Java import formatting is valid.");
	}

	private static FormatResult format(String source) {
		String lineSeparator = lineSeparator(source);
		String[] lines = source.split("\\r\\n|\\n|\\r", -1);
		int firstImport = -1;
		int lastImport = -1;
		List<ImportEntry> imports = new ArrayList<>();

		for (int index = 0; index < lines.length; index++) {
			Matcher matcher = IMPORT.matcher(lines[index]);
			if (matcher.matches()) {
				if (firstImport < 0) {
					firstImport = index;
				}
				lastImport = index;
			}
		}
		if (firstImport < 0) {
			return new FormatResult(source, null);
		}

		for (int index = firstImport; index <= lastImport; index++) {
			String line = lines[index];
			if (line.isBlank()) {
				continue;
			}
			Matcher matcher = IMPORT.matcher(line);
			if (!matcher.matches()) {
				return new FormatResult(source, "comments or other text inside the import block need manual review");
			}
			imports.add(new ImportEntry(matcher.group(1) != null, matcher.group(2), matcher.group(3)));
		}

		List<String> formattedImports = formatImports(imports);
		List<String> updatedLines = new ArrayList<>(lines.length + formattedImports.size());
		for (int index = 0; index < firstImport; index++) {
			updatedLines.add(lines[index]);
		}
		updatedLines.addAll(formattedImports);
		for (int index = lastImport + 1; index < lines.length; index++) {
			updatedLines.add(lines[index]);
		}
		return new FormatResult(String.join(lineSeparator, updatedLines), null);
	}

	private static List<String> formatImports(List<ImportEntry> imports) {
		List<List<ImportEntry>> groups = List.of(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
				new ArrayList<>(), new ArrayList<>());
		for (ImportEntry entry : imports) {
			groups.get(groupOf(entry)).add(entry);
		}

		List<String> formatted = new ArrayList<>();
		for (List<ImportEntry> group : groups) {
			if (group.isEmpty()) {
				continue;
			}
			group.sort(Comparator.comparing(ImportEntry::name));
			if (!formatted.isEmpty()) {
				formatted.add("");
			}
			for (ImportEntry entry : group) {
				String line = "import " + (entry.isStatic() ? "static " : "") + entry.name() + ";";
				if (entry.comment() != null) {
					line += " " + entry.comment();
				}
				formatted.add(line);
			}
		}
		return formatted;
	}

	private static int groupOf(ImportEntry entry) {
		if (entry.isStatic()) {
			return 2;
		}
		if (entry.name().startsWith("java.")) {
			return 0;
		}
		if (entry.name().startsWith("javax.")) {
			return 1;
		}
		if (entry.name().startsWith("io.simpleide.")) {
			return 4;
		}
		return 3;
	}

	private static String lineSeparator(String source) {
		Matcher matcher = Pattern.compile("\\r\\n|\\n|\\r").matcher(source);
		return matcher.find() ? matcher.group() : System.lineSeparator();
	}

	private static boolean isJavaSource(Path path) {
		return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".java");
	}

	private static boolean isExcluded(Path relativePath) {
		for (Path part : relativePath) {
			String directory = part.toString();
			if (directory.equals(".git") || directory.equals(".idea") || directory.equals("target")
					|| directory.equals("build") || directory.equals("out")) {
				return true;
			}
		}
		return false;
	}

	private static void usage() {
		System.err.println("Usage: java scripts/JavaImportFormatter.java --apply|--check [directory]");
	}

	private record FormatResult(String source, String error) {
	}

	private record ImportEntry(boolean isStatic, String name, String comment) {
	}

}
