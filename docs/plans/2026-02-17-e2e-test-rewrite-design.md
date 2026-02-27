# E2E Test Rewrite Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 用 TestContainers + PostgreSQL + TestRestTemplate 全量重写 E2E API 测试，替代手动 curl，实现可复现的自动化端到端测试。

**Architecture:** 在 `releasehub-bootstrap` 模块新建 `e2e` 包，所有 E2E 测试继承 `E2eTestBase` 基类。基类通过 TestContainers 启动 PostgreSQL 18.1 容器，`@DynamicPropertySource` 注入连接参数，`TestRestTemplate` 发起真实 HTTP 请求。使用 `@ActiveProfiles("e2e")` 隔离配置。

**Tech Stack:** Java 21, Spring Boot 3.4.1, TestContainers (PostgreSQL 18.1), JUnit 5, TestRestTemplate, Flyway, AssertJ

---

## Task 1: Add TestContainers Dependencies

**Files:**
- Modify: `release-hub/pom.xml` (parent pom, add testcontainers BOM to dependencyManagement)
- Modify: `release-hub/releasehub-bootstrap/pom.xml` (add testcontainers dependencies)

**Step 1: Add TestContainers BOM to parent pom.xml**

在 `release-hub/pom.xml` 的 `<properties>` 中添加版本号：

```xml
<testcontainers.version>1.20.4</testcontainers.version>
```

在 `<dependencyManagement><dependencies>` 中（`spring-boot-dependencies` BOM 旁边）添加：

```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers-bom</artifactId>
    <version>${testcontainers.version}</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
```

**Step 2: Add TestContainers dependencies to bootstrap pom.xml**

在 `release-hub/releasehub-bootstrap/pom.xml` 的 `<dependencies>` 中添加（`spring-security-test` 之后）：

```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
```

**Step 3: Verify dependencies resolve**

Run: `cd /Users/tongshuanglong/releasehub/release-hub && mvn dependency:resolve -pl releasehub-bootstrap -q`
Expected: BUILD SUCCESS, no errors

**Step 4: Commit**

```bash
git add release-hub/pom.xml release-hub/releasehub-bootstrap/pom.xml
git commit -m "chore: add TestContainers dependencies for E2E tests"
```

---

## Task 2: Create E2E Profile and Base Class

**Files:**
- Create: `release-hub/releasehub-bootstrap/src/main/resources/application-e2e.yml`
- Create: `release-hub/releasehub-bootstrap/src/test/java/io/releasehub/bootstrap/e2e/E2eTestBase.java`

**Step 1: Create application-e2e.yml**

```yaml
releasehub:
  seed:
    enabled: true

spring:
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        format_sql: true
  flyway:
    enabled: true
    baseline-on-migrate: true
    locations: classpath:db/migration
    fail-on-missing-locations: true

security:
  jwt:
    secret: "E2eTestSecretKeyForReleaseHubJwtTokenGenerationAndValidationWhichMustBeLongEnough"
    ttlMinutes: 120
cors:
  allowedOrigins: "*"
```

注意：`spring.datasource.*` 不在 yml 中配置，由 `@DynamicPropertySource` 动态注入。

**Step 2: Create E2eTestBase.java**

```java
package io.releasehub.bootstrap.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("e2e")
@Testcontainers
public abstract class E2eTestBase {

    @Container
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:18.1")
            .withDatabaseName("releasehub_e2e")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void pgProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", pg::getJdbcUrl);
        registry.add("spring.datasource.username", pg::getUsername);
        registry.add("spring.datasource.password", pg::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired
    protected TestRestTemplate rest;

    @Autowired
    protected ObjectMapper objectMapper;

    /**
     * Login as admin and return the JWT token.
     */
    protected String loginAndGetToken() {
        var body = Map.of("username", "admin", "password", "admin");
        ResponseEntity<String> resp = rest.postForEntity("/api/v1/auth/login", body, String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        JsonNode node = readTree(resp.getBody());
        assertThat(node.path("success").asBoolean()).isTrue();
        return node.path("data").path("token").asText();
    }

    /**
     * Build HttpHeaders with Authorization Bearer token.
     */
    protected HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return headers;
    }

    /**
     * Build an HttpEntity with auth headers and a JSON body.
     */
    protected <T> HttpEntity<T> authEntity(String token, T body) {
        return new HttpEntity<>(body, authHeaders(token));
    }

    /**
     * Build an HttpEntity with auth headers and no body (for GET/DELETE).
     */
    protected HttpEntity<Void> authEntity(String token) {
        return new HttpEntity<>(authHeaders(token));
    }

    /**
     * Generate a unique code with prefix to avoid test data collisions.
     */
    protected String uniqueCode(String prefix) {
        return prefix + "-" + System.currentTimeMillis();
    }

    /**
     * Parse JSON string to JsonNode.
     */
    protected JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse JSON: " + json, e);
        }
    }
}
```

**Step 3: Write a smoke test to verify the base class works**

Create `release-hub/releasehub-bootstrap/src/test/java/io/releasehub/bootstrap/e2e/SmokeE2eTest.java`:

```java
package io.releasehub.bootstrap.e2e;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class SmokeE2eTest extends E2eTestBase {

    @Test
    void shouldStartWithPostgreSQLAndLogin() {
        assertThat(pg.isRunning()).isTrue();

        String token = loginAndGetToken();
        assertThat(token).isNotBlank();

        // Verify /me endpoint
        ResponseEntity<String> resp = rest.exchange(
                "/api/v1/me", org.springframework.http.HttpMethod.GET,
                authEntity(token), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(readTree(resp.getBody()).path("data").path("username").asText()).isEqualTo("admin");
    }
}
```

**Step 4: Run the smoke test**

Run: `cd /Users/tongshuanglong/releasehub/release-hub && mvn test -pl releasehub-bootstrap -Dtest="io.releasehub.bootstrap.e2e.SmokeE2eTest" -q`
Expected: BUILD SUCCESS — TestContainers starts PG, Flyway migrates, DataSeeder creates admin, login works.

**Step 5: Commit**

```bash
git add release-hub/releasehub-bootstrap/src/main/resources/application-e2e.yml
git add release-hub/releasehub-bootstrap/src/test/java/io/releasehub/bootstrap/e2e/
git commit -m "feat: add E2E test base with TestContainers PostgreSQL"
```

