package dev.tsrx.intellij_plugin

import com.intellij.lang.refactoring.RefactoringSupportProvider
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiElement

class TsrxRefactoringSupportProvider : RefactoringSupportProvider() {
    init {
        LOG.warn("TSRX refactoring support provider instantiated")
    }

    override fun isInplaceRenameAvailable(element: PsiElement, context: PsiElement): Boolean {
        val res = isTsrxIdentifier(element) || isTsrxFile(element, context)
        LOG.warn("TSRX rename isInplaceRenameAvailable: element=${element.text?.take(30)} type=${element.node?.elementType} lang=${element.language.id} fileLang=${element.containingFile?.language?.id} res=$res")
        return res
    }

    override fun isMemberInplaceRenameAvailable(element: PsiElement, context: PsiElement?): Boolean {
        val res = if (context == null) isTsrxIdentifier(element) else isInplaceRenameAvailable(element, context)
        LOG.warn("TSRX rename isMemberInplaceRenameAvailable: element=${element.text?.take(30)} res=$res")
        return res
    }

    override fun isAvailable(element: PsiElement): Boolean {
        val res = element.containingFile?.language?.`is`(TsrxLanguage) == true || element.containingFile?.fileType === TsrxFileType.INSTANCE
        LOG.warn("TSRX rename isAvailable: element=${element.text?.take(30)} res=$res")
        return res
    }

    private fun isTsrxFile(element: PsiElement, context: PsiElement): Boolean {
        val file = element.containingFile ?: context.containingFile
        return file?.language?.`is`(TsrxLanguage) == true || file?.fileType === TsrxFileType.INSTANCE
    }

    private fun isTsrxIdentifier(element: PsiElement): Boolean {
        val type = element.node?.elementType
        if (type === TsrxTokenTypes.IDENTIFIER) return true
        if (element.language.`is`(TsrxLanguage)) return true
        // Fallback: leaf text looks like identifier and file is TSRX
        val text = element.text
        if (text != null && IDENTIFIER_REGEX.matches(text)) {
            val file = element.containingFile
            if (file?.language?.`is`(TsrxLanguage) == true || file?.fileType === TsrxFileType.INSTANCE) return true
        }
        return false
    }

    companion object {
        private val LOG = Logger.getInstance(TsrxRefactoringSupportProvider::class.java)
        private val IDENTIFIER_REGEX = Regex("[_\$a-zA-Z][_\$a-zA-Z0-9]*")
    }
}
