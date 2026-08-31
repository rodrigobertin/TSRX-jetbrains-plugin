# AGENTS.md — TSRX JetBrains Plugin

IntelliJ-platform plugin providing TSRX language support (TextMate highlighting + Node.js LSP) for IntelliJ IDEA, WebStorm, PyCharm, PhpStorm (2025.2+).

## Build & Dev Commands

```bash
node scripts/regenerate-textmate.js   # regenerate TextMate bundle from grammars/textmate/ (or: ./gradlew regenerateTextMate)
./gradlew buildPlugin                 # build -> build/distributions/*.zip
./gradlew runIde                      # launch sandbox IDE with plugin
./gradlew verifyPlugin                # cross-IDE compatibility verification
./gradlew publishPlugin -PintellijPlatform.publishing.token=$JETBRAINS_MARKETPLACE_TOKEN
```

**Required order when changing grammar:** regenerate TextMate → `buildPlugin`. The `checkTextMateResources` task (dependency of `processResources`) fails the build if TextMate files are missing.

## Toolchain

- **Java 21** (Temurin), **Gradle 9.0.0** (wrapper), **Kotlin 2.1.20**, **Node.js 22+** on PATH
- Gradle configuration cache is enabled (`gradle.properties`)
- IntelliJ Platform Gradle Plugin 2.10.2; build dependency: WebStorm 2025.2.4 (`sinceBuild = 252.25557`, `untilBuild = null`)

## Version Pins (must stay in sync)

| What | File | Current |
|------|------|---------|
| LSP version | `gradle.properties` → `tsrxLspVersion` | `0.3.128` |
| LSP version | `src/main/resources/lsp-version.txt` | `0.3.128` |
| Plugin version | `build.gradle.kts` → `version` (fallback) | `1.0.7` |
| Plugin version | `package.json` → `version` | `1.0.7` |

Plugin version at build time: `GITHUB_REF_NAME` env var → `pluginVersion` Gradle property → fallback `1.0.7` (with `v` prefix stripped). Publishing is triggered by pushing `v*` tags.

## TextMate Grammar

