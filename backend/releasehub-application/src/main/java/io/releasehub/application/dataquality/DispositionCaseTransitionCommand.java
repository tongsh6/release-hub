package io.releasehub.application.dataquality;

public record DispositionCaseTransitionCommand(
        String operator,
        String preStateSnapshot,
        String postStateSnapshot,
        String failureReason,
        String rollbackNote
) {
    public static DispositionCaseTransitionCommand start(String operator, String preStateSnapshot) {
        return new DispositionCaseTransitionCommand(operator, preStateSnapshot, null, null, null);
    }

    public static DispositionCaseTransitionCommand verify(String operator, String postStateSnapshot) {
        return new DispositionCaseTransitionCommand(operator, null, postStateSnapshot, null, null);
    }

    public static DispositionCaseTransitionCommand fail(String operator, String failureReason, String rollbackNote) {
        return new DispositionCaseTransitionCommand(operator, null, null, failureReason, rollbackNote);
    }

    public static DispositionCaseTransitionCommand cancel(String operator, String rollbackNote) {
        return new DispositionCaseTransitionCommand(operator, null, null, null, rollbackNote);
    }
}