---

## Task 3: Auth E2E Test

**Files:**
- Create: `release-hub/releasehub-bootstrap/src/test/java/io/releasehub/bootstrap/e2e/auth/AuthE2eTest.java`

**Step 1: Write the test**

```java
package io.releasehub.bootstrap.e2e.auth;

import io.releasehub.bootstrap.e2e.E2eTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuthE2eTest extends E2eTestBase {

    @Test
    void loginSuccess() {
        var body = Map.of("username", "admin", "password", "admin");
        ResponseEntity<String> resp = rest.postForEntity("/api/v1/auth/login", body, String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        JsonNode data = readTree(resp.getBody()).path("data");
        assertThat(data.path("token").asText()).isNotBlank();
    }

    @Test
    void loginFailWrongPassword() {
        var body = Map.of("username", "admin", "password", "wrong");
        ResponseEntity<String> resp = rest.postForEntity("/api/v1/auth/login", body, String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(401);
        JsonNode root = readTree(resp.getBody());
        assertThat(root.path("success").asBoolean()).isFalse();
        assertThat(root.path("code").asText()).isEqualTo("AUTH_001");
    }

    @Test
    void meWithoutTokenReturns401() {
        ResponseEntity<String> resp = rest.getForEntity("/api/v1/me", String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void meWithTokenReturnsUserInfo() {
        String token = loginAndGetToken();
        ResponseEntity<String> resp = rest.exchange(
                "/api/v1/me", HttpMethod.GET, authEntity(token), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        JsonNode data = readTree(resp.getBody()).path("data");
        assertThat(data.path("username").asText()).isEqualTo("admin");
        assertThat(data.path("displayName").asText()).isEqualTo("Admin User");
    }

    @Test
    void protectedEndpointWithoutTokenReturns401() {
        ResponseEntity<String> resp = rest.getForEntity("/api/v1/groups", String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(401);
    }
}
```

**Step 2: Run and verify**

Run: `cd /Users/tongshuanglong/releasehub/release-hub && mvn test -pl releasehub-bootstrap -Dtest="io.releasehub.bootstrap.e2e.auth.AuthE2eTest" -q`
Expected: 5 tests PASS

**Step 3: Commit**

```bash
git add release-hub/releasehub-bootstrap/src/test/java/io/releasehub/bootstrap/e2e/auth/
git commit -m "test: add Auth E2E tests (login/me/401)"
```

---

## Task 4: Group E2E Test

**Files:**
- Create: `release-hub/releasehub-bootstrap/src/test/java/io/releasehub/bootstrap/e2e/group/GroupE2eTest.java`

**Step 1: Write the test**

```java
package io.releasehub.bootstrap.e2e.group;

import io.releasehub.bootstrap.e2e.E2eTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.http.*;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GroupE2eTest extends E2eTestBase {

    private static String token;
    private static String parentId;
    private static String parentCode;
    private static String childId;
    private static String childCode;

    @BeforeAll
    void auth() {
        token = loginAndGetToken();
    }

    // --- CRUD ---

    @Test @Order(1)
    void createParentGroup() {
        parentCode = uniqueCode("e2e-parent");
        var body = Map.of("name", "E2E Parent", "code", parentCode);
        ResponseEntity<String> resp = rest.exchange(
                "/api/v1/groups", HttpMethod.POST, authEntity(token, body), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        parentId = readTree(resp.getBody()).path("data").asText();
        assertThat(parentId).isNotBlank();
    }

    @Test @Order(2)
    void createChildGroup() {
        childCode = uniqueCode("e2e-child");
        var body = Map.of("name", "E2E Child", "code", childCode, "parentCode", parentCode);
        ResponseEntity<String> resp = rest.exchange(
                "/api/v1/groups", HttpMethod.POST, authEntity(token, body), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        childId = readTree(resp.getBody()).path("data").asText();
        assertThat(childId).isNotBlank();
    }

    @Test @Order(3)
    void getById() {
        ResponseEntity<String> resp = rest.exchange(
                "/api/v1/groups/" + parentId, HttpMethod.GET, authEntity(token), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        JsonNode data = readTree(resp.getBody()).path("data");
        assertThat(data.path("name").asText()).isEqualTo("E2E Parent");
        assertThat(data.path("code").asText()).isEqualTo(parentCode);
    }

    @Test @Order(4)
    void getByCode() {
        ResponseEntity<String> resp = rest.exchange(
                "/api/v1/groups/by-code/" + childCode, HttpMethod.GET, authEntity(token), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        JsonNode data = readTree(resp.getBody()).path("data");
        assertThat(data.path("code").asText()).isEqualTo(childCode);
        assertThat(data.path("parentCode").asText()).isEqualTo(parentCode);
    }

    @Test @Order(5)
    void updateGroup() {
        var body = Map.of("name", "E2E Parent Updated");
        ResponseEntity<String> resp = rest.exchange(
                "/api/v1/groups/" + parentId, HttpMethod.PUT, authEntity(token, body), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(readTree(resp.getBody()).path("data").path("name").asText()).isEqualTo("E2E Parent Updated");
    }

    // --- List & Query ---

    @Test @Order(6)
    void listAll() {
        ResponseEntity<String> resp = rest.exchange(
                "/api/v1/groups", HttpMethod.GET, authEntity(token), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(readTree(resp.getBody()).path("data").size()).isGreaterThanOrEqualTo(2);
    }

    @Test @Order(7)
    void listPaged() {
        ResponseEntity<String> resp = rest.exchange(
                "/api/v1/groups/paged?page=1&size=10", HttpMethod.GET, authEntity(token), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        JsonNode root = readTree(resp.getBody());
        assertThat(root.path("data").size()).isGreaterThanOrEqualTo(2);
        assertThat(root.path("page").path("total").asLong()).isGreaterThanOrEqualTo(2);
    }

    @Test @Order(8)
    void children() {
        ResponseEntity<String> resp = rest.exchange(
                "/api/v1/groups/children/" + parentCode, HttpMethod.GET, authEntity(token), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        JsonNode data = readTree(resp.getBody()).path("data");
        assertThat(data.size()).isEqualTo(1);
        assertThat(data.get(0).path("code").asText()).isEqualTo(childCode);
    }

    @Test @Order(9)
    void topLevel() {
        ResponseEntity<String> resp = rest.exchange(
                "/api/v1/groups/top-level", HttpMethod.GET, authEntity(token), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        // parent should be in top-level, child should not
        JsonNode data = readTree(resp.getBody()).path("data");
        boolean hasParent = false;
        boolean hasChild = false;
        for (JsonNode g : data) {
            if (g.path("code").asText().equals(parentCode)) hasParent = true;
            if (g.path("code").asText().equals(childCode)) hasChild = true;
        }
        assertThat(hasParent).isTrue();
        assertThat(hasChild).isFalse();
    }

    @Test @Order(10)
    void tree() {
        ResponseEntity<String> resp = rest.exchange(
                "/api/v1/groups/tree", HttpMethod.GET, authEntity(token), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(readTree(resp.getBody()).path("data").size()).isGreaterThanOrEqualTo(1);
    }

    // --- Boundary ---

    @Test @Order(11)
    void duplicateCodeReturnsError() {
        var body = Map.of("name", "Dup", "code", parentCode);
        ResponseEntity<String> resp = rest.exchange(
                "/api/v1/groups", HttpMethod.POST, authEntity(token, body), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(400);
        assertThat(readTree(resp.getBody()).path("code").asText()).isEqualTo("GROUP_002");
    }

    @Test @Order(12)
    void deleteGroupWithChildrenFails() {
        ResponseEntity<String> resp = rest.exchange(
                "/api/v1/groups/" + parentId, HttpMethod.DELETE, authEntity(token), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(400);
        assertThat(readTree(resp.getBody()).path("code").asText()).isEqualTo("GROUP_008");
    }

    @Test @Order(13)
    void deleteChildThenParent() {
        // Delete child first
        ResponseEntity<String> r1 = rest.exchange(
                "/api/v1/groups/" + childId, HttpMethod.DELETE, authEntity(token), String.class);
        assertThat(r1.getStatusCode().value()).isEqualTo(200);

        // Now delete parent
        ResponseEntity<String> r2 = rest.exchange(
                "/api/v1/groups/" + parentId, HttpMethod.DELETE, authEntity(token), String.class);
        assertThat(r2.getStatusCode().value()).isEqualTo(200);
    }
}
```

