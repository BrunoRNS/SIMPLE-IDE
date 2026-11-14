# Contributing to Simple IDE

Thank you for improving Simple IDE. This guide explains how to propose a change,
which branch to start from, how to test it and what a pull request must contain.

## Before you start

1. Read [Branch model](BRANCHING.md), [Code style](CODE_STYLE.md) and
   [Testing](TESTING.md).
2. Search existing issues and pull requests to avoid duplicate work.
3. For a large change, open an issue or a draft pull request first. Describe the
   problem, the proposed behavior and the affected modules.
4. Keep changes focused. A bug fix should not also reformat unrelated files.

## Requirements

- JDK 17 or newer.
- GNU Make (recommended) or the Maven Wrapper.
- Git.
- The external toolchain for any language you change: `javac`/`java`, `kotlinc`,
  `groovy`, `node`, `python3`, `gcc`/`g++` or `clang`, `mono`, `go`, `rustc`.
- A desktop environment for UI and terminal tests. Headless tests can still run
  the non-Swing parts.

Check your environment:

```sh
java -version
./mvnw -version
make help
```

## Branch model in one page

| Branch | Purpose | Created from | Merges into |
| --- | --- | --- | --- |
| `main` | Latest stable release | — | — |
| `develop` | Next version | `main` when next-version work starts | `main` when the next version is ready |
| `develop-vX.X.X-lts` | Maintenance for one stable version | `main` at the first stable `vX.X.X` release | `main` for version-specific fixes |
| `feature/<name>`, `chore/<name>`, `fix/<name>` | A single contribution | Current `develop`, or `main` if no `develop` exists, or the matching `develop-vX.X.X-lts` for an older-version fix | The branch it was created from |

Rules:

- Never open a feature pull request directly against `main` unless `main` is the
  only active development branch.
- If `develop` exists, it is the default target for new features.
- If you are fixing a released version, branch from
  `develop-vX.X.X-lts` and target that branch.
- When a version leaves support, its `develop-vX.X.X-lts` branch is deleted. The
  source remains available in the GitHub release and tag.
- Do not rewrite published history. Do not force-push a branch that others may
  have based work on.

See [Branch model](BRANCHING.md) for worked examples.

## Contribution workflow

1. **Fork** the repository (or create a branch if you have write access).
2. **Sync** with the branch you will target:

   ```sh
   git fetch origin
   git switch develop
   git pull --ff-only origin develop
   ```

3. **Create a branch**:

   ```sh
   git switch -c feature/terminal-tab-titles
   ```

4. **Make the change** in small commits.
5. **Format**:

   ```sh
   make format
   ```

6. **Test**:

   ```sh
   make test
   ```

   If a language toolchain is missing, run `make test-nolang` and say so in the
   pull request.

7. **Verify** the actual build output:

   ```sh
   make verify
   ```

8. **Push** and open a pull request against the correct base branch.
9. **Respond** to review comments. Add new commits; do not amend commits that
   were already reviewed unless the reviewer asks for a squash.

## Commit messages

Use a short imperative subject and an optional body:

```text
Add terminal tab titles from the working directory

The terminal previously used the session id as the tab title. Use the
current directory when the shell reports it, and fall back to the session
id before the first prompt.
```

Conventions:

- Subject line at most about 72 characters.
- No trailing period in the subject.
- Use `Add`, `Fix`, `Remove`, `Refactor`, `Document` or `Test`.
- Reference issues in the body (`Refs #123`, `Fixes #123`).
- One logical change per commit.
- Do not mix formatting-only changes with behavior changes.

## Pull request checklist

A pull request should contain:

- A clear title and description of the problem and solution.
- The target branch and, if relevant, the release version.
- Steps to reproduce the original problem.
- Tests added or updated, or an explanation of why no test is practical.
- Screenshots or screen recordings for visible UI changes.
- Documentation updates for behavior, API, commands or dependencies.
- A note about new external toolchains or runtime downloads.
- Confirmation that `make format-check` and the relevant test command pass.

Keep pull requests small enough to review. Split refactors and behavior changes
into separate pull requests.

## Formatting

The build uses Spotless with Google Java Format:

```sh
make format        # apply
make format-check  # verify
```

Do not format unrelated files. Spotless is bound to the `validate` phase, so a
normal build fails when a changed Java file is not formatted.

Configuration lives in the parent `pom.xml`:

- Spotless 2.44.3.
- Google Java Format 1.24.0.
- AOSP style disabled; default Google style is used.

