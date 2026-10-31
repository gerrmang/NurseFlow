@AGENTS.md

# NurseFlow project context for Claude Code

- Start with `docs/mvp-scope.md` and `docs/monthly-roster-rules-and-two-person-plan.md` for the agreed demo MVP and expanded scheduling rules, then read `docs/requirements.md`, `docs/openapi.yaml`, `docs/project-plan.md`, and `docs/test-plan.md` as needed. The latest user decisions in these scope documents win when older planning text differs.
- Follow `docs/ai-development-review-workflow.md`: use Opus for design decisions and Sonnet for bounded implementation; Codex independently reviews new changes every 30 minutes.
- The imported `AGENTS.md` contains OMX runtime workflows. Use those only when that runtime is available; its repository rules and Lore commit protocol still apply in Claude Code.
- The repo has `backend/` (Kotlin Spring Boot + local H2) and `android/` (Kotlin Compose Figma prototype). The responsive web client and Kakao login/invitation flow are not implemented yet.
- Inspect `git status --short --branch` and the remote branch heads before edits. Start new feature work from the latest reviewed `dev`; see `docs/claude-next-stage.md` after the foundation PR is merged. Base worktrees on the intended branch and preserve uncommitted work.
- The server is authoritative for roster constraints and publication. Approved leave and half-day intervals are entered by the ward administrator. Nurses read the published ward/personal calendar; self-entry and preferences are outside this demo MVP.
- Use fictional demo staff only. Never place Kakao credentials, real nurse information, or leave reasons in code, fixtures, logs, prompts, or commits.
- Work in small feature branches from `dev`. Submit feature PRs to `dev`; release with a reviewed `dev` to `main` PR. Do not merge or push to `main` directly.
- Follow `docs/claude-next-stage.md`: use feature-first packages with separate Controller, Request/Response DTO, Service, Domain, and Persistence responsibilities; implement one numbered feature unit per branch and PR, then stop for verification before the next unit.
- For backend errors and identity, follow `docs/backend-error-auth-dto-conventions.md`: stable ErrorCode/BusinessException, central MVC and security-filter error responses, explicit actor passing to services, and Kotlin request/response data classes.