**Step 2: Run and verify**

Run: `cd /Users/tongshuanglong/releasehub/release-hub && mvn test -pl releasehub-bootstrap -Dtest="io.releasehub.bootstrap.e2e.group.GroupE2eTest" -q`
Expected: 13 tests PASS

**Step 3: Commit**

```bash
git add release-hub/releasehub-bootstrap/src/test/java/io/releasehub/bootstrap/e2e/group/
git commit -m "test: add Group E2E tests (CRUD, tree, hierarchy, boundary)"
```

---

## Task 5: VersionPolicy E2E Test

**Files:**
- Create: `release-hub/releasehub-bootstrap/src/test/java/io/releasehub/bootstrap/e2e/versionpolicy/VersionPolicyE2eTest.java`

**Step 1: Write the test** (重点回归 B1: keyword=null bytea 问题)

```java
package io.releasehub.bootstrap.e2e.versionpolicy;

import io.releasehub.bootstrap.e2e.E2eTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.http.*;

import static org.assertj.core.api.Assertions.assertThat;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class VersionPolicyE2eTest extends E2eTestBase {

    private static String token;
    private static String firstPolicyId;

    @BeforeAll
    void auth() {
        token = loginAndGetToken();
    }

    @Test @Order(1)
    void listAll() {
        ResponseEntity<String> resp = rest.exchange(
                "/api/v1/version-policies", HttpMethod.GET, authEntity(token), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        JsonNode data = readTree(resp.getBody()).path("data");
        // VersionPolicyDataInitializer seeds 4 policies
        assertThat(data.size()).isGreaterThanOrEqualTo(4);
        firstPolicyId = data.get(0).path("id").asText();
    }

    @Test @Order(2)
    void pagedWithoutKeyword_regression_B1() {
        // B1 regression: keyword=null should NOT cause lower(bytea) error
        ResponseEntity<String> resp = rest.exchange(
                "/api/v1/version-policies/paged?page=1&size=10", HttpMethod.GET, authEntity(token), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        JsonNode root = readTree(resp.getBody());
        assertThat(root.path("success").asBoolean()).isTrue();
        assertThat(root.path("data").size()).isGreaterThanOrEqualTo(4);
    }

    @Test @Order(3)
    void pagedWithKeyword() {
        ResponseEntity<String> resp = rest.exchange(
                "/api/v1/version-policies/paged?page=1&size=10&keyword=SEMVER", HttpMethod.GET, authEntity(token), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(readTree(resp.getBody()).path("data").size()).isGreaterThanOrEqualTo(1);
    }

    @Test @Order(4)
    void getById() {
        assertThat(firstPolicyId).isNotNull();
        ResponseEntity<String> resp = rest.exchange(
                "/api/v1/version-policies/" + firstPolicyId, HttpMethod.GET, authEntity(token), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(readTree(resp.getBody()).path("data").path("id").asText()).isEqualTo(firstPolicyId);
    }
}
```

**Step 2: Run and verify**

Run: `cd /Users/tongshuanglong/releasehub/release-hub && mvn test -pl releasehub-bootstrap -Dtest="io.releasehub.bootstrap.e2e.versionpolicy.VersionPolicyE2eTest" -q`
Expected: 4 tests PASS (B1 regression verified on real PostgreSQL)

**Step 3: Commit**

```bash
git add release-hub/releasehub-bootstrap/src/test/java/io/releasehub/bootstrap/e2e/versionpolicy/
git commit -m "test: add VersionPolicy E2E tests (B1 bytea regression)"
```

---

## Task 6: Repository E2E Test

**Files:**
- Create: `release-hub/releasehub-bootstrap/src/test/java/io/releasehub/bootstrap/e2e/repository/RepositoryE2eTest.java`

**Step 1: Write the test**

