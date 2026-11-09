# Project instructions

This is a Java 17 / JavaFX Windows application recovered from the user's installer.
Read README_RU.md and PROJECT_MAP_RU.md before edits. Keep user-facing communication in Russian.

- Treat src/ as editable sources. Do not edit only binary JARs and claim source changes.
- Preserve baseline/. It is the pre-recovery binary reference, not a build dependency.
- Use scripts/build.ps1 on Windows; the included runtime contains jdk.compiler and jdk.jartool modules. If unavailable, use a real JDK 17. Do not silently skip compilation.
- Rebuild and run the existing six test programs for meaningful code changes. Tests use temporary databases. Never run them against the user's live database.
- Do not erase, restore over, or migrate %USERPROFILE%/EduTestSuite2 without a verified local backup and an explicit migration plan.
- Preserve user accounts, assignment versions, existing attempts, grades, encrypted settings, RU/KZ UI, exports and backups.
- Never kill arbitrary Java processes or unrelated port owners. Handle port conflicts in this app.
- Do not add API keys, database passwords, service_role keys or personal data to source or distributable builds. Do not publish this private recovered project without explicit user instruction.
- No cloud billing, project creation, destructive SQL or production migration without the applicable authorization. Read current Supabase docs before implementation; inspect an existing schema before changing it.
- Prefer focused fixes over full rewrites. Refactor recovered variable names and types incrementally with regression checks.
- After each stage, report exact changes, checks actually run, limitations and next steps. Do not claim Windows/GUI/real-provider tests passed when only Linux or mocked tests ran.
