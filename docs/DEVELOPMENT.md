# Development and continuous integration

## Fresh checkout

Install Git and **JDK 25**, set `JAVA_HOME` to the JDK directory (or add its `bin` directory to PATH), then clone the repository. No system Gradle, Python, optional mod, absolute tool path or pre-existing cache is needed for the normal build/server tests. Initial dependency downloads need internet access to Gradle, Fabric, Maven Central, Mojang and the optional Mod Menu compile API repository.

```sh
git clone https://github.com/matthew-c-lee/blockwheel.git
cd blockwheel
java -version
./gradlew --version
./gradlew build runGameTest releaseArtifacts
```

In PowerShell use `.\gradlew.bat`. The checked-in wrapper supplies Gradle 9.5.1 and verifies its distribution SHA-256. The wrapper JAR is tracked; `gradlew` has its executable bit and LF endings preserved by Git. The build selects a Java 25 toolchain and compiles with release 25. Version/dependency values live in `gradle.properties`; filenames include the Minecraft version while Fabric metadata retains the public mod version.

`build` compiles the production/client code, builds the production/sources archives and runs JUnit plus Fabric server GameTests: the existing Fabric test configuration wires `runGameTest` into `check`. `runGameTest` also runs that server suite on its own; listing it alongside `build` does not run it twice in one invocation. `runClientGameTest` is a separate display-dependent suite. Test code and resources are a dedicated source set, excluded from the mod archives.

`releaseArtifacts` builds and stages these files in `build/release/` using Gradle's actual production archive outputs:

```text
blockwheel-1.0.2+mc26.2.jar
blockwheel-1.0.2+mc26.2-sources.jar
SHA256SUMS.txt
```

The version portion is derived from project properties. Install the first JAR, along with Fabric API, in Minecraft's `mods` folder. The sources JAR is for development. Verify the extracted bundle with `sha256sum -c SHA256SUMS.txt` on Linux or compare `Get-FileHash -Algorithm SHA256 *.jar` in PowerShell. The historical `release/` directory is not used by CI. Generated caches, builds, runtimes and logs are ignored.

## GitHub Actions

The existing `.github/workflows/build.yml` runs on pushes, pull requests and manual dispatch. Its required `verify` job uses Ubuntu 24.04, Temurin 25 and the checked-in wrapper to execute:

```sh
./gradlew --no-daemon --console=plain build runGameTest releaseArtifacts
```

Read-only repository permissions, a 30-minute timeout and cancellation of superseded runs are configured. Gradle's action validates wrapper JARs and manages its user-home cache; PR/non-default-branch runs only read the cache. There is no second overlapping Java/Gradle cache.

Action majors were checked against their maintainers' documentation on November 24, 2026: [checkout v7](https://github.com/actions/checkout), [setup-java v6](https://github.com/actions/setup-java), [setup-gradle v6](https://github.com/gradle/actions) and [upload-artifact v7](https://github.com/actions/upload-artifact). The Gradle action uses its documented basic cache provider.

Successful verification uploads **blockwheel-1.0.2+mc26.2**, with the name derived from `gradle.properties`. In the repository, open **Actions → Build and test → successful run → Artifacts**, download/extract that bundle, then install the JAR without `-sources`. Only the two production archives and their checksums are uploaded there. Test mods, third-party mods, Minecraft assets and runtime caches cannot enter this bundle. Reports and logs are uploaded separately as **blockwheel-verification-<attempt>**, even if verification fails; installable bundles are uploaded only after success. Artifact retention is 30 days for binaries and 14 days for diagnostics, subject to repository policy.

The repository already has `origin` configured as `https://github.com/matthew-c-lee/blockwheel.git`. After reviewing and committing the local changes, push your branch normally and open a PR if appropriate. No publishing tokens or other secrets are needed. If Actions is disabled, enable it under **Settings → Actions → General** and allow the official GitHub/Gradle actions. Manual dispatch becomes available after the workflow is present on the default branch. This workflow does not publish to GitHub Releases, Modrinth or CurseForge.

## Client and render tests

Client tests are deliberately opt-in because they need a graphics stack and run substantially more slowly. In **Actions → Build and test → Run workflow**, enable **client_tests** and choose `baseline`, `modmenu`, `tweakeroo` or `litematica`. The separate 60-minute job installs Xvfb/Mesa, forces software OpenGL, and runs the existing client suite at **1600×1000**. The selected optional profile only adds test-runtime dependencies. Baseline remains free of the optional compatibility mods.

The job checks all captured vanilla/restored and standard/HD resource-pack reference frames at GUI scales 2/3/4 using the existing Python scripts and Pillow. Missing frames and failed assertions fail the job. Client screenshots, reports, crash reports and logs are retained even on failure. There is no `continue-on-error`. This Linux headless configuration still requires an observed remote run to verify runner-specific graphics behavior.

For local client testing, use an OpenGL-capable display:

```sh
./gradlew runClientGameTest
./gradlew runClientGameTest -PtestModMenu=true
./gradlew runClientGameTest -PtestTweakeroo=true
./gradlew runClientGameTest -PtestLitematica=true
```

