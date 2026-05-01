package io.releasehub.application.iteration;

import io.releasehub.domain.version.ConflictType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("VersionConflict 检测测试")
class VersionConflictTest {

    private static final String REPO_ID = "repo-001";
    private static final String ITERATION_KEY = "iter-001";

    @Nested
    @DisplayName("noConflict - 无冲突")
    class NoConflictTest {

        @Test
        @DisplayName("版本一致时无冲突")
        void shouldHaveNoConflictWhenVersionsMatch() {
            String version = "1.0.0-SNAPSHOT";
            
            VersionConflict conflict = VersionConflict.noConflict(REPO_ID, ITERATION_KEY, version);

            assertThat(conflict.hasConflict()).isFalse();
            assertThat(conflict.getSystemVersion()).isEqualTo(version);
            assertThat(conflict.getRepoVersion()).isEqualTo(version);
            assertThat(conflict.getConflictType()).isNull();
            assertThat(conflict.getMessage()).isNull();
        }
    }

    @Nested
    @DisplayName("mismatch - 版本不匹配")
    class MismatchTest {

        @Test
        @DisplayName("检测版本不匹配冲突")
        void shouldDetectMismatchConflict() {
            String systemVersion = "1.1.0-SNAPSHOT";
            String repoVersion = "1.2.0-SNAPSHOT";
            
            VersionConflict conflict = VersionConflict.mismatch(REPO_ID, ITERATION_KEY, systemVersion, repoVersion);

            assertThat(conflict.hasConflict()).isTrue();
            assertThat(conflict.getConflictType()).isEqualTo(ConflictType.MISMATCH);
            assertThat(conflict.getSystemVersion()).isEqualTo(systemVersion);
            assertThat(conflict.getRepoVersion()).isEqualTo(repoVersion);
            assertThat(conflict.getMessage()).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("repoAhead - 仓库版本领先")
    class RepoAheadTest {

        @Test
        @DisplayName("检测仓库版本领先冲突")
        void shouldDetectRepoAheadConflict() {
            String systemVersion = "1.0.0-SNAPSHOT";
            String repoVersion = "1.1.0-SNAPSHOT";
            
            VersionConflict conflict = VersionConflict.repoAhead(REPO_ID, ITERATION_KEY, systemVersion, repoVersion);

            assertThat(conflict.hasConflict()).isTrue();
            assertThat(conflict.getConflictType()).isEqualTo(ConflictType.REPO_AHEAD);
            assertThat(conflict.getRepoId()).isEqualTo(REPO_ID);
            assertThat(conflict.getIterationKey()).isEqualTo(ITERATION_KEY);
        }
    }

    @Nested
    @DisplayName("systemAhead - 系统版本领先")
    class SystemAheadTest {

        @Test
        @DisplayName("检测系统版本领先冲突")
        void shouldDetectSystemAheadConflict() {
            String systemVersion = "2.0.0-SNAPSHOT";
            String repoVersion = "1.0.0-SNAPSHOT";
            
            VersionConflict conflict = VersionConflict.systemAhead(REPO_ID, ITERATION_KEY, systemVersion, repoVersion);

            assertThat(conflict.hasConflict()).isTrue();
            assertThat(conflict.getConflictType()).isEqualTo(ConflictType.SYSTEM_AHEAD);
        }
    }

    @Nested
    @DisplayName("集成场景测试")
    class IntegrationTest {

        @Test
        @DisplayName("根据版本比较创建正确的冲突类型")
        void shouldCreateCorrectConflictTypeBasedOnVersionComparison() {
            // 场景1：版本一致
            VersionConflict noConflict = detectConflict("1.0.0", "1.0.0");
            assertThat(noConflict.hasConflict()).isFalse();

            // 场景2：仓库版本更高
            VersionConflict repoAhead = detectConflict("1.0.0", "1.1.0");
            assertThat(repoAhead.getConflictType()).isEqualTo(ConflictType.REPO_AHEAD);

            // 场景3：系统版本更高
            VersionConflict systemAhead = detectConflict("2.0.0", "1.0.0");
            assertThat(systemAhead.getConflictType()).isEqualTo(ConflictType.SYSTEM_AHEAD);

            // 场景4：不同分支版本（无法比较，归为 MISMATCH）
            VersionConflict mismatch = detectConflict("1.0.0-SNAPSHOT", "1.0.0-RC1");
            assertThat(mismatch.getConflictType()).isEqualTo(ConflictType.MISMATCH);
        }

        /**
         * 模拟版本冲突检测逻辑
         */
        private VersionConflict detectConflict(String systemVersion, String repoVersion) {
            if (systemVersion.equals(repoVersion)) {
                return VersionConflict.noConflict(REPO_ID, ITERATION_KEY, systemVersion);
            }
            
            // 简单比较（实际应使用 VersionDeriver.compareVersions）
            int comparison = compareSimpleVersions(systemVersion, repoVersion);
            
            if (comparison < 0) {
                return VersionConflict.repoAhead(REPO_ID, ITERATION_KEY, systemVersion, repoVersion);
            } else if (comparison > 0) {
                return VersionConflict.systemAhead(REPO_ID, ITERATION_KEY, systemVersion, repoVersion);
            } else {
                return VersionConflict.mismatch(REPO_ID, ITERATION_KEY, systemVersion, repoVersion);
            }
        }

        private int compareSimpleVersions(String v1, String v2) {
            // 移除后缀比较主版本
            String clean1 = v1.replaceAll("-.*", "");
            String clean2 = v2.replaceAll("-.*", "");
            
            String[] parts1 = clean1.split("\\.");
            String[] parts2 = clean2.split("\\.");
            
            int maxLen = Math.max(parts1.length, parts2.length);
            for (int i = 0; i < maxLen; i++) {
                int num1 = i < parts1.length ? parseIntSafe(parts1[i]) : 0;
                int num2 = i < parts2.length ? parseIntSafe(parts2[i]) : 0;
                if (num1 != num2) {
                    return num1 - num2;
                }
            }
            
            // 主版本相同但后缀不同
            if (!v1.equals(v2)) {
                return 0; // 标记为需要进一步检查
            }
            return 0;
        }
        
        private int parseIntSafe(String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
    }
}
