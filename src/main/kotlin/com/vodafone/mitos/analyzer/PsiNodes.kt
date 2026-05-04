package com.vodafone.mitos.analyzer

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.vodafone.mitos.model.CallNode
import com.vodafone.mitos.model.Language
import com.vodafone.mitos.model.NodeKind

/** Helpers that build [CallNode]s from PSI without leaking `PsiElement`s. */
internal object PsiNodes {
    fun lineOf(element: PsiElement): Int {
        val file = element.containingFile ?: return 0
        val document = FileDocumentManager.getInstance().getDocument(file.virtualFile) ?: return 0
        return document.getLineNumber(element.textOffset) + 1
    }

    fun pathOf(file: PsiFile?): String = file?.virtualFile?.path ?: ""

    fun build(
        displayName: String,
        qualifiedName: String,
        kind: NodeKind,
        language: Language,
        filePath: String,
        offset: Int,
        lineNumber: Int,
        tooltip: String = qualifiedName,
    ): CallNode = CallNode(
        id = CallNode.makeId(filePath, offset, kind),
        displayName = displayName,
        qualifiedName = qualifiedName,
        kind = kind,
        language = language,
        filePath = filePath,
        offset = offset,
        lineNumber = lineNumber,
        tooltip = tooltip,
    )
}