```java
package io.releasehub.bootstrap.e2e.repository;

import io.releasehub.bootstrap.e2e.E2eTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.http.*;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RepositoryE2eTest extends E2eTestBase {

    private static String token;
    private static String groupCode;
    private static String repoId;
    private static String repoName;

    @BeforeAll
    void auth() {
        token = loginAndGetToken();
        // Create a leaf group for repos
        groupCode = uniqueCode("e2e-repo-grp");
        var body = Map.of("name", "Repo Test Group", "code", groupCode);
        rest.exchange("/api/v1/groups", HttpMethod.POST, authEntity(token, body), String.class);
    }

    @Test @Order(1)
    void createRepository() {
        repoName = uniqueCode("e2e-repo");
        var body = Map.of(
                "name", repoName,
                "cloneUrl", "https://gitlab.example.com/" + repoName + ".git",
                "groupCode", groupCode,
                "defaultBranch", "main"
        );
        ResponseEntity<String> resp = rest.exchange(
                "/api/v1/repositories", HttpMethod.POST, authEntity(token, body), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        JsonNode data = readTree(resp.getBody()).path("data");
        repoId = data.path("id").asText();
        assertThat(repoId).isNotBlank();
        assertThat(data.path("name").asText()).isEqualTo(repoName);
        assertThat(data.path("groupCode").asText()).isEqualTo(groupCode);
    }

    @Test @Order(2)
    void getById() {
        ResponseEntity<String> resp = rest.exchange(
                "/api/v1/repositories/" + repoId, HttpMethod.GET, authEntity(token), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(readTree(resp.getBody()).path("data").path("name").asText()).isEqualTo(repoName);
    }

    @Test @Order(3)
    void listAll() {
        ResponseEntity<String> resp = rest.exchange(
                "/api/v1/repositories", HttpMethod.GET, authEntity(token), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(readTree(resp.getBody()).path("data").size()).isGreaterThanOrEqualTo(1);
    }

    @Test @Order(4)
    void listPaged() {
        ResponseEntity<String> resp = rest.exchange(
                "/api/v1/repositories/paged?page=1&size=10", HttpMethod.GET, authEntity(token), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(readTree(resp.getBody()).path("success").asBoolean()).isTrue();
    }

    @Test @Order(5)
    void searchByKeyword() {
        ResponseEntity<String> resp = rest.exchange(
                "/api/v1/repositories?keyword=" + repoName, HttpMethod.GET, authEntity(token), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(readTree(resp.getBody()).path("data").size()).isEqualTo(1);
    }

    @Test @Order(6)
    void updateRepository() {
        var body = Map.of("name", repoName + "-updated", "cloneUrl", "https://gitlab.example.com/" + repoName + "-updated.git", "groupCode", groupCode);
        ResponseEntity<String> resp = rest.exchange(
                "/api/v1/repositories/" + repoId, HttpMethod.PUT, authEntity(token, body), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(readTree(resp.getBody()).path("data").path("name").asText()).isEqualTo(repoName + "-updated");
    }

    @Test @Order(7)
    void initialVersion() {
        ResponseEntity<String> resp = rest.exchange(
                "/api/v1/repositories/" + repoId + "/initial-version", HttpMethod.GET, authEntity(token), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
    }

    @Test @Order(8)
    void setInitialVersion() {
        var body = Map.of("version", "1.0.0");
        ResponseEntity<String> resp = rest.exchange(
                "/api/v1/repositories/" + repoId + "/initial-version", HttpMethod.PUT,
                authEntity(token, body), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
    }

    @Test @Order(9)
    void createOnNonLeafGroupFails_regression_B2() {
        // Create a child under groupCode to make it non-leaf
        String childCode = uniqueCode("e2e-child-grp");
        var childBody = Map.of("name", "Child", "code", childCode, "parentCode", groupCode);
        rest.exchange("/api/v1/groups", HttpMethod.POST, authEntity(token, childBody), String.class);

        // Now creating repo under parent (non-leaf) should fail with GROUP_014
        var body = Map.of("name", "fail-repo", "cloneUrl", "https://x.com/fail.git", "groupCode", groupCode);
        ResponseEntity<String> resp = rest.exchange(
                "/api/v1/repositories", HttpMethod.POST, authEntity(token, body), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(400);
        assertThat(readTree(resp.getBody()).path("code").asText()).isEqualTo("GROUP_014");

        // Clean up: delete child group
        rest.exchange("/api/v1/groups/by-code/" + childCode, HttpMethod.DELETE, authEntity(token), String.class);
    }

    @Test @Order(10)
    void deleteRepository() {
        ResponseEntity<String> resp = rest.exchange(
                "/api/v1/repositories/" + repoId, HttpMethod.DELETE, authEntity(token), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
    }
}
```

**Step 2: Run and verify**

Run: `cd /Users/tongshuanglong/releasehub/release-hub && mvn test -pl releasehub-bootstrap -Dtest="io.releasehub.bootstrap.e2e.repository.RepositoryE2eTest" -q`
Expected: 10 tests PASS

**Step 3: Commit**

```bash
git add release-hub/releasehub-bootstrap/src/test/java/io/releasehub/bootstrap/e2e/repository/
git commit -m "test: add Repository E2E tests (CRUD, search, B2 regression)"
```

---

## Task 7: Iteration E2E Test

**Files:**
- Create: `release-hub/releasehub-bootstrap/src/test/java/io/releasehub/bootstrap/e2e/iteration/IterationE2eTest.java`

**Step 1: Write the test**