- **Sources:** `grammars/textmate/tsrx.tmLanguage.json` + `info.plist` (vendored from monorepo)
- **Generated bundle:** `src/main/resources/textmate/` — **committed to this repo** (unlike the upstream monorepo where it's gitignored)
- CI (`ci.yml` grammar job) regenerates and fails if `git diff --exit-code` shows changes — meaning the committed bundle must always match the grammar sources

## Architecture

All Kotlin source lives in a single flat package: `dev.tsrx.intellij_plugin` under `src/main/kotlin/`.

| File | Role |
|------|------|
| `TsrxLanguage` / `TsrxFileType` | Language + file type registration (`.tsrx` ext) |
| `TsrxTextMateBundleProvider` | Extracts TextMate bundle from plugin resources to IDE system dir at runtime |
| `TsrxLanguageServer` | Resolves/installs `@tsrx/language-server` (local `node_modules/.bin` → global PATH → auto-install via npm) |
| `TsrxLspServerSupportProvider` / `TsrxLspServerDescriptor` | LSP integration (optional, gated by `com.intellij.modules.lsp`) |
| `TsrxCommenter` / `TsrxBraceMatcher` / `TsrxFindUsagesProvider` / `TsrxGotoDeclarationHandler` | IDE editor features |
| `TsrxEmmetGenerator` | Emmet in `.tsrx` — `xml.zenCodingGenerator` EP; extends `XmlZenCodingGeneratorImpl`, matches `TsrxLanguage`/`*.tsrx` (built-in generators only match `XMLLanguage`/JSX dialects) |
| `TsrxXmlExtension` | HTML tag handling (auto-close, sync editing) in `.tsrx` — `xml.extension` EP |
| `TsrxFoldingBuilder` | Code folding in `.tsrx` — `lang.foldingBuilder` EP; single-pass text scanner (tags, braces, imports), no PSI required |
| `TsrxFormattingService` | Reformat Code in `.tsrx` — `formattingService` EP (`AsyncDocumentFormattingService`); same scanner as folding, indents tags/braces like TSX, offline |
| `NewTsrxFileAction` | New File → TSRX File action |
| `TsrxIcons` | Icon references |

`plugin.xml` registers extensions; `tsrx-lsp.xml` is an optional config-file loaded only when LSP support is present.

## Emmet in .tsrx (since v1.0.5)

Emmet (`div>ul>li*3` + `Tab`) is provided by `TsrxEmmetGenerator`, registered as `<xml.zenCodingGenerator>` (`com.intellij` namespace, EP declared in `app.jar`). Key points:

- **Why a custom generator:** built-in `XmlZenCodingGeneratorImpl.isMyLanguage` requires `XMLLanguage` and `JSXZenCodingGenerator` requires `DialectDetector.isJSX` — neither matches `TsrxLanguage : Language("TSRX")`. Without a registered generator, `ZenCodingTemplate.findApplicableDefaultGenerator()` finds no candidate for `.tsrx`.
- **Context check is relaxed on purpose:** TSRX is TextMate-only (flat PSI, no `XmlTag`/`XmlText`), so `HtmlTextContextType.isInContext()` always returns false. `TsrxEmmetGenerator.isMyContext` falls back to "is this a `.tsrx` file" (language check → `VirtualFile.extension` → filename).
- **Template generation is reused:** `generateTemplate`/`createTemplateByKey` from `XmlZenCodingGeneratorImpl` produce the HTML; `getSuffix() = "html"` enables html/BEM filters. Gated by `EmmetOptions.isEmmetEnabled` (Settings → Editor → Emmet).
- **Caveat:** Emmet currently expands anywhere in a `.tsrx` file (including inside `@if(...)` headers or JS blocks). A markup-context gate can be added to `isMyContext` if needed.

## Code Folding in .tsrx (since v1.0.6)

Folding (`<div class="test">...</div>` collapse, `@if {...}`, import groups) is provided by `TsrxFoldingBuilder`, registered as `<lang.foldingBuilder language="TSRX">`. Key points:

- **Why a custom builder:** TSRX is TextMate-only — the PSI is a flat token list with no `XmlTag`/block AST, so there is nothing for a PSI-walking folding builder to traverse. The `@tsrx/language-server` LSP does not expose `textDocument/foldingRange` (verified against 0.3.128), so LSP folding is not an option either.
- **Single-pass text scanner:** `buildLanguageFoldRegions` scans `document.charsSequence` once with a state machine, skipping strings (`'` `"`), template literals (`` ` `` with `${}` interpolation returning to code mode), line/block comments and regex literals (heuristic: `/` preceded by an operator char). Emits `FoldingDescriptor`s for: balanced `<tag ...> ... </tag>` pairs (fold body only → placeholder shows `<div class="test">...</div>`), balanced `{ ... }` blocks (→ `@if (cond) {...}`), and contiguous `import` runs (≥2 lines → `N imports`). Only multiline regions fold.
- **`<` disambiguation:** `<` is treated as a tag open only when the previous char is not an identifier/`]`/`)` char — this keeps TS generics (`Array<string>`) and comparisons (`a < b`) out of the tag stack. Self-closing (`<br/>`) and void elements (`img`, `input`, ...) are not pushed.
- **Dumb-aware:** works without indices or the LSP; folding is computed purely from the document text.
- **Caveat:** heuristic scanner, not a parser — exotic cases (regex after `return`, tags inside attribute expressions like `attr={<span/>}`) are skipped rather than folded. `// region` markers are left to `CustomFoldingBuilder`'s built-in custom-region support.

## Reformat Code in .tsrx (since v1.0.7)

Reformatting (`Code → Reformat Code`) is provided by `TsrxFormattingService`, registered as `formattingService` (`AsyncDocumentFormattingService`). Key points:

- **Same scanner as folding:** reuses the single-pass state machine (tags, braces, imports, strings, comments, template literals) but computes indent instead of fold ranges — `tagDepth + braceDepth` determines indent (2 spaces).
- **Offline & LSP-free:** no Node, no `textDocument/formatting`; the LSP (`@tsrx/language-server` 0.3.128) explicitly strips `documentFormattingProvider` (formatting is owned by Prettier in VS Code) so IDE formatting must be local.
- **Triggers:** `Code → Reformat Code`, `Reformat on Save`, and range formatting all route through `AsyncDocumentFormattingService` → `onTextReady` diff.
- **Caveat:** heuristic like folding — `Array<string>`/regex edge cases share the same limitations.

