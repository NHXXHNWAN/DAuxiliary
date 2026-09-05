# DAuxiliary Development Notes

This file is the persistent project context for future development sessions.

## Project identity

- Project name: DAuxiliary.
- Module type: LSPosed/Xposed module for Douyin Android.
- Target package: `com.ss.android.ugc.aweme`.
- Module package: `com.dauxiliary`.
- The module is not a standalone Douyin client and must not be designed as one.

## UI requirements

- The module configuration Activity uses Miuix Compose UI consistently.
- The configuration page uses the official Miuix `FloatingNavigationBar` and `FloatingNavigationBarItem` APIs.
- Do not replace the Miuix floating bar with Material navigation components or a hand-made `NavigationBar` plus padding/clip workaround.
- Keep the in-Douyin entry lightweight. It is a control surface injected into the target process, not the full module Activity.
- Do not call the library MIUI or MIUIX in new documentation; the project dependency is Miuix.

## LSPosed architecture

- `EntryHook` is the Xposed entry and must remain small.
- Install the in-Douyin entry only for the target package.
- Actual features belong in separate feature/hook classes and must be dispatched independently from the entry UI.
- Configuration is stored by the module and read from the hooked process through `ConfigStore`.
- Every feature needs an explicit switch, a safe disabled path, and idempotent installation.
- A module exception must not crash Douyin. Wrap risky reflection, hook setup, and UI injection with clear exception boundaries and log useful diagnostics.
- Account for multiple Douyin processes, Activity recreation, obfuscation, and version differences.

## Miuix dependency policy

- Keep dependency versions in `gradle/libs.versions.toml`.
- Before upgrading Miuix, verify the official component name, API signature, Android artifact, required `compileSdk`, and Kotlin/Compose compatibility.
- Do not commit downloaded source jars, local SDK files, Gradle caches, APKs, or reverse-engineering artifacts.

## Development sequence

1. Inspect the affected source and current dependency API before editing.
2. Make the smallest isolated change.
3. Build with `./gradlew :app:assembleDebug --console=plain`.
4. Read the actual final marker and failure output; do not infer success from a timeout or an old APK.
5. For Hook changes, verify package filtering, duplicate-install protection, failure isolation, and configuration behavior.
6. Update `README.md` and `CONTRIBUTING.md` when architecture or workflow changes.

## Current baseline

The current baseline includes the module manifest, `assets/xposed_init`, a target-package Xposed entry, an in-Douyin entry panel skeleton, `ConfigStore`, Miuix configuration pages, and a Debug build configuration. Concrete Douyin feature hooks and broad device/version validation are still pending.
