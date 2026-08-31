package dev.tsrx.intellij_plugin

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.util.ProcessingContext

class TsrxReferenceContributor : PsiReferenceContributor() {
    init {
        LOG.warn("TSRX reference contributor instantiated")
    }
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        LOG.warn("TSRX reference contributor registerReferenceProviders called")
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(TsrxTokenTypes.IDENTIFIER),
            object : PsiReferenceProvider() {
                override fun getReferencesByElement(
                    element: PsiElement,
                    context: ProcessingContext
                ): Array<PsiReference> {
                    val text = element.text
                    LOG.warn("TSRX reference provider IDENTIFIER hit: text='$text' lang=${element.language.id}")
                    if (text.isEmpty()) return PsiReference.EMPTY_ARRAY
                    // Whole identifier is the reference
                    return arrayOf(TsrxReference(element, TextRange(0, text.length)))
                }
            }
        )
        // Fallback: also match any leaf in TSRX file with identifier shape, in case token type mismatch
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement().withLanguage(TsrxLanguage),
            object : PsiReferenceProvider() {
                override fun getReferencesByElement(
                    element: PsiElement,
                    context: ProcessingContext
                ): Array<PsiReference> {
                    // Only provide for leaf identifier-like text, avoid whitespace/BAD_CHARACTER
                    val text = element.text
                    if (text.length < 1 || text.length > 80) return PsiReference.EMPTY_ARRAY
                    if (!IDENTIFIER_REGEX.matches(text)) return PsiReference.EMPTY_ARRAY
                    // Avoid providing for elements that already have a reference from above (they would duplicate)
                    if (element.node?.elementType === TsrxTokenTypes.IDENTIFIER) return PsiReference.EMPTY_ARRAY
                    LOG.warn("TSRX reference provider fallback hit: text='$text'")
                    return arrayOf(TsrxReference(element, TextRange(0, text.length)))
                }
            }
        )
    }

    companion object {
        private val LOG = Logger.getInstance(TsrxReferenceContributor::class.java)
        private val IDENTIFIER_REGEX = Regex("[_\$a-zA-Z][_\$a-zA-Z0-9]*")
    }
}