## Gotchas

- **`package.json` is vestigial** — just a name/version marker. No dependencies or scripts. Do NOT run `npm install` / `pnpm install` at repo root.
- **No test suite exists** — `src/test/` is absent. The IntelliJ platform `testFrameworkType(Platform)` dependency is declared but no tests are written.
- **LSP auto-install** writes to `<IDE-system-dir>/tsrx-language-server/` via `npm install`. Requires Node 22+ + npm on PATH at runtime.

<!-- gitnexus:start -->
# GitNexus — Code Intelligence

This project is indexed by GitNexus as **TSRX-jetbrains-plugin** (268 symbols, 378 relationships, 14 execution flows).

> Index stale? Run `node .gitnexus/run.cjs analyze --index-only` from the project root — it auto-selects an available runner. No `.gitnexus/run.cjs` yet? Bootstrap with `npx`, `bunx`, or `pnpm dlx` — e.g. `bunx gitnexus@latest analyze` (npm 11 npx crash; #1939).

## Always Do

- **MUST run impact analysis before editing.** Use `impact({target: "symbolName", direction: "upstream"})` (MCP) or `node .gitnexus/run.cjs impact "symbolName" --direction upstream --repo .` (CLI fallback); report callers, processes, and risk. Never substitute grep for graph analysis.
- **MUST analyze graph changes before committing.** Use `detect_changes({scope: "all"})` (MCP) or `node .gitnexus/run.cjs detect-changes --scope all --repo .` (CLI fallback). `partial: true` or `truncated: true` is not a clean check — a zero means unseen, not unaffected; re-run it. For regression review: `detect_changes({scope: "compare", base_ref: "main"})` or `node .gitnexus/run.cjs detect-changes --scope compare --base-ref "main" --repo .`.
- **MUST warn the user** if impact analysis returns HIGH or CRITICAL risk before proceeding with edits.
- **MUST treat `risk: UNKNOWN` as unresolved, not as low.** An empty caller set is not evidence the symbol is unused — it can also mean the callers are not resolvable by the index (plain-object property access, dynamic dispatch, cross-language calls). `impact` pairs `UNKNOWN` with a `riskNote` saying so. Confirm with a text search before treating the symbol as safe to change or delete; do not proceed on the strength of a zero.
- When exploring unfamiliar code, use `query({search_query: "concept"})` to find execution flows instead of grepping. It returns process-grouped results ranked by relevance.
- When you need full context on a specific symbol — callers, callees, which execution flows it participates in — use `context({name: "symbolName"})`.
- For security review, `explain({target: "fileOrSymbol"})` lists taint findings (source→sink flows; needs `analyze --pdg`).

## Never Do

- NEVER edit a function, class, or method before MCP/CLI impact analysis.
- NEVER ignore HIGH or CRITICAL risk warnings from impact analysis, and never read `UNKNOWN` as an all-clear — it means the walk could not answer, which is the one verdict that requires confirming by other means.
- NEVER rename symbols with find-and-replace — use `rename` which understands the call graph.
- NEVER commit before MCP/CLI graph change analysis.

## Resources

| Resource | Use for |
| --- | --- |
| `gitnexus://repo/TSRX-jetbrains-plugin/context` | Codebase overview, check index freshness |
| `gitnexus://repo/TSRX-jetbrains-plugin/clusters` | All functional areas |
| `gitnexus://repo/TSRX-jetbrains-plugin/processes` | All execution flows |
| `gitnexus://repo/TSRX-jetbrains-plugin/process/{name}` | Step-by-step execution trace |

## CLI

| Task | Read this skill file |
| --- | --- |
| Understand architecture / "How does X work?" | `.claude/skills/gitnexus-exploring/SKILL.md` |
| Blast radius / "What breaks if I change X?" | `.claude/skills/gitnexus-impact-analysis/SKILL.md` |
| Trace bugs / "Why is X failing?" | `.claude/skills/gitnexus-debugging/SKILL.md` |
| Rename / extract / split / refactor | `.claude/skills/gitnexus-refactoring/SKILL.md` |
| Tools, resources, schema reference | `.claude/skills/gitnexus-guide/SKILL.md` |
| Index, status, clean, wiki CLI commands | `.claude/skills/gitnexus-cli/SKILL.md` |

<!-- gitnexus:end -->