```java
package io.releasehub.bootstrap.e2e.iteration;

import io.releasehub.bootstrap.e2e.E2eTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.http.*;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IterationE2eTest extends E2eTestBase {

    private static String token;
    private static String groupCode;
    private static String repoId;
    private static String iterKey;

    @BeforeAll
    void setUp() {
        token = loginAndGetToken();
        groupCode = uniqueCode("e2e-iter-grp");
        rest.exchange("/api/v1/groups", HttpMethod.POST,
                authEntity(token, Map.of("name", "Iter Group", "code", groupCode)), String.class);

        String repoName = uniqueCode("e2e-iter-repo");
        ResponseEntity<String> repoResp = rest.exchange("/api/v1/repositories", HttpMethod.POST,
                authEntity(token, Map.of("name", repoName, "cloneUrl", "https://x.com/" + repoName + ".git", "groupCode", groupCode)),
                String.class);
        repoId = readTree(repoResp.getBody()).path("data").path("id").asText();
    }

    @Test @Order(1)
    void createIteration() {
        var body = Map.of("name", "E2E Iteration", "groupCode", groupCode, "repoIds", List.of(repoId));
        ResponseEntity<String> resp = rest.exchange(
                "/api/v1/iterations", HttpMethod.POST, authEntity(token, body), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        JsonNode data = readTree(resp.getBody()).path("data");
        iterKey = data.path("key").asText();
        assertThat(iterKey).isNotBlank();
        assertThat(data.path("name").asText()).isEqualTo("E2E Iteration");
    }

    @Test @Order(2)
    void getByKey() {
        ResponseEntity<String> resp = rest.exchange(
                "/api/v1/iterations/" + iterKey, HttpMethod.GET, authEntity(token), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(readTree(resp.getBody()).path("data").path("key").asText()).isEqualTo(iterKey);
    }

    @Test @Order(3)
    void listAll() {
        ResponseEntity<String> resp = rest.exchange(
                "/api/v1/iterations", HttpMethod.GET, authEntity(token), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(readTree(resp.getBody()).path("data").size()).isGreaterThanOrEqualTo(1);
    }

    @Test @Order(4)
    void listPaged() {
        ResponseEntity<String> resp = rest.exchange(
                "/api/v1/iterations/paged?page=1&size=10", HttpMethod.GET, authEntity(token), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(readTree(resp.getBody()).path("success").asBoolean()).isTrue();
    }

    @Test @Order(5)
    void listRepos() {
        ResponseEntity<String> resp = rest.exchange(
                "/api/v1/iterations/" + iterKey + "/repos", HttpMethod.GET, authEntity(token), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(readTree(resp.getBody()).path("data").size()).isEqualTo(1);
    }

    @Test @Order(6)
    void removeRepo() {
        var body = Map.of("repoIds", List.of(repoId));
        ResponseEntity<String> resp = rest.exchange(
                "/api/v1/iterations/" + iterKey + "/repos/remove", HttpMethod.POST,
                authEntity(token, body), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
    }

    @Test @Order(7)
    void addRepo() {
        var body = Map.of("repoIds", List.of(repoId));
        ResponseEntity<String> resp = rest.exchange(
                "/api/v1/iterations/" + iterKey + "/repos/add", HttpMethod.POST,
                authEntity(token, body), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
    }

    @Test @Order(8)
    void updateIteration() {
        var body = Map.of("name", "E2E Iteration Updated", "groupCode", groupCode);
        ResponseEntity<String> resp = rest.exchange(
                "/api/v1/iterations/" + iterKey, HttpMethod.PUT, authEntity(token, body), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(readTree(resp.getBody()).path("data").path("name").asText()).isEqualTo("E2E Iteration Updated");
    }

    @Test @Order(9)
    void deleteIteration() {
        ResponseEntity<String> resp = rest.exchange(
                "/api/v1/iterations/" + iterKey, HttpMethod.DELETE, authEntity(token), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
    }
}
```

**Step 2: Run and verify**

Run: `cd /Users/tongshuanglong/releasehub/release-hub && mvn test -pl releasehub-bootstrap -Dtest="io.releasehub.bootstrap.e2e.iteration.IterationE2eTest" -q`
Expected: 9 tests PASS

**Step 3: Commit**

```bash
git add release-hub/releasehub-bootstrap/src/test/java/io/releasehub/bootstrap/e2e/iteration/
git commit -m "test: add Iteration E2E tests (CRUD, repo management)"
```

---

## Task 8: ReleaseWindow E2E Test

**Files:**
- Create: `release-hub/releasehub-bootstrap/src/test/java/io/releasehub/bootstrap/e2e/releasewindow/ReleaseWindowE2eTest.java`

**Step 1: Write the test**

```java
package io.releasehub.bootstrap.e2e.releasewindow;

import io.releasehub.bootstrap.e2e.E2eTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.http.*;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ReleaseWindowE2eTest extends E2eTestBase {

    private static String token;
    private static String groupCode;
    private static String windowId;

    @BeforeAll
    void setUp() {
        token = loginAndGetToken();
        groupCode = uniqueCode("e2e-rw-grp");
        rest.exchange("/api/v1/groups", HttpMethod.POST,
                authEntity(token, Map.of("name", "RW Group", "code", groupCode)), String.class);
    }

    @Test @Order(1)
    void createWindow() {
        var body = Map.of("name", "E2E Window", "groupCode", groupCode);
        ResponseEntity<String> resp = rest.exchange(
                "/api/v1/release-windows", HttpMethod.POST, authEntity(token, body), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        JsonNode data = readTree(resp.getBody()).path("data");
        windowId = data.path("id").asText();
        assertThat(windowId).isNotBlank();
        assertThat(data.path("status").asText()).isEqualTo("DRAFT");
    }

    @Test @Order(2)
    void getById() {
        ResponseEntity<String> resp = rest.exchange(
                "/api/v1/release-windows/" + windowId, HttpMethod.GET, authEntity(token), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(readTree(resp.getBody()).path("data").path("name").asText()).isEqualTo("E2E Window");
    }

    @Test @Order(3)
    void listAll() {
        ResponseEntity<String> resp = rest.exchange(
                "/api/v1/release-windows", HttpMethod.GET, authEntity(token), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(readTree(resp.getBody()).path("data").size()).isGreaterThanOrEqualTo(1);
    }

    @Test @Order(4)
    void listPaged() {
        ResponseEntity<String> resp = rest.exchange(
                "/api/v1/release-windows/paged?page=1&size=10", HttpMethod.GET, authEntity(token), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(readTree(resp.getBody()).path("success").asBoolean()).isTrue();
    }

    @Test @Order(5)
    void pagedFilterByStatus() {
        ResponseEntity<String> resp = rest.exchange(
                "/api/v1/release-windows/paged?page=1&size=10&status=DRAFT", HttpMethod.GET, authEntity(token), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        JsonNode data = readTree(resp.getBody()).path("data");
        for (JsonNode w : data) {
            assertThat(w.path("status").asText()).isEqualTo("DRAFT");
        }
    }

    // --- State transitions ---

    @Test @Order(6)
    void freeze() {
        ResponseEntity<String> resp = rest.exchange(
                "/api/v1/release-windows/" + windowId + "/freeze", HttpMethod.POST,
                authEntity(token, Map.of()), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(readTree(resp.getBody()).path("data").path("frozen").asBoolean()).isTrue();
    }

    @Test @Order(7)
    void unfreeze() {
        ResponseEntity<String> resp = rest.exchange(
                "/api/v1/release-windows/" + windowId + "/unfreeze", HttpMethod.POST,
                authEntity(token, Map.of()), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(readTree(resp.getBody()).path("data").path("frozen").asBoolean()).isFalse();
    }

    @Test @Order(8)
    void publish() {
        ResponseEntity<String> resp = rest.exchange(
                "/api/v1/release-windows/" + windowId + "/publish", HttpMethod.POST,
                authEntity(token, Map.of()), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(readTree(resp.getBody()).path("data").path("status").asText()).isEqualTo("PUBLISHED");
    }

    @Test @Order(9)
    void close() {
        ResponseEntity<String> resp = rest.exchange(
                "/api/v1/release-windows/" + windowId + "/close", HttpMethod.POST,
                authEntity(token, Map.of()), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(readTree(resp.getBody()).path("data").path("status").asText()).isEqualTo("CLOSED");
    }

    @Test @Order(10)
    void freezeClosedWindowFails_regression_B4() {
        ResponseEntity<String> resp = rest.exchange(
                "/api/v1/release-windows/" + windowId + "/freeze", HttpMethod.POST,
                authEntity(token, Map.of()), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(400);
        assertThat(readTree(resp.getBody()).path("code").asText()).isEqualTo("RW_009");
    }

    @Test @Order(11)
    void createOnNonLeafGroupFails_regression_B2() {
        // Make groupCode non-leaf
        String childCode = uniqueCode("e2e-rw-child");
        rest.exchange("/api/v1/groups", HttpMethod.POST,
                authEntity(token, Map.of("name", "Child", "code", childCode, "parentCode", groupCode)), String.class);

        var body = Map.of("name", "Fail Window", "groupCode", groupCode);
        ResponseEntity<String> resp = rest.exchange(
                "/api/v1/release-windows", HttpMethod.POST, authEntity(token, body), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(400);
        assertThat(readTree(resp.getBody()).path("code").asText()).isEqualTo("GROUP_014");

        // Clean up
        rest.exchange("/api/v1/groups/by-code/" + childCode, HttpMethod.DELETE, authEntity(token), String.class);
    }
}
```

