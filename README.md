# TSRX for JetBrains IDEs

TSRX language support for **IntelliJ IDEA, WebStorm, PyCharm and PhpStorm (2025.2+)**. Standalone repository — extracted from the [tsrx-org/tsrx monorepo](https://github.com/tsrx-org/tsrx) — containing only the JetBrains plugin.

- **TextMate highlighting** for `.tsrx` (TSX-like, `source.tsrx`)
- **LSP** via `@tsrx/language-server` — diagnostics, completion, hover, Go to Definition, Find Usages, document symbols
- **Status bar icon** + **New File → TSRX File** template

## Requirements

- IntelliJ-based IDE 2025.2+ (branch `252`)
- Node.js 22+ with npm on PATH (for LSP)

## Language Server Resolution

1. `node_modules/.bin/tsrx-language-server` (walk up from file)
2. Global `tsrx-language-server` on PATH
3. Auto-installs `@tsrx/language-server@<lsp-version>` into IDE system dir and restarts LSP

Pinned version: `gradle.properties` `tsrxLspVersion` and `src/main/resources/lsp-version.txt` (both `0.3.128`).

## Development

```bash
# Regenerate TextMate bundle from grammars/textmate (committed here, but regeneratable)
node scripts/regenerate-textmate.js
# or via Gradle
./gradlew regenerateTextMate

./gradlew buildPlugin   # -> build/distributions/*.zip
./gradlew runIde        # sandbox IDE
./gradlew verifyPlugin  # cross-IDE verification
```

TextMate sources are `grammars/textmate/tsrx.tmLanguage.json` + `info.plist` (vendored from monorepo). The bundle `src/main/resources/textmate/` is **committed** in this repo (unlike the monorepo where it is gitignored).

## Publishing

```bash
./gradlew publishPlugin -PintellijPlatform.publishing.token=$JETBRAINS_MARKETPLACE_TOKEN
```

Version is `build.gradle.kts` `version = \"0.0.82\"` + `gradle.properties` `tsrxLspVersion`. ChangeNotes/description live in `build.gradle.kts` `intellijPlatform { pluginConfiguration { ... } }`.

## Sync with Monorepo

When `tsrx.tmLanguage.json` or `@tsrx/language-server` changes upstream, copy the files or run:

```bash
cp ../tsrx/grammars/textmate/tsrx.tmLanguage.json grammars/textmate/
cp ../tsrx/grammars/textmate/info.plist grammars/textmate/
node scripts/regenerate-textmate.js
# bump gradle.properties tsrxLspVersion + src/main/resources/lsp-version.txt if needed
```

Original monorepo: https://github.com/tsrx-org/tsrx
