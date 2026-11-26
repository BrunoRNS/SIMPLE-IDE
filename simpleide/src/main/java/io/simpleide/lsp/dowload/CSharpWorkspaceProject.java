package io.simpleide.lsp.dowload;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Creates an MSBuild project for C# files that are not part of a project yet. */
/**
 * @author BrunoRNS
 * @since v1.1.0
 */
final class CSharpWorkspaceProject {

	private static final Logger LOGGER = LoggerFactory.getLogger(CSharpWorkspaceProject.class);

	private static final String PROJECT_FILE = "SimpleIdeLooseFiles.csproj";

	private CSharpWorkspaceProject() {
	}

	static void createIfNeeded(Path workspaceRoot) {
		if (workspaceRoot == null) {
			return;
		}
		Path root = workspaceRoot.toAbsolutePath().normalize();
		List<Path> sources;
		try (var paths = Files.list(root)) {
			List<Path> entries = paths.toList();
			boolean hasProject = entries.stream()
				.map(path -> path.getFileName().toString())
				.anyMatch(name -> name.endsWith(".sln") || name.endsWith(".csproj") || name.endsWith(".csx"));
			if (hasProject) {
				return;
			}
			sources = entries.stream()
				.filter(Files::isRegularFile)
				.filter(path -> path.getFileName().toString().endsWith(".cs"))
				.sorted()
				.toList();
		}
		catch (IOException error) {
			LOGGER.warn("Unable to list the C# files in {}.", root, error);
			return;
		}
		if (sources.isEmpty()) {
			return;
		}
		try {
			Files.writeString(root.resolve(PROJECT_FILE), projectFile(sources), StandardCharsets.UTF_8,
					StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
		}
		catch (IOException error) {
			LOGGER.warn("Unable to create a C# project for the loose files in {}; OmniSharp will not analyze them.",
					root, error);
		}
	}

	private static String projectFile(List<Path> sources) {
		StringBuilder project = new StringBuilder();
		project.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
			.append("<Project ToolsVersion=\"15.0\" ")
			.append("xmlns=\"http://schemas.microsoft.com/developer/msbuild/2003\">\n")
			.append("  <Import Project=\"$(MSBuildExtensionsPath)\\$(MSBuildToolsVersion)\\Microsoft.Common.props\"")
			.append(" Condition=\"Exists('$(MSBuildExtensionsPath)\\$(MSBuildToolsVersion)\\Microsoft.Common.props')\" />\n")
			.append("  <PropertyGroup>\n")
			.append("    <OutputType>Exe</OutputType>\n")
			.append("    <TargetFrameworkVersion>v4.7.2</TargetFrameworkVersion>\n")
			.append("    <RootNamespace>SimpleIdeLooseFiles</RootNamespace>\n")
			.append("    <AssemblyName>SimpleIdeLooseFiles</AssemblyName>\n")
			.append("  </PropertyGroup>\n")
			.append("  <ItemGroup>\n");
		for (Path source : sources) {
			project.append("    <Compile Include=\"")
				.append(escapeXmlAttribute(source.getFileName().toString()))
				.append("\" />\n");
		}
		project.append("  </ItemGroup>\n")
			.append("  <Import Project=\"$(MSBuildToolsPath)\\Microsoft.CSharp.targets\" />\n")
			.append("</Project>\n");
		return project.toString();
	}

	private static String escapeXmlAttribute(String value) {
		return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
	}

}