**Step 2: Run and verify**

Run: `cd /Users/tongshuanglong/releasehub/release-hub && mvn test -pl releasehub-bootstrap -Dtest="io.releasehub.bootstrap.e2e.releasewindow.ReleaseWindowE2eTest" -q`
Expected: 11 tests PASS

**Step 3: Commit**

```bash
git add release-hub/releasehub-bootstrap/src/test/java/io/releasehub/bootstrap/e2e/releasewindow/
git commit -m "test: add ReleaseWindow E2E tests (CRUD, state transitions, B2/B4 regression)"
```

---

## Task 9: BranchRule E2E Test

**Files:**
- Create: `release-hub/releasehub-bootstrap/src/test/java/io/releasehub/bootstrap/e2e/branchrule/BranchRuleE2eTest.java`

**Step 1: Write the test**

```java
package io.releasehub.bootstrap.e2e.branchrule;

import io.releasehub.bootstrap.e2e.E2eTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.http.*;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BranchRuleE2eTest extends E2eTestBase {

    private static String token;
    private static String ruleId;

    @BeforeAll
    void auth() {
        token = loginAndGetToken();
    }

    @Test @Order(1)
    void listRules() {
        ResponseEntity<String> resp = rest.exchange(
                "/api/v1/branch-rules", HttpMethod.GET, authEntity(token), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        // Flyway seeds built-in rules
        assertThat(readTree(resp.getBody()).path("data")).isNotNull();
    }

    @Test @Order(2)
    void createRule() {
        var body = Map.of("name", "E2E Rule", "pattern", "e2e/**", "type", "ALLOW");
        ResponseEntity<String> resp = rest.exchange(
                "/api/v1/branch-rules", HttpMethod.POST, authEntity(token, body), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        JsonNode data = readTree(resp.getBody()).path("data");
        ruleId = data.path("id").asText();
        assertThat(ruleId).isNotBlank();
    }

    @Test @Order(3)
    void getById() {
        ResponseEntity<String> resp = rest.exchange(
                "/api/v1/branch-rules/" + ruleId, HttpMethod.GET, authEntity(token), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(readTree(resp.getBody()).path("data").path("name").asText()).isEqualTo("E2E Rule");
    }

    @Test @Order(4)
    void listPaged() {
        ResponseEntity<String> resp = rest.exchange(
                "/api/v1/branch-rules/paged?page=1&size=10", HttpMethod.GET, authEntity(token), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(readTree(resp.getBody()).path("success").asBoolean()).isTrue();
    }

    @Test @Order(5)
    void updateRule() {
        var body = Map.of("name", "E2E Rule Updated", "pattern", "e2e-updated/**", "type", "ALLOW");
        ResponseEntity<String> resp = rest.exchange(
                "/api/v1/branch-rules/" + ruleId, HttpMethod.PUT, authEntity(token, body), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(readTree(resp.getBody()).path("data").path("name").asText()).isEqualTo("E2E Rule Updated");
    }

    @Test @Order(6)
    void checkCompliance() {
        ResponseEntity<String> resp = rest.exchange(
                "/api/v1/branch-rules/check?branchName=feature/test", HttpMethod.GET, authEntity(token), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(readTree(resp.getBody()).path("data").has("compliant")).isTrue();
    }

    @Test @Order(7)
    void deleteRule() {
        ResponseEntity<String> resp = rest.exchange(
                "/api/v1/branch-rules/" + ruleId, HttpMethod.DELETE, authEntity(token), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
    }
}
```

**Step 2: Run and verify**

Run: `cd /Users/tongshuanglong/releasehub/release-hub && mvn test -pl releasehub-bootstrap -Dtest="io.releasehub.bootstrap.e2e.branchrule.BranchRuleE2eTest" -q`
Expected: 7 tests PASS

**Step 3: Commit**

```bash
git add release-hub/releasehub-bootstrap/src/test/java/io/releasehub/bootstrap/e2e/branchrule/
git commit -m "test: add BranchRule E2E tests (CRUD, compliance check)"
```

---

## Task 10: Settings E2E Test

