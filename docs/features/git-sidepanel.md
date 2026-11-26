# Git side panel

The Source Control activity opens a Git side panel for the repository associated
with the folder open in Explorer. It shows repository status and commit history,
and provides branch and commit operations through JGit. Git work runs in the
background so the Swing event thread stays responsive; operations that need
credentials report setup guidance instead of waiting on an interactive prompt.

The panel is documented alongside the rest of the
[application UI](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/API-UI-Application). Repository access uses the JGit
library listed in [third-party dependencies](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Third-Party-Maven).
