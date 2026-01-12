#!/bin/bash

OUTPUT_FILE="BACKEND_STRUCTURE_REPORT.md"
echo "# ReleaseHub Backend Structure Report" > "$OUTPUT_FILE"
echo "Generated on $(date)" >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"

# 1. Module Directory Tree
echo "## 1. Module Directory Tree" >> "$OUTPUT_FILE"
echo '```' >> "$OUTPUT_FILE"
# Use find to simulate tree, filtering appropriately
# We focus on src/main/java, src/test/java, src/main/resources and the modules themselves
find . -maxdepth 6 \
    -not -path '*/.*' \
    -not -path '*/target*' \
    -not -path '*/node_modules*' \
    -not -path './tools*' \
    -not -path './BACKEND_STRUCTURE_REPORT.md' \
    -not -path './mvnw*' \
    -not -path './*.md' \
    -not -path './*.xml' \
    -not -path './*.json' \
    -not -path './*.sql' \
    | sort | sed 's|[^/]*/|  |g' >> "$OUTPUT_FILE"
echo '```' >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"

# 2. Module -> Java File List & 3. Consistency Check
echo "## 2. Java File List" >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"

# Initialize violations file
: > violations.tmp

MODULES=("releasehub-domain" "releasehub-application" "releasehub-infrastructure" "releasehub-interfaces" "releasehub-bootstrap" "releasehub-common")

for module in "${MODULES[@]}"; do
    echo "### Module: $module" >> "$OUTPUT_FILE"
    echo "| Relative Path | Package Declaration |" >> "$OUTPUT_FILE"
    echo "|---|---|" >> "$OUTPUT_FILE"

    expected_pkg="io.releasehub.${module#releasehub-}"
    
    # Check if directory exists
    if [ -d "$module" ]; then
        # Find java files
        find "$module" -name "*.java" | sort | while read -r file; do
            # Extract package declaration
            pkg_line=$(grep "^package " "$file" | head -n 1)
            # Clean up package name (remove 'package ' and ';')
            pkg_name=$(echo "$pkg_line" | sed 's/package //; s/;//' | tr -d '\r')
            
            echo "| $file | $pkg_name |" >> "$OUTPUT_FILE"

            # Check consistency
            # If pkg_name is empty, skip check (or flag it)
            if [ -n "$pkg_name" ]; then
                if [[ "$pkg_name" != "$expected_pkg"* ]]; then
                     echo "- **$module**: \`$file\` (Package: \`$pkg_name\`, Expected starts with: \`$expected_pkg\`)" >> violations.tmp
                fi
            fi
        done
    else
        echo "Module directory not found." >> "$OUTPUT_FILE"
    fi
    echo "" >> "$OUTPUT_FILE"
done

echo "## 3. Layer Consistency Violations" >> "$OUTPUT_FILE"
if [ -s violations.tmp ]; then
    cat violations.tmp >> "$OUTPUT_FILE"
else
    echo "NONE" >> "$OUTPUT_FILE"
fi
rm violations.tmp
echo "" >> "$OUTPUT_FILE"

# 4. Key Entry Points
echo "## 4. Key Entry Points & Gatekeepers" >> "$OUTPUT_FILE"

echo "### ReleaseHubApplication" >> "$OUTPUT_FILE"
app_file=$(find releasehub-bootstrap -name "ReleaseHubApplication.java" | head -n 1)
if [ -n "$app_file" ]; then
    echo "Path: \`$app_file\`" >> "$OUTPUT_FILE"
    echo '```java' >> "$OUTPUT_FILE"
    grep -A 10 "^package " "$app_file" | head -n 30 >> "$OUTPUT_FILE"
    echo '```' >> "$OUTPUT_FILE"
else
    echo "ReleaseHubApplication.java not found." >> "$OUTPUT_FILE"
fi
echo "" >> "$OUTPUT_FILE"

echo "### ArchitectureRulesTest" >> "$OUTPUT_FILE"
arch_file=$(find . -name "ArchitectureRulesTest.java" | head -n 1)
if [ -n "$arch_file" ]; then
    echo "Path: \`$arch_file\`" >> "$OUTPUT_FILE"
    echo '```java' >> "$OUTPUT_FILE"
    head -n 80 "$arch_file" >> "$OUTPUT_FILE"
    echo '```' >> "$OUTPUT_FILE"
else
    echo "ArchitectureRulesTest.java not found." >> "$OUTPUT_FILE"
fi
echo "" >> "$OUTPUT_FILE"

echo "### Enforcer Configuration" >> "$OUTPUT_FILE"
find . -name "pom.xml" | while read -r pom; do
    if grep -q "maven-enforcer-plugin" "$pom"; then
        echo "#### $pom" >> "$OUTPUT_FILE"
        echo '```xml' >> "$OUTPUT_FILE"
        # Try to extract the configuration block for enforcer
        # This is a rough extraction: grep lines around bannedDependencies
        if grep -q "bannedDependencies" "$pom"; then
             grep -C 10 "bannedDependencies" "$pom" >> "$OUTPUT_FILE"
        else
             echo "<!-- Enforcer plugin configured but no explicit bannedDependencies block found in simple grep -->" >> "$OUTPUT_FILE"
             # Show the plugin declaration context
             grep -C 5 "maven-enforcer-plugin" "$pom" >> "$OUTPUT_FILE"
        fi
        echo '```' >> "$OUTPUT_FILE"
    fi
done

echo "DONE: BACKEND_STRUCTURE_REPORT.md"
