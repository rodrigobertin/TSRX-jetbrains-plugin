package dev.tsrx.intellij_plugin

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase
import com.intellij.util.IncorrectOperationException

class TsrxReference(element: PsiElement, range: TextRange) : PsiReferenceBase<PsiElement>(element, range, false) {

    override fun resolve(): PsiElement? = myElement

    override fun isReferenceTo(element: PsiElement): Boolean {
        // Word-based: same text identifier in same file is considered reference to same symbol (for local rename without LSP)
        if (element.text != myElement.text) return false
        if (element.containingFile != myElement.containingFile) return false
        val type = element.node?.elementType
        if (type != null && type !== TsrxTokenTypes.IDENTIFIER) return false
        return true
    }

    override fun getVariants(): Array<Any> = emptyArray()

    @Throws(IncorrectOperationException::class)
    override fun handleElementRename(newElementName: String): PsiElement {
        return com.intellij.psi.ElementManipulators.handleContentChange(myElement, rangeInElement, newElementName)
    }
}