See [Code style](CODE_STYLE.md) for naming, package and comment conventions that
the formatter does not enforce.

## Tests

Run the full suite before opening a pull request:

```sh
make test
```

Tests tagged `language-tools` execute real toolchains. If a tool is unavailable,
the test is skipped by the test code or the build is expected to exclude it with
`make test-nolang`. Do not silence a failure by removing a tag.

Add tests for:

- New behavior in a language runner.
- New thread-node behavior or cancellation paths.
- LSP registry, version selection and installation logic.
- Editor syntax mapping and file type detection.
- Terminal platform selection and BusyBox extraction.

See [Testing](TESTING.md) for test structure and commands.

## Adding a language

1. Create a subclass of `AbstractCompilerLanguage`, `AbstractInterpreterLanguage`
   or `AbstractBytecodeLanguage` in the appropriate package.
2. Implement the required command/toolchain detection.
3. Register it in `LangSetup`.
4. Add the extension mapping.
5. Add or update the prepackaged compiler handling if the language needs one.
6. Update [Languages](../reference/LANGUAGES.md) and the API [language table](../api/languages.md).
7. Add tests that do not require the toolchain, plus a `language-tools` test for
   the real execution path.
8. Document the toolchain requirement in
   [Runtime tools](../third-party/RUNTIME.md).

Do not add a language that shells out to an installer without documenting it.
Do not add binaries without their license and source/provenance information.

## Adding an LSP server

1. Add an entry to
   `simpleide/src/main/resources/io/simpleide/lsp/dowload/registry.json`.
2. Include OS, architecture, minimum version, version command and arguments,
   install strategy, launch command and arguments.
3. Verify that the server's `textDocument/languageId` matches the editor mapping.
4. Test installation on every supported OS/architecture you can access.
5. Add registry tests for version selection and platform filtering.
6. Update [LSP](../reference/LSP.md) and [LSP API](../api/lsp.md).

Registry entries can run package managers and start network processes. Review
the security and privacy implications before adding one.

## Adding a BusyBox binary

1. Place the binary under the correct platform directory:
   `simpleide/src/main/resources/io/simpleide/ui/terminal/core/busybox/`.
2. Keep the source archive in the same directory.
3. Add `LICENSE.txt` and `README.txt` with version, format, provenance and build
   notes.
4. Update `BusyBoxPlatform` if the path changes.
5. Update [BusyBox third-party notes](../third-party/BUSYBOX.md) and
   [Third-party licenses](../third-party/LICENSES.md).
6. Test terminal startup on the target platform.

BusyBox is GPLv2. Do not remove the source archive or license files.

## Documentation

Documentation is part of the change:

- Update the closest focused page, not a central mega-page.
- Keep all documentation in English.
- Use relative links.
- Update the index [`docs/README.md`](../README.md) if you add a page.
- Update API pages when a public facade changes.
- Include commands that work from the repository root.

Check for broken relative links before submitting.

## Dependencies

Adding a Maven dependency requires:

- A license compatible with BSD 3-Clause distribution.
- A version property in the parent `pom.xml` when the dependency is shared.
- An entry in [Maven dependencies](../third-party/MAVEN.md).
- An entry in [Third-party licenses](../third-party/LICENSES.md).
- A note about transitive dependencies that affect distribution.

Do not add a dependency for something the JDK already provides unless there is a
clear benefit. Do not add native binaries without a licensing and source plan.

## Security

- Never commit credentials, tokens, keys or personal paths.
- Do not log secrets.
- Treat downloaded archives and install commands as untrusted input; validate
  URLs and arguments.
- Process commands must not be built by concatenating untrusted text into a
  shell string.
- Report a security issue privately rather than in a public issue.

## Review expectations

Reviewers look for:

- Correctness and edge cases.
- Compliance with the branch model and scope.
- No unrelated formatting or refactoring.
- Tests that fail before the fix and pass after it.
- Resource cleanup (processes, threads, streams, temporary files).
- EDT correctness.
- Documentation and license updates.

## After merge

- Delete your feature branch.
- Do not delete release or LTS branches.
- If the change should be backported, open a separate pull request against the
  matching `develop-vX.X.X-lts` branch.
- Watch the CI workflows on the target branch.

## Getting help

Open a discussion or issue with:

- The exact command you ran.
- The Java version and OS.
- The full error output.
- Whether the problem reproduces on `main`, `develop` or a release tag.
