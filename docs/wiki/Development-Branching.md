# Branch model

Simple IDE uses a stable branch, a next-version branch and optional maintenance
branches for released versions.

## Branches

| Branch | Lifetime | Purpose |
| --- | --- | --- |
| `main` | Permanent | Latest stable release |
| `develop` | While the next version is open | Integration branch for the next release |
| `develop-vX.X.X-lts` | Until that version leaves support | Bug fixes for one released version |
| `feature/*`, `fix/*`, `chore/*` | Until merged and deleted | One contribution |

## The rules

1. `main` always contains the latest stable release.
2. When work starts on a new version, create `develop` from `main`.
3. When the next version is ready, merge `develop` into `main`.
4. When the first stable release `vX.X.X` is published, create
   `develop-vX.X.X-lts` from `main` (or from the release commit).
5. While `vX.X.X` is the current stable release, version-specific bug fixes go
   from `develop-vX.X.X-lts` into `main`, so the stable branch stays patched.
6. New features for the next version go to `develop`.
7. Contributions branch from the branch they target:
   - New feature for the next version: `develop`.
   - Fix for the current stable version: `develop-vX.X.X-lts` if it exists,
     otherwise `main`.
   - When `develop` does not exist, start from `main`.
8. When a version leaves support, delete its `develop-vX.X.X-lts` branch. The
   source remains in the GitHub release and tag.

## Why `develop`

`main` must stay releasable. `develop` lets unfinished work land without making
the stable branch unstable. A stable release is a merge of `develop` into `main`
followed by a tag and a GitHub release.

## Worked examples

### New feature for the next version

```sh
git fetch origin
git switch develop
git pull --ff-only origin develop
git switch -c feature/editor-minimap
# commit, test, push
# open a pull request targeting develop
```

### Fix for the current stable version

Assume the current stable version is `1.0.0` and
`develop-v1.0.0-lts` exists:

```sh
git fetch origin
git switch develop-v1.0.0-lts
git pull --ff-only origin develop-v1.0.0-lts
git switch -c fix/terminal-close-race
# commit, test, push
# open a pull request targeting develop-v1.0.0-lts
```

After the fix is merged, it is merged into `main` as a version-specific fix:

```sh
git switch main
git pull --ff-only origin main
git merge --no-ff develop-v1.0.0-lts
git push origin main
```

Use a normal merge, not a rebase, so the stable history keeps the maintenance
merge visible.

### First release of a new version

Assume `develop` contains version `1.1.0` and the release is approved:

```sh
git switch main
git pull --ff-only origin main
git merge --no-ff develop
git tag -a v1.1.0 -m "Simple IDE v1.1.0"
git push origin main
git push origin v1.1.0
```

Then create the maintenance branch for the new stable version:

```sh
git branch develop-v1.1.0-lts v1.1.0
git push origin develop-v1.1.0-lts
```

Start the next `develop` from `main` when the next version opens.

### Beta release

A version containing `beta` is published as a GitHub pre-release. The branch
rules are the same; only the release flag differs. The release workflow reads
the version from `simpleide.properties` and sets `--prerelease` automatically.

### Backport during a new version's development

If a fix must land in both the stable version and the next version:

1. Merge the fix into `develop-vX.X.X-lts`.
2. Merge the maintenance branch into `main`.
3. Merge `main` into `develop` (or cherry-pick the fix) so the next version also
   has it.

Do not merge `develop` into `main` just to backport a fix. That would ship
unfinished next-version work.

## Release tags

- Tag format: `v<version>` using the version from `simpleide.properties`.
- Example: `v1.0.0beta`.
- Annotated tags are preferred.
- Tags are immutable. If a release is wrong, publish a new version instead of
  moving a tag.
- GitHub releases are created by the release workflow on a push to `main`, not
  manually.

## Branch protection

Recommended settings for `main` and `develop`:

- Require pull requests.
- Require the CI workflows for at least Linux x64, Windows x64 and macOS ARM64.
- Require the branch to be up to date before merging.
- Require conversation resolution.
- Do not allow force pushes.
- Do not allow deletion.

## Deleting old branches

When a version leaves support:

1. Announce the end of support in the release notes.
2. Delete `develop-vX.X.X-lts` on the remote.
3. Keep the tag and GitHub release, including source archives.
4. Do not rewrite or delete release tags.

This keeps the branch list small while preserving the exact source of every
release.

## Common mistakes

| Mistake | Problem | Correct action |
| --- | --- | --- |
| Opening a feature PR against `main` while `develop` exists | Ships unfinished work to stable | Target `develop` |
| Branching a stable fix from `develop` | Includes unreleased changes | Branch from `develop-vX.X.X-lts` |
| Merging `develop` into `main` for one backport | Unstable next-version code reaches stable | Merge the maintenance branch |
| Force-pushing a shared branch | Breaks other contributors | Add commits instead |
| Deleting an old release tag | Loses the exact source | Keep tags, delete only the maintenance branch |