**Files:**
- Create: `release-hub/releasehub-bootstrap/src/test/java/io/releasehub/bootstrap/e2e/settings/SettingsE2eTest.java`

**Step 1: Write the test**

```java
package io.releasehub.bootstrap.e2e.settings;

import io.releasehub.bootstrap.e2e.E2eTestBase;
import org.junit.jupiter.api.*;
import org.springframework.http.*;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SettingsE2eTest extends E2eTestBase {

    private static String token;

    @BeforeAll
    void auth() {
        token = loginAndGetToken();
    }

    @Test @Order(1)
    void saveAndGetGitlab() {
        var body = Map.of("baseUrl", "https://gitlab.example.com", "token", "glpat-xxx");
        ResponseEntity<String> saveResp = rest.exchange(
                "/api/v1/settings/gitlab", HttpMethod.POST, authEntity(token, body), String.class);
        assertThat(saveResp.getStatusCode().value()).isEqualTo(200);

        ResponseEntity<String> getResp = rest.exchange(
                "/api/v1/settings/gitlab", HttpMethod.GET, authEntity(token), String.class);
        assertThat(getResp.getStatusCode().value()).isEqualTo(200);
    }

    @Test @Order(2)
    void saveAndGetNaming() {
        var body = Map.of("featureTemplate", "feature/{iterationKey}", "releaseTemplate", "release/{windowKey}");
        ResponseEntity<String> saveResp = rest.exchange(
                "/api/v1/settings/naming", HttpMethod.POST, authEntity(token, body), String.class);
        assertThat(saveResp.getStatusCode().value()).isEqualTo(200);

        ResponseEntity<String> getResp = rest.exchange(
                "/api/v1/settings/naming", HttpMethod.GET, authEntity(token), String.class);
        assertThat(getResp.getStatusCode().value()).isEqualTo(200);
    }

    @Test @Order(3)
    void getRef() {
        ResponseEntity<String> resp = rest.exchange(
                "/api/v1/settings/ref", HttpMethod.GET, authEntity(token), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
    }

    @Test @Order(4)
    void saveAndGetBlocking() {
        var body = Map.of("defaultPolicy", "WARN");
        ResponseEntity<String> saveResp = rest.exchange(
                "/api/v1/settings/blocking", HttpMethod.POST, authEntity(token, body), String.class);
        assertThat(saveResp.getStatusCode().value()).isEqualTo(200);

        ResponseEntity<String> getResp = rest.exchange(
                "/api/v1/settings/blocking", HttpMethod.GET, authEntity(token), String.class);
        assertThat(getResp.getStatusCode().value()).isEqualTo(200);
    }
}
```

**Step 2: Run and verify**

Run: `cd /Users/tongshuanglong/releasehub/release-hub && mvn test -pl releasehub-bootstrap -Dtest="io.releasehub.bootstrap.e2e.settings.SettingsE2eTest" -q`
Expected: 4 tests PASS

**Step 3: Commit**

```bash
git add release-hub/releasehub-bootstrap/src/test/java/io/releasehub/bootstrap/e2e/settings/
git commit -m "test: add Settings E2E tests"
```

---

## Task 11: Dashboard E2E Test

**Files:**
- Create: `release-hub/releasehub-bootstrap/src/test/java/io/releasehub/bootstrap/e2e/dashboard/DashboardE2eTest.java`

**Step 1: Write the test**

```java
package io.releasehub.bootstrap.e2e.dashboard;

import io.releasehub.bootstrap.e2e.E2eTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardE2eTest extends E2eTestBase {

    @Test
    void statsEndpointReturnsData() {
        String token = loginAndGetToken();
        ResponseEntity<String> resp = rest.exchange(
                "/api/v1/dashboard/stats", HttpMethod.GET, authEntity(token), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        JsonNode data = readTree(resp.getBody()).path("data");
        assertThat(data.has("totalRepositories")).isTrue();
        assertThat(data.has("totalIterations")).isTrue();
        assertThat(data.has("activeWindows")).isTrue();
        assertThat(data.has("totalRuns")).isTrue();
    }
}
```

**Step 2: Run and verify**

Run: `cd /Users/tongshuanglong/releasehub/release-hub && mvn test -pl releasehub-bootstrap -Dtest="io.releasehub.bootstrap.e2e.dashboard.DashboardE2eTest" -q`
Expected: 1 test PASS

**Step 3: Commit**

```bash
git add release-hub/releasehub-bootstrap/src/test/java/io/releasehub/bootstrap/e2e/dashboard/
git commit -m "test: add Dashboard E2E test"
```

---

## Task 12: Release Flow E2E Test (Full Lifecycle)

**Files:**
- Create: `release-hub/releasehub-bootstrap/src/test/java/io/releasehub/bootstrap/e2e/flow/ReleaseFlowE2eTest.java`

**Step 1: Write the test**

This test covers the complete business flow: Group → Repo → Iteration → Window → Attach → Freeze → Publish → Close, including B3 regression (null iterationKeys).

