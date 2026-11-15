package io.github.topdanya33200_netizen.reporthook.model;

/**
 * Outcome of a status change request.
 */
public sealed interface TransitionResult {

    /** The status was changed; {@code report} is the updated row. */
    record Changed(Report report, ReportStatus previous) implements TransitionResult {
    }

    /** The change is not allowed from the report's current status. */
    record Rejected(Report current) implements TransitionResult {
    }

    record NotFound(long id) implements TransitionResult {
    }
}
