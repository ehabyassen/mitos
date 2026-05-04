package com.vodafone.mitos.model

/**
 * Role of a node in the graph. The renderer maps each kind to a distinct shape
 * (FR-18a) so the language *and* the role are readable at a glance.
 */
enum class NodeKind {
    METHOD,
    FIELD,
    JSP_PAGE,
    JSP_INCLUDE,
    JS_FUNCTION,
    CONTROLLER_MAPPING,
    SERVICE,
    REPOSITORY,
}
