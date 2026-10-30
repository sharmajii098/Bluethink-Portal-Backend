# BlueThink RC4 upgrade branch

This is a proposed source-code replacement created from the local RC4 completion workspace.
It is intentionally on a NEW BRANCH, not the original default branch.
Do not merge or deploy until code review, four-role application acceptance,
secret scanning, configuration reconciliation, and migration signoff.
Runtime uploads, database dumps, credentials, build files, and GitHub Actions
are deliberately excluded. The shared root deploy/ tooling has NOT been migrated
to either repository and requires a separate reviewed release plan.
