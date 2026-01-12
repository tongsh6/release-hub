#!/bin/bash
# 直接测试 VersionUpdater 功能（不通过 API）

set -e

echo "=== 直接测试 VersionUpdater 功能 ==="
echo ""

# 创建测试 pom.xml
TEST_REPO_PATH="/tmp/test-repo-direct"
TEST_POM_PATH="$TEST_REPO_PATH/pom.xml"

mkdir -p "$TEST_REPO_PATH"
cat > "$TEST_POM_PATH" << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.test</groupId>
    <artifactId>test-project</artifactId>
    <version>1.0.0</version>
    <name>Test Project</name>
</project>
EOF

echo "1. 测试文件已创建: $TEST_POM_PATH"
OLD_VERSION=$(grep '<version>' $TEST_POM_PATH | head -1 | sed 's/.*<version>\(.*\)<\/version>.*/\1/')
echo "   当前版本: $OLD_VERSION"
echo ""

# 使用 Java 直接测试（如果可能）
echo "2. 注意：直接测试需要运行 Java 代码"
echo "   可以通过以下方式验证："
echo "   - 运行单元测试: mvn test -Dtest=MavenVersionUpdaterTest"
echo "   - 或通过 API 测试（需要先创建仓库）"
echo ""

# 验证文件格式
echo "3. 验证 pom.xml 格式..."
if grep -q "<version>1.0.0</version>" "$TEST_POM_PATH"; then
  echo "   ✅ pom.xml 格式正确"
else
  echo "   ❌ pom.xml 格式错误"
fi
echo ""

echo "=== 建议的测试方式 ==="
echo "1. 运行单元测试验证 VersionUpdater 功能"
echo "2. 创建完整的测试数据（项目、仓库）后测试 API"
echo "3. 通过前端 UI 进行端到端测试"
echo ""