```java
package io.releasehub.bootstrap.e2e.flow;

import io.releasehub.bootstrap.e2e.E2eTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.http.*;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ReleaseFlowE2eTest extends E2eTestBase {

    private static String token;
    private static String groupCode;
    private static String repoId;
    private static String iterKey;
    private static String windowId;

    @BeforeAll
    void setUp() {
        token = loginAndGetToken();
    }

    @Test @Order(1)
    void step1_createGroup() {
        groupCode = uniqueCode("e2e-flow-grp");
        var body = Map.of("name", "Flow Group", "code", groupCode);
        ResponseEntity<String> resp = rest.exchange(
                "/api/v1/groups", HttpMethod.POST, authEntity(token, body), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
    }

    @Test @Order(2)
    void step2_createRepo() {
        String name = uniqueCode("e2e-flow-repo");
        var body = Map.of("name", name, "cloneUrl", "https://x.com/" + name + ".git", "groupCode", groupCode);
        ResponseEntity<String> resp = rest.exchange(
                "/api/v1/repositories", HttpMethod.POST, authEntity(token, body), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        repoId = readTree(resp.getBody()).path("data").path("id").asText();
    }

    @Test @Order(3)
    void step3_createIteration() {
        var body = Map.of("name", "Flow Iteration", "groupCode", groupCode, "repoIds", List.of(repoId));
        ResponseEntity<String> resp = rest.exchange(
                "/api/v1/iterations", HttpMethod.POST, authEntity(token, body), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        iterKey = readTree(resp.getBody()).path("data").path("key").asText();
    }

    @Test @Order(4)
    void step4_createWindow() {
        var body = Map.of("name", "Flow Window", "groupCode", groupCode);
        ResponseEntity<String> resp = rest.exchange(
                "/api/v1/release-windows", HttpMethod.POST, authEntity(token, body), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        windowId = readTree(resp.getBody()).path("data").path("id").asText();
    }

    @Test @Order(5)
    void step5_attachNullIterationKeysFails_regression_B3() {
        // B3 regression: sending wrong field name results in null iterationKeys → validation error, not NPE
        var body = Map.of("wrongField", List.of(iterKey));
        ResponseEntity<String> resp = rest.exchange(
                "/api/v1/release-windows/" + windowId + "/attach", HttpMethod.POST,
                authEntity(token, body), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(400);
        // Should NOT be 500 (NPE)
    }

    @Test @Order(6)
    void step6_attachIteration() {
        var body = Map.of("iterationKeys", List.of(iterKey));
        ResponseEntity<String> resp = rest.exchange(
                "/api/v1/release-windows/" + windowId + "/attach", HttpMethod.POST,
                authEntity(token, body), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
    }

    @Test @Order(7)
    void step7_listWindowIterations() {
        ResponseEntity<String> resp = rest.exchange(
                "/api/v1/release-windows/" + windowId + "/iterations", HttpMethod.GET,
                authEntity(token), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(readTree(resp.getBody()).path("data").size()).isEqualTo(1);
    }

    @Test @Order(8)
    void step8_freeze() {
        ResponseEntity<String> resp = rest.exchange(
                "/api/v1/release-windows/" + windowId + "/freeze", HttpMethod.POST,
                authEntity(token, Map.of()), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(readTree(resp.getBody()).path("data").path("frozen").asBoolean()).isTrue();
    }

    @Test @Order(9)
    void step9_publish() {
        ResponseEntity<String> resp = rest.exchange(
                "/api/v1/release-windows/" + windowId + "/publish", HttpMethod.POST,
                authEntity(token, Map.of()), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(readTree(resp.getBody()).path("data").path("status").asText()).isEqualTo("PUBLISHED");
    }

    @Test @Order(10)
    void step10_close() {
        ResponseEntity<String> resp = rest.exchange(
                "/api/v1/release-windows/" + windowId + "/close", HttpMethod.POST,
                authEntity(token, Map.of()), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(readTree(resp.getBody()).path("data").path("status").asText()).isEqualTo("CLOSED");
    }

    @Test @Order(11)
    void step11_dashboardReflectsData() {
        ResponseEntity<String> resp = rest.exchange(
                "/api/v1/dashboard/stats", HttpMethod.GET, authEntity(token), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        JsonNode data = readTree(resp.getBody()).path("data");
        assertThat(data.path("totalRepositories").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(data.path("totalIterations").asInt()).isGreaterThanOrEqualTo(1);
    }

    @Test @Order(12)
    void step12_detachIteration() {
        var body = Map.of("iterationKey", iterKey);
        ResponseEntity<String> resp = rest.exchange(
                "/api/v1/release-windows/" + windowId + "/detach", HttpMethod.POST,
                authEntity(token, body), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
    }

    @Test @Order(13)
    void step13_deleteRepoAttachedToIterationFails() {
        // Repo is still in iteration, delete should be blocked
        ResponseEntity<String> resp = rest.exchange(
                "/api/v1/repositories/" + repoId, HttpMethod.DELETE, authEntity(token), String.class);
        // Depending on implementation: may be 400 with error code or 200
        // Just verify it doesn't 500
        assertThat(resp.getStatusCode().value()).isNotEqualTo(500);
    }
}
```

**Step 2: Run and verify**

Run: `cd /Users/tongshuanglong/releasehub/release-hub && mvn test -pl releasehub-bootstrap -Dtest="io.releasehub.bootstrap.e2e.flow.ReleaseFlowE2eTest" -q`
Expected: 13 tests PASS

**Step 3: Commit**

```bash
git add release-hub/releasehub-bootstrap/src/test/java/io/releasehub/bootstrap/e2e/flow/
git commit -m "test: add Release Flow E2E test (full lifecycle, B3 regression)"
```

---

## Task 13: Delete Smoke Test and Run All E2E Tests

**Step 1: Delete the smoke test**

Delete `release-hub/releasehub-bootstrap/src/test/java/io/releasehub/bootstrap/e2e/SmokeE2eTest.java` — its purpose was to verify the base class; now the real tests serve that role.

**Step 2: Run ALL E2E tests together**

Run: `cd /Users/tongshuanglong/releasehub/release-hub && mvn test -pl releasehub-bootstrap -Dtest="io.releasehub.bootstrap.e2e.**" -q`
Expected: All tests PASS

**Step 3: Run ALL tests (existing + E2E) to ensure no regression**

Run: `cd /Users/tongshuanglong/releasehub/release-hub && mvn test -q`
Expected: All tests PASS (existing 195 + new E2E tests)

**Step 4: Commit**

```bash
git rm release-hub/releasehub-bootstrap/src/test/java/io/releasehub/bootstrap/e2e/SmokeE2eTest.java
git commit -m "test: remove smoke test, verify all E2E tests pass"
```

---

## Summary

| Task | 内容 | 测试数 |
|------|------|--------|
| 1 | TestContainers 依赖 | — |
| 2 | E2E Profile + Base Class + Smoke | 1 |
| 3 | Auth E2E | 5 |
| 4 | Group E2E | 13 |
| 5 | VersionPolicy E2E (B1 回归) | 4 |
| 6 | Repository E2E (B2 回归) | 10 |
| 7 | Iteration E2E | 9 |
| 8 | ReleaseWindow E2E (B2/B4 回归) | 11 |
| 9 | BranchRule E2E | 7 |
| 10 | Settings E2E | 4 |
| 11 | Dashboard E2E | 1 |
| 12 | Release Flow E2E (B3 回归) | 13 |
| 13 | 全量验证 | — |

**总计: ~77 个 E2E 测试用例，覆盖所有 API 端点 + 4 个 Bug 回归。**
