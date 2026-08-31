package dev.tsrx.intellij_plugin

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.rename.RenameHandler

class TsrxRenameHandler : RenameHandler {
    override fun isAvailableOnDataContext(dataContext: DataContext): Boolean {
        val editor = CommonDataKeys.EDITOR.getData(dataContext) ?: return false
        val file = CommonDataKeys.PSI_FILE.getData(dataContext) ?: return false
        if (!isTsrxFile(file)) return false
        val word = getWordAtCaret(editor, file) ?: return false
        LOG.warn("TSRX rename handler isAvailable: word=$word file=${file.name}")
        return true
    }

    override fun isRenaming(dataContext: DataContext): Boolean = isAvailableOnDataContext(dataContext)

    override fun invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext) {
        if (!isTsrxFile(file)) return
        val word = getWordAtCaret(editor, file)
        if (word == null) {
            Messages.showErrorDialog(project, "Caret should be positioned at symbol to be renamed", "Rename")
            return
        }
        val newName = Messages.showInputDialog(project, "New name for '$word':", "Rename", Messages.getQuestionIcon(), word, null)
        if (newName == null || newName.isEmpty() || newName == word) return
        if (!IDENTIFIER_REGEX.matches(newName)) {
            Messages.showErrorDialog(project, "Invalid identifier: $newName", "Rename")
            return
        }
        ApplicationManager.getApplication().invokeLater {
            WriteCommandAction.runWriteCommandAction(project) {
                val document = editor.document
                val text = document.text
                // Find all word occurrences with word boundaries
                val pattern = Regex("\\b${Regex.escape(word)}\\b")
                val newText = pattern.replace(text, newName)
                if (newText != text) {
                    document.setText(newText)
                    PsiDocumentManager.getInstance(project).commitDocument(document)
                    LOG.warn("TSRX rename: '$word' -> '$newName' in ${file.name}")
                }
            }
        }
    }

    override fun invoke(project: Project, elements: Array<PsiElement>, dataContext: DataContext) {
        val editor = CommonDataKeys.EDITOR.getData(dataContext)
        val file = CommonDataKeys.PSI_FILE.getData(dataContext)
        if (editor != null && file != null) {
            invoke(project, editor, file, dataContext)
        }
    }

    private fun getWordAtCaret(editor: Editor, file: PsiFile): String? {
        val offset = editor.caretModel.offset
        val document = editor.document
        if (offset < 0 || offset > document.textLength) return null
        val text = document.text
        if (text.isEmpty()) return null
        // If caret is at end, look one char back
        var pos = offset
        if (pos == text.length && pos > 0) pos--
        if (pos < 0 || pos >= text.length) return null
        // If char at pos is not identifier part, try offset-1 (caret between words)
        var c = text[pos]
        if (!isIdentifierPart(c)) {
            if (pos > 0 && isIdentifierPart(text[pos - 1])) pos--
            else return null
        }
        var start = pos
        while (start > 0 && isIdentifierPart(text[start - 1])) start--
        if (!isIdentifierStart(text[start])) return null
        var end = start + 1
        while (end < text.length && isIdentifierPart(text[end])) end++
        val word = text.substring(start, end)
        if (!IDENTIFIER_REGEX.matches(word)) return null
        return word
    }

    private fun isTsrxFile(file: PsiFile): Boolean {
        if (file.name.endsWith(".tsrx", true)) return true
        if (file.virtualFile?.extension?.equals("tsrx", true) == true) return true
        if (file.language.isKindOf(TsrxLanguage) || file.language.id == "TSRX") return true
        return false
    }

    private fun isIdentifierStart(c: Char) = c == '_' || c == '$' || c.isLetter()
    private fun isIdentifierPart(c: Char) = c == '_' || c == '$' || c.isLetterOrDigit()

    companion object {
        private val LOG = Logger.getInstance(TsrxRenameHandler::class.java)
        private val IDENTIFIER_REGEX = Regex("[_\$a-zA-Z][_\$a-zA-Z0-9]*")
    }
}
