package io.releasehub.application.version;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("VersionDeriver 单元测试")
class VersionDeriverTest {

    private VersionDeriver versionDeriver;

    @BeforeEach
    void setUp() {
        versionDeriver = new VersionDeriver();
    }

    @Nested
    @DisplayName("deriveDevVersion - 推导开发版本")
    class DeriveDevVersionTest {

        @Test
        @DisplayName("从正式版本推导：1.2.3 → 1.3.0-SNAPSHOT")
        void shouldDeriveFromReleaseVersion() {
            String result = versionDeriver.deriveDevVersion("1.2.3");
            assertThat(result).isEqualTo("1.3.0-SNAPSHOT");
        }

        @Test
        @DisplayName("从 SNAPSHOT 版本推导：1.2.3-SNAPSHOT → 1.3.0-SNAPSHOT")
        void shouldDeriveFromSnapshotVersion() {
            String result = versionDeriver.deriveDevVersion("1.2.3-SNAPSHOT");
            assertThat(result).isEqualTo("1.3.0-SNAPSHOT");
        }

        @Test
        @DisplayName("两位版本号：1.0 → 1.1.0-SNAPSHOT")
        void shouldHandleTwoPartVersion() {
            String result = versionDeriver.deriveDevVersion("1.0");
            assertThat(result).isEqualTo("1.1.0-SNAPSHOT");
        }

        @Test
        @DisplayName("单位版本号：1 → 1.1-SNAPSHOT")
        void shouldHandleSinglePartVersion() {
            String result = versionDeriver.deriveDevVersion("1");
            assertThat(result).isEqualTo("1.1-SNAPSHOT");
        }

        @Test
        @DisplayName("版本号进位：1.9.5 → 1.10.0-SNAPSHOT")
        void shouldHandleMinorVersionCarry() {
            String result = versionDeriver.deriveDevVersion("1.9.5");
            assertThat(result).isEqualTo("1.10.0-SNAPSHOT");
        }
    }

    @Nested
    @DisplayName("deriveTargetVersion - 推导目标版本")
    class DeriveTargetVersionTest {

        @Test
        @DisplayName("移除 SNAPSHOT：1.3.0-SNAPSHOT → 1.3.0")
        void shouldRemoveSnapshot() {
            String result = versionDeriver.deriveTargetVersion("1.3.0-SNAPSHOT");
            assertThat(result).isEqualTo("1.3.0");
        }

        @Test
        @DisplayName("正式版本保持不变：1.3.0 → 1.3.0")
        void shouldKeepReleaseVersion() {
            String result = versionDeriver.deriveTargetVersion("1.3.0");
            assertThat(result).isEqualTo("1.3.0");
        }
    }

    @Nested
    @DisplayName("isSnapshot - 检查是否为 SNAPSHOT")
    class IsSnapshotTest {

        @Test
        @DisplayName("SNAPSHOT 版本返回 true")
        void shouldReturnTrueForSnapshot() {
            assertThat(versionDeriver.isSnapshot("1.0.0-SNAPSHOT")).isTrue();
        }

        @Test
        @DisplayName("正式版本返回 false")
        void shouldReturnFalseForRelease() {
            assertThat(versionDeriver.isSnapshot("1.0.0")).isFalse();
        }

        @Test
        @DisplayName("小写 snapshot 也返回 true")
        void shouldHandleLowerCaseSnapshot() {
            assertThat(versionDeriver.isSnapshot("1.0.0-snapshot")).isTrue();
        }

        @Test
        @DisplayName("null 返回 false")
        void shouldReturnFalseForNull() {
            assertThat(versionDeriver.isSnapshot(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("compareVersions - 版本比较")
    class CompareVersionsTest {

        @Test
        @DisplayName("相等版本返回 0")
        void shouldReturnZeroForEqualVersions() {
            assertThat(versionDeriver.compareVersions("1.2.3", "1.2.3")).isEqualTo(0);
        }

        @Test
        @DisplayName("较小版本返回负数")
        void shouldReturnNegativeForSmallerVersion() {
            assertThat(versionDeriver.compareVersions("1.2.3", "1.2.4")).isLessThan(0);
        }

        @Test
        @DisplayName("较大版本返回正数")
        void shouldReturnPositiveForLargerVersion() {
            assertThat(versionDeriver.compareVersions("1.2.4", "1.2.3")).isGreaterThan(0);
        }

        @Test
        @DisplayName("SNAPSHOT < 正式版")
        void shouldSnapshotBeLessThanRelease() {
            assertThat(versionDeriver.compareVersions("1.2.3-SNAPSHOT", "1.2.3")).isLessThan(0);
        }

        @Test
        @DisplayName("正式版 > SNAPSHOT")
        void shouldReleaseBeGreaterThanSnapshot() {
            assertThat(versionDeriver.compareVersions("1.2.3", "1.2.3-SNAPSHOT")).isGreaterThan(0);
        }

        @Test
        @DisplayName("相同 SNAPSHOT 版本返回 0")
        void shouldReturnZeroForEqualSnapshots() {
            assertThat(versionDeriver.compareVersions("1.2.3-SNAPSHOT", "1.2.3-SNAPSHOT")).isEqualTo(0);
        }

        @Test
        @DisplayName("次版本号比较")
        void shouldCompareMinorVersion() {
            assertThat(versionDeriver.compareVersions("1.3.0", "1.2.9")).isGreaterThan(0);
        }

        @Test
        @DisplayName("主版本号比较")
        void shouldCompareMajorVersion() {
            assertThat(versionDeriver.compareVersions("2.0.0", "1.9.9")).isGreaterThan(0);
        }
    }

    @Nested
    @DisplayName("addSnapshot - 添加 SNAPSHOT 后缀")
    class AddSnapshotTest {

        @Test
        @DisplayName("正式版本添加后缀")
        void shouldAddSnapshotToRelease() {
            assertThat(versionDeriver.addSnapshot("1.0.0")).isEqualTo("1.0.0-SNAPSHOT");
        }

        @Test
        @DisplayName("已有 SNAPSHOT 不重复添加")
        void shouldNotAddDuplicateSnapshot() {
            assertThat(versionDeriver.addSnapshot("1.0.0-SNAPSHOT")).isEqualTo("1.0.0-SNAPSHOT");
        }

        @Test
        @DisplayName("null 返回 null")
        void shouldReturnNullForNull() {
            assertThat(versionDeriver.addSnapshot(null)).isNull();
        }
    }
}
