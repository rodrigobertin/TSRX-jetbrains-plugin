package dev.tsrx.intellij_plugin

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement

/**
 * Enables \"Go to Declaration or Usages\" (Cmd+B) on TSRX definitions.
 *
 * For TextMate-only language (no PSI), IntelliJ's default handler for
 * LSP (textDocument/definition) only triggers when caret is on a *usage*.
 * When caret is on the *definition* itself (e.g. `export function MyButton`),
 * textDocument/definition returns the same location or empty, so Cmd+B does nothing.
 *
 * This handler makes Cmd+B on a definition fall back to Find Usages
 * (textDocument/references) via the existing TsrxFindUsagesProvider + LSP.
 *
 * Logic: if the element is inside a TSRX file and looks like a declaration
 * (previous text contains `function`, `const`, `let`, `class`), return the element
 * itself as a target. Then GotoDeclarationOrUsagesHandler2 detects that the target
 * is the source itself and shows the usages popup (which will be populated by LSP).
 * Otherwise return null to let LSP's definition handler run normally.
 */
class TsrxGotoDeclarationHandler : GotoDeclarationHandler {
	companion object {
		private val LOG = Logger.getInstance(TsrxGotoDeclarationHandler::class.java)
	}

	override fun getGotoDeclarationTargets(
		sourceElement: PsiElement?,
		offset: Int,
		editor: Editor?,
	): Array<PsiElement>? {
		LOG.warn("TsrxGotoDeclarationHandler CALLED: source=${sourceElement?.text?.take(30)} offset=$offset file=${sourceElement?.containingFile?.name} lang=${sourceElement?.containingFile?.language} fileType=${sourceElement?.containingFile?.fileType?.name} elementType=${sourceElement?.node?.elementType}")
		if (sourceElement == null) {
			LOG.warn("TsrxGotoDeclarationHandler: sourceElement null -> return null")
			return null
		}
		val file = sourceElement.containingFile
		if (file == null) {
			LOG.warn("TsrxGotoDeclarationHandler: containingFile null -> return null")
			return null
		}
		LOG.warn("TsrxGotoDeclarationHandler: file language=${file.language} fileType=${file.fileType.name} isTsrxLang=${file.language.`is`(TsrxLanguage)} isTsrxFileType=${file.fileType === TsrxFileType.INSTANCE}")
		// Only for TSRX – allow if either language is TSRX OR fileType is TSRX (TextMate files may have generic language)
		if (!file.language.`is`(TsrxLanguage) && file.fileType !== TsrxFileType.INSTANCE) {
			LOG.warn("TsrxGotoDeclarationHandler: not TSRX file -> return null (let LSP handle)")
			return null
		}

		val text = sourceElement.text
		if (text == null || text.isBlank()) {
			LOG.warn("TsrxGotoDeclarationHandler: blank text -> return null")
			return null
		}

		// Heuristic: is caret on a declaration? Check preceding text in file up to offset
		// contains declaration keywords near the word.
		try {
			val docText = file.text ?: return null
			val offsetInFile = sourceElement.textRange?.startOffset ?: offset
			val windowStart = maxOf(0, offsetInFile - 200)
			val prefix = docText.substring(windowStart, offsetInFile)
			val trimmedPrefix = prefix.trimEnd()
			val isDeclaration = prefix.contains(Regex("\\b(function|const|let|var|class|interface|type|export)\\b[^;{]*$"))
				|| trimmedPrefix.endsWith("export") || trimmedPrefix.endsWith("function")
				|| trimmedPrefix.matches(Regex(".*\\bexport\\s+function\\s*"))
				|| docText.substring(0, offsetInFile).contains(Regex("export\\s+function\\s+${Regex.escape(text)}\\b"))

			LOG.warn("TsrxGotoDeclarationHandler: text='${text}' prefix='${prefix.takeLast(50)}' isDeclaration=$isDeclaration offset=$offset offsetInFile=$offsetInFile file=${file.name}")

			// If isDeclaration, return the sourceElement itself to trigger \"show usages\" fallback.
			// GotoDeclarationOrUsagesHandler2 will see target == source and show usages popup
			// which will be populated by LSP textDocument/references.
			if (isDeclaration) {
				LOG.warn("TsrxGotoDeclarationHandler: returning sourceElement for usages popup: ${file.name}:${offsetInFile}")
				return arrayOf(sourceElement)
			}
		} catch (e: Exception) {
			LOG.warn("TsrxGotoDeclarationHandler: exception", e)
		}

		// Not a declaration -> let LSP's GotoDefinition handle it
		return null
	}

	override fun getActionText(context: com.intellij.openapi.actionSystem.DataContext): String? {
		// Provide hint text for the action when on declaration
		return null
	}
}
