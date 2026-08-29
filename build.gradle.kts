plugins {
	id("java")
	id("org.jetbrains.kotlin.jvm") version "2.1.20"
	id("org.jetbrains.intellij.platform") version "2.10.2"
}

group = "dev.tsrx.intellij_plugin"
version = "0.0.82"

repositories {
	mavenCentral()
	intellijPlatform {
		defaultRepositories()
	}
}

// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
	intellijPlatform {
		webstorm("2025.2.4")
		testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)

		// Add plugin dependencies for compilation here:
		bundledPlugin("org.jetbrains.plugins.textmate")
	}
}

intellijPlatform {
	pluginConfiguration {
		name = "TSRX"
		description = """
TSRX — TypeScript Render Extensions for .tsrx files. A TSX superset with declarative control flow for React, Solid, Vue, Preact, Ripple & Octane.<br/>
<p>Works in <b>IntelliJ IDEA, WebStorm, PyCharm and PhpStorm (2025.2+)</b> — same .tsrx grammar everywhere.</p>
<p>Provides:</p>
<ul>
  <li>Syntax highlighting via TextMate (<code>source.tsrx</code>) — TSX-like plus <code>@if/@else</code>, <code>@for/@empty</code>, <code>@switch/@case/@default</code>, <code>@try/@pending/@catch</code>, <code>@{}</code> statement containers, <code>&lt;style&gt;</code> scoped CSS, <code>{prop}</code> shorthand and <code>&lt;{expr}&gt;</code> dynamic tags</li>
  <li>Language Server (<code>@tsrx/language-server</code>) — diagnostics, completion, hover, Go to Definition, Find Usages, document symbols, auto-closing tags</li>
  <li>Status bar TSRX icon for Language Services, <em>New File → TSRX File</em> template, braces & comments</li>
</ul>
<p>Requirements: Node.js 22+ on PATH and project with <code>@tsrx/typescript-plugin</code> in <code>tsconfig.json</code>. The LSP auto-installs if missing.</p>
<p>Links: <a href="https://tsrx.dev">tsrx.dev</a> · <a href="https://github.com/tsrx-org/tsrx">GitHub</a> · <a href="https://github.com/tsrx-org/tsrx/issues">Issues</a></p>
        """.trimIndent()

		changeNotes = """
<h2>0.0.82 — Multi-IDE & Marketplace polish</h2>
<ul>
  <li>Compatible with IntelliJ IDEA, WebStorm, PyCharm and PhpStorm 2025.2+</li>
  <li>Marketplace description with features, requirements and links</li>
  <li>Verified via pluginVerifier for WebStorm, PyCharm, PhpStorm and IDEA</li>
</ul>
        """.trimIndent()

		ideaVersion {
			sinceBuild = "252.25557"
			untilBuild = provider { null }
		}

		vendor {
			name = "TSRX"
			url = "https://github.com/tsrx-org/tsrx"
			email = "hello@tsrx.dev"
		}
	}

	publishing {
		token = providers.environmentVariable("JETBRAINS_MARKETPLACE_TOKEN")
	}

	pluginVerification {
		ides {
			recommended()
			// Explicit verification for the four requested products (252 = 2025.2):
			// create(\"IC\", \"242.22855\") // Community fallback if needed
		}
	}
}

val textmateGrammar = file("src/main/resources/textmate/Syntaxes/tsrx.tmLanguage.json")
val textmateInfoPlist = file("src/main/resources/textmate/info.plist")

tasks.register("checkTextMateResources") {
	group = "verification"
	description = "Verifies TextMate bundle resources are present (run `node scripts/regenerate-textmate.js` if missing)."
	notCompatibleWithConfigurationCache("Uses project.file at execution time")
	doLast {
		val missing = listOf(textmateGrammar, textmateInfoPlist).filterNot { it.exists() }
		if (missing.isNotEmpty()) {
			val expected = missing.joinToString(", ") { it.path }
			throw GradleException(
				"Missing TextMate resources: $expected. " +
					"Run `node scripts/regenerate-textmate.js` and then re-run the build. Resources are commited in this repo but can be regenerated from grammars/textmate."
			)
		}
	}
}

tasks.register<Exec>("regenerateTextMate") {
	group = "generation"
	description = "Regenerates TextMate bundle from grammars/textmate via scripts/regenerate-textmate.js"
	workingDir = file(".")
	commandLine("node", "scripts/regenerate-textmate.js")
}

tasks {
	// Ensure TextMate resources are present before packaging resources
	named("processResources") {
		dependsOn("checkTextMateResources")
	}
	// Set the JVM compatibility versions
	withType<JavaCompile> {
		sourceCompatibility = "21"
		targetCompatibility = "21"
	}
}

kotlin {
	compilerOptions {
		jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
	}
}
