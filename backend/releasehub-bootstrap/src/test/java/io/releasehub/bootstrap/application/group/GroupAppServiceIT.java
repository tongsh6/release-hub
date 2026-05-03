package io.releasehub.bootstrap.application.group;

import io.releasehub.application.group.GroupAppService;
import io.releasehub.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GroupAppServiceIT {

    @Autowired
    private GroupAppService svc;

    private static String uniqueCode(String suffix) {
        return "TC-" + System.currentTimeMillis() + "-" + suffix;
    }

    @Test
    void childrenAndTopLevelAndTree_ShouldWork() {
        var aCode = uniqueCode("A");
        var bCode = uniqueCode("B");
        var cCode = uniqueCode("C");
        var dCode = uniqueCode("D");

        var a = svc.create("A", aCode, null);
        var b = svc.create("B", bCode, aCode);
        var c = svc.create("C", cCode, bCode);
        var d = svc.create("D", dCode, null);

        var top = svc.topLevel();
        assertTrue(top.size() >= 2, "top level should have at least 2 groups");
        assertTrue(top.stream().anyMatch(g -> g.getCode().equals(aCode)));
        assertTrue(top.stream().anyMatch(g -> g.getCode().equals(dCode)));

        var childrenA = svc.children(aCode);
        assertEquals(1, childrenA.size());
        assertEquals(bCode, childrenA.get(0).getCode());

        var tree = svc.tree();
        assertTrue(tree.size() >= 2);
        var aNodeOpt = tree.stream().filter(n -> n.getCode().equals(aCode)).findFirst();
        assertTrue(aNodeOpt.isPresent());
        var aNode = aNodeOpt.get();
        assertEquals(1, aNode.getChildren().size());
        assertEquals(bCode, aNode.getChildren().get(0).getCode());
        assertEquals(1, aNode.getChildren().get(0).getChildren().size());
        assertEquals(cCode, aNode.getChildren().get(0).getChildren().get(0).getCode());
    }

    @Test
    void getByCode_ShouldReturnGroup_WhenExists() {
        var parentCode = uniqueCode("P");
        var childCode = uniqueCode("C");
        svc.create("Parent", parentCode, null);
        svc.create("Child", childCode, parentCode);
        var got = svc.getByCode(childCode);
        assertEquals("Child", got.getName());
        assertEquals(parentCode, got.getParentCode());
    }

    @Test
    void deleteByCode_ShouldThrow_WhenHasChildren() {
        var parentCode = uniqueCode("P");
        var childCode = uniqueCode("C");
        svc.create("Parent", parentCode, null);
        svc.create("Child", childCode, parentCode);
        var ex = assertThrows(BusinessException.class, () -> svc.deleteByCode(parentCode));
        assertEquals("GROUP_008", ex.getCode());
    }

    @Test
    void create_ShouldAutoGenerateCode_WhenCodeIsNull() {
        var g1 = svc.create("Group 1", null, null);
        assertNotNull(g1.getCode());
        assertFalse(g1.getCode().isBlank(), "auto-generated code should not be blank");

        var g2 = svc.create("Group 2", null, null);
        assertNotNull(g2.getCode());
        assertFalse(g2.getCode().isBlank());
        assertNotEquals(g1.getCode(), g2.getCode(), "auto-generated codes should be unique");
    }

    @Test
    void create_ShouldAutoGenerateCode_WhenCodeIsBlank() {
        var g1 = svc.create("Group 1", "", null);
        assertNotNull(g1.getCode());
        assertFalse(g1.getCode().isBlank());

        var g2 = svc.create("Group 2", "  ", null);
        assertNotNull(g2.getCode());
        assertFalse(g2.getCode().isBlank());
        assertNotEquals(g1.getCode(), g2.getCode());
    }

    @Test
    void create_ShouldAutoGenerateChildCode_WhenParentExists() {
        var parentCode = uniqueCode("P");
        var parent = svc.create("Parent", parentCode, null);

        var child1 = svc.create("Child 1", null, parentCode);
        assertNotNull(child1.getCode());
        assertTrue(child1.getCode().startsWith(parentCode),
                "child code should start with parent code");

        var child2 = svc.create("Child 2", null, parentCode);
        assertNotNull(child2.getCode());
        assertTrue(child2.getCode().startsWith(parentCode));
        assertNotEquals(child1.getCode(), child2.getCode());
    }

    @Test
    void create_ShouldUseProvidedCode_WhenCodeIsNotBlank() {
        var customCode = uniqueCode("CUSTOM");
        var g1 = svc.create("Custom Group", customCode, null);
        assertEquals(customCode, g1.getCode());

        // 自动生成的 code 不受自定义 code 影响，且唯一
        var g2 = svc.create("Auto Group", null, null);
        assertNotNull(g2.getCode());
        assertNotEquals(customCode, g2.getCode());
    }
}
