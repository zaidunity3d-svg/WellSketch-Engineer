package com.wellsketch.engineer.core.engineering.validation

/** Result of a consistency or engineering check, shown to the user verbatim. */
data class ValidationIssue(
    val severity: Severity,
    val code: String,
    val message: String,
    /** Model ids the issue refers to (for highlighting in the UI). */
    val subjectIds: List<String> = emptyList(),
) {
    enum class Severity(val label: String) { ERROR("Error"), WARNING("Warning"), INFO("Info") }
}

fun List<ValidationIssue>.errors() = filter { it.severity == ValidationIssue.Severity.ERROR }
fun List<ValidationIssue>.hasErrors() = any { it.severity == ValidationIssue.Severity.ERROR }
