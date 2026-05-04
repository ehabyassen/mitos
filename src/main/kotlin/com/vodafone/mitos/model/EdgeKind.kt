package com.vodafone.mitos.model

/**
 * Type of relationship between two [CallNode]s. The renderer assigns each kind
 * a colour and a confidence style (solid vs dashed) per FR-18c / FR-15.
 */
enum class EdgeKind(val confidence: Confidence) {
    DIRECT_CALL(Confidence.CONFIDENT),
    JSP_INCLUDE(Confidence.CONFIDENT),
    FORWARD(Confidence.CONFIDENT),
    EL_REFERENCE(Confidence.CONFIDENT),
    SCRIPTLET_CALL(Confidence.CONFIDENT),
    JS_INVOCATION(Confidence.CONFIDENT),
    AJAX_REQUEST(Confidence.HEURISTIC),
    ;

    enum class Confidence { CONFIDENT, HEURISTIC }
}
