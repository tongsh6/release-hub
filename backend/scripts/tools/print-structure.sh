#!/usr/bin/env bash
set -euo pipefail

# 中文说明：本脚本用于生成后端项目结构报告，便于审计类的归位与分层边界。
# 中文说明：脚本不会修改任何业务代码，只会在 docs/ 下输出 STRUCTURE_BACKEND.md。

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_FILE="${ROOT_DIR}/docs/STRUCTURE_BACKEND.md"

# 中文说明：过滤常见噪音目录，避免输出过长
TREE_IGNORE="target|.git|.idea|.vscode|node_modules|dist|build|out|logs|tmp|coverage|.DS_Store"

mkdir -p "${ROOT_DIR}/docs"

{
  echo "# Backend Project Structure Report"
  echo
  echo "生成时间（UTC）：$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
  echo "仓库根目录：\`${ROOT_DIR}\`"
  echo

  echo "## 1. 仓库目录树（过滤噪音目录）"
  echo
  echo "\`\`\`"
  if command -v tree >/dev/null 2>&1; then
    (cd "${ROOT_DIR}" && tree -a -I "${TREE_IGNORE}")
  else
    # 中文说明：无 tree 命令时退化为 find 输出
    (cd "${ROOT_DIR}" && find . -maxdepth 6 \
      -not -path "./.git/*" \
      -not -path "./target/*" \
      -not -path "./node_modules/*" \
      -not -path "./dist/*" \
      -not -path "./build/*" \
      -not -path "./out/*" \
      -not -path "./coverage/*" \
      -not -name ".DS_Store" \
      | sed 's|^\./||' | sort)
  fi
  echo "\`\`\`"
  echo

  echo "## 2. Maven 模块与关键坐标（如存在）"
  echo
  echo "\`\`\`"
  if [ -f "${ROOT_DIR}/pom.xml" ]; then
    echo "[pom.xml] 存在"
    echo
    echo "modules："
    awk '
      BEGIN{in_modules=0}
      /<modules>/ {in_modules=1}
      /<\/modules>/ {in_modules=0}
      in_modules && /<module>/ {
        gsub(/.*<module>/,""); gsub(/<\/module>.*/,""); print "- " $0
      }
    ' "${ROOT_DIR}/pom.xml" || true
    echo
    echo "groupId/artifactId/version（截取前 80 行匹配）："
    grep -nE "<groupId>|<artifactId>|<version>" "${ROOT_DIR}/pom.xml" | head -n 80 || true
  else
    echo "未发现 pom.xml（可能为 Gradle 或非 Maven 工程）。"
  fi
  echo "\`\`\`"
  echo

  echo "## 3. Java 源码类清单（main）"
  echo
  echo "\`\`\`"
  if [ -d "${ROOT_DIR}/src/main/java" ]; then
    (cd "${ROOT_DIR}" && find src/main/java -name "*.java" | sort)
  else
    # 中文说明：多模块场景下，搜索所有 */src/main/java/*.java
    (cd "${ROOT_DIR}" && find . -path "*/src/main/java/*.java" -name "*.java" | sort)
  fi
  echo "\`\`\`"
  echo

  echo "## 4. Java 测试类清单（test）"
  echo
  echo "\`\`\`"
  if [ -d "${ROOT_DIR}/src/test/java" ]; then
    (cd "${ROOT_DIR}" && find src/test/java -name "*.java" | sort)
  else
    (cd "${ROOT_DIR}" && find . -path "*/src/test/java/*.java" -name "*.java" | sort)
  fi
  echo "\`\`\`"
  echo

  echo "## 5. Spring 关键注解/配置扫描（入口/JPA/Security/Flyway）"
  echo
  echo "\`\`\`"
  # 中文说明：粗略扫描关键注解，便于快速定位启动类、配置类与扫描范围
  (cd "${ROOT_DIR}" && grep -RIn --include="*.java" -E \
    "@SpringBootApplication|@Configuration|@EnableJpaRepositories|@EntityScan|@EnableWebSecurity|Flyway" . \
    | head -n 240 || true)
  echo "\`\`\`"
  echo

  echo "## 6. 分层目录分布概览（domain/application/interfaces/infra/persistence/bootstrap）"
  echo
  echo "\`\`\`"
  # 中文说明：仅用于体感检查分层目录分布，不作为强校验
  (cd "${ROOT_DIR}" && find . -name "*.java" \
    | grep -E "/(domain|application|interfaces|infra|infrastructure|persistence|bootstrap)/" \
    | sed 's|^\./||' | sort | head -n 500 || true)
  echo "\`\`\`"
  echo

  echo "## 7. Flyway migration 位置扫描（避免重复来源）"
  echo
  echo "\`\`\`"
  (cd "${ROOT_DIR}" && find . -path "*/src/main/resources/db/migration/*" | sed 's|^\./||' | sort || true)
  echo "\`\`\`"
  echo
} > "${OUT_FILE}"

echo "已生成结构报告：${OUT_FILE}"
echo "请将 docs/STRUCTURE_BACKEND.md 全文复制粘贴到对话中用于审计。"