For headless Linux, install Xvfb, xauth and Mesa EGL/GL drivers, then use:

```sh
LIBGL_ALWAYS_SOFTWARE=true xvfb-run -a -s '-screen 0 1600x1000x24' ./gradlew runClientGameTest
```

Screenshots are under `build/run/clientGameTest/screenshots/`. Install Python 3 and Pillow only to run the optional image checks; match `--scale` to the capture and use `--source-scale 2` for HD packs. See the commands in [README](../README.md#build-and-test). A fresh client test run recreates its test runtime; preserve desired diagnostics before starting another run.

## Validation in this workspace

Unlike [VERIFICATION.md](VERIFICATION.md), which records the earlier release environment, this section describes this checkout's checks on November 24–25, 2026.

The checks below ran before the version-only bump to **1.0.1**, while the mod metadata still read `1.0.0`. Tests were not rerun for that version bump, as requested.

- `tasks --all`: confirmed `build`, `runGameTest`, `runClientGameTest` and `releaseArtifacts` with wrapper 9.5.1/Loom 1.17.21. `build --dry-run` confirmed the existing server-GameTest dependency; `releaseArtifacts` therefore also runs server verification through `build`.
- `build runGameTest releaseArtifacts`: passed with Temurin JDK 25; **90 JUnit tests**, zero failures/errors, and **225 required server GameTests** passed. Six new server cases cover zero-batch retrieval, reserves/metadata, source priority, settings/server gates, exact-only eligibility, full storage and stale snapshots. Existing successful conversions remain covered.
- Wrapper JAR and distribution SHA-256 values matched Gradle's official checksum endpoints. The Unix wrapper's shell syntax and `./gradlew --version` passed under Git Bash. Its working file has no CR bytes, and the executable bit is recorded in Git.
- `actionlint` 1.7.12: the workflow passed static validation. Remote runner execution is a separate check; no GitHub Actions run is claimed here.

- An isolated source copy with a separate, initially empty Gradle user home downloaded its own Gradle/dependencies; `clean build runGameTest releaseArtifacts` passed with the same **90 JUnit / 225 server tests**. A PowerShell harness initially treated harmless compiler stderr as an exception; rerunning with normal native-process stderr handling completed successfully. No previous environment's cache or script was needed by the project.
- `runClientGameTest`: the full baseline suite passed on Windows/OpenGL at 1600×1000, including the new native zero-preview R-release retrieval. Optional Mod Menu/Tweakeroo profiles and Linux Xvfb/Mesa were not executed locally.
- The workflow's frame-verification shell block was executed under Git Bash against the actual client captures (substituting only the local Python executable): **all 12 vanilla/restored/standard/HD frames passed**, covering GUI scales 2/3/4. Fabric prefixes screenshot filenames with sequence numbers; CI's lookup accepts those prefixes and requires exactly one match. The workflow's metadata block also produced `blockwheel-1.0.0+mc26.2` as expected.
- Both staged JAR checksums verified. Archive inspection found **159 runtime entries / 101 sources entries**, no test mods/fixtures, nested third-party dependencies or Minecraft assets. Runtime `fabric.mod.json` retains `version: 1.0.0` and the original supported dependency minimums.
- No remote Actions run was launched or observed. Commit/push remains the handoff; the first remote run must confirm Ubuntu and action-runtime behavior.

The offhand-restock follow-up compiled production, client and GameTest sources and rebuilt the 1.0.1 release bundle with `compileGametestJava releaseArtifacts -x test -x runGameTest`. Six server regression cases and native client placement/use/eating scenarios were added and compiled. No unit, server or client tests were executed for this follow-up, as requested; the earlier runtime results above do not validate these new paths.

On November 25, 2026, the 1.0.2 update initially compiled production/client, JUnit and GameTest sources, staged the release archives, and resolved the optional Litematica runtime profile without executing tests. Litematica 0.28.8 and MaLiLib 0.29.5 release checksums and relevant bytecode signatures were inspected. After runtime testing was authorized, `build runGameTest` passed with **93 JUnit tests**, zero failures/errors, and **243 required server GameTests**. These include offhand restocking, schematic acquisition, bulk offhand exact/family/log deposits and gate recipe accounting.

`runClientGameTest -PtestLitematica=true -PtestTweakeroo=true` then passed the full client suite on Windows/OpenGL at 1600×1000 with Litematica 0.28.8, MaLiLib 0.29.5 and Tweakeroo 0.29.5 loaded together. Native scenarios exercised offhand placement/use/completed eating, bulk dump holds, fence/gate scrolling and release, and both default and rewritten Easy Place with actual acquisition, placement and restocking. Initial failures identified test-fixture issues: an old assertion expected scrolling over a fence to leave it unchanged, and Litematica reloads configuration on world join. The Easy Place fixture now reapplies its options after joining and uses the upstream default direct-click policy. Focused Easy Place runs and the subsequent full run passed; no production changes were needed during this verification.

The workflow's frame checks passed for **all 12 vanilla/restored/standard/HD captures** at GUI scales 2/3/4. `actionlint` and `git diff --check` passed, and both staged 1.0.2 JAR checksums matched. These are local results; no remote Actions or Linux headless run is claimed.
