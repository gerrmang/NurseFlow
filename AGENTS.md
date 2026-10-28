# NurseFlow Repository Instructions

Before changing application code, configuration, documentation, tests, or deployment files:

1. Read `docs/runbooks/git-flow.md` and inspect `git status --short --branch`.
2. Do not make feature changes directly on `main` or `dev`; use a work branch created from `dev`.
3. Preserve unrelated user changes. Do not overwrite, revert, stash, commit, push, or delete changes outside the current task.
4. Keep roster hard rules enforced on the server. The Android UI is not the authority for schedule validity.
5. Follow the PR template and record verification, skipped checks, API/DB changes, deployment impact, and rollback where applicable.
6. Do not include secrets, leave reasons, or health information in logs, examples, or fixtures.

The repository currently has no configured remote. Do not claim GitHub PR review, branch protection, or CI enforcement until a remote repository is connected and those settings are verified.
