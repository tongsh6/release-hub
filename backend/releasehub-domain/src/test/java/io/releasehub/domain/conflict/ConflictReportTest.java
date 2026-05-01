package io.releasehub.domain.conflict;

import io.releasehub.domain.version.ConflictType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConflictReportTest {

    @Test
    void shouldCreateVersionConflictItem() {
        ConflictItem item = ConflictItem.versionMismatch(
                "R001", "repo-a", "ITER-001",
                "1.0.0", "1.1.0");

        assertThat(item.getConflictType()).isEqualTo(ConflictType.MISMATCH);
        assertThat(item.getRepoId()).isEqualTo("R001");
        assertThat(item.getRepoName()).isEqualTo("repo-a");
        assertThat(item.getSystemVersion()).isEqualTo("1.0.0");
        assertThat(item.getRepoVersion()).isEqualTo("1.1.0");
    }

    @Test
    void shouldCreateBranchExistsConflictItem() {
        ConflictItem item = ConflictItem.branchExists(
                "R001", "repo-a", "ITER-001",
                "feature/ITER-001");

        assertThat(item.getConflictType()).isEqualTo(ConflictType.BRANCH_EXISTS);
        assertThat(item.getSourceBranch()).isEqualTo("feature/ITER-001");
    }

    @Test
    void shouldCreateMergeConflictItem() {
        ConflictItem item = ConflictItem.mergeConflict(
                "R001", "repo-a", "ITER-001",
                "feature/ITER-001", "release/v1.2.0",
                "Merge conflict in pom.xml");

        assertThat(item.getConflictType()).isEqualTo(ConflictType.MERGE_CONFLICT);
        assertThat(item.getSourceBranch()).isEqualTo("feature/ITER-001");
        assertThat(item.getTargetBranch()).isEqualTo("release/v1.2.0");
        assertThat(item.getMessage()).contains("pom.xml");
    }

    @Test
    void emptyReportShouldHaveNoConflicts() {
        ConflictReport report = ConflictReport.empty("W001");

        assertThat(report.hasConflicts()).isFalse();
        assertThat(report.totalCount()).isEqualTo(0);
    }

    @Test
    void reportWithConflictsShouldDetectConflicts() {
        ConflictReport report = ConflictReport.of("W001",
                java.util.List.of(
                        ConflictItem.versionMismatch("R001", "a", "I001", "1.0", "1.1"),
                        ConflictItem.branchExists("R002", "b", "I001", "feat/x")));

        assertThat(report.hasConflicts()).isTrue();
        assertThat(report.totalCount()).isEqualTo(2);
    }
}
