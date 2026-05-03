package io.releasehub.bootstrap.pact;

import au.com.dius.pact.provider.junit5.HttpTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;
import io.releasehub.application.group.GroupAppService;
import io.releasehub.application.releasewindow.ReleaseWindowAppService;
import io.releasehub.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;

/**
 * Consolidated Pact Provider Verification.
 */
@Provider("releasehub-backend")
@PactFolder("../../frontend/pacts")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = "security.pact-verification=true")
class AllPactIT {

    @LocalServerPort
    int port;

    @Autowired
    private GroupAppService groupAppService;

    @Autowired
    private ReleaseWindowAppService releaseWindowAppService;

    @BeforeEach
    void setUp(PactVerificationContext context) {
        context.setTarget(new HttpTestTarget("localhost", port));
    }

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void verifyPact(PactVerificationContext context) {
        context.verifyInteraction();
    }

    private void createGroupIfAbsent(String name, String code, String parentCode) {
        try {
            groupAppService.create(name, code, parentCode);
        } catch (BusinessException e) {
            // group already exists from a previous @State — ignore
        }
    }

    // -- Auth API --

    @State("admin user exists")
    void adminUserExists() {
        // Seed data ensures admin/admin user exists
    }

    // -- Groups Tree API --

    @State("groups exist with tree structure")
    void groupsExistWithTreeStructure() {
        createGroupIfAbsent("Parent Group", "parent", null);
        createGroupIfAbsent("Child Group", "child", "parent");
    }

    // -- Release Window APIs — use a leaf group (no children) --

    @State("a group exists for release window creation")
    void aGroupExistsForReleaseWindowCreation() {
        createGroupIfAbsent("Root Group", "root", null);
    }

    @State("a release window exists")
    void aReleaseWindowExists() {
        createGroupIfAbsent("Root Group", "root", null);
        releaseWindowAppService.create(
            "Q1 2026 Release", "Q1 release window",
            Instant.parse("2026-03-31T00:00:00Z"), "root");
    }

    @State("release windows exist")
    void releaseWindowsExist() {
        createGroupIfAbsent("Root Group", "root", null);
        releaseWindowAppService.create(
            "Q1 2026 Release", "Q1 release window",
            Instant.parse("2026-03-31T00:00:00Z"), "root");
    }
}
