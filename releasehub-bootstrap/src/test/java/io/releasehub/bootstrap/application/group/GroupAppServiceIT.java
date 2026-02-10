package io.releasehub.bootstrap.application.group;

import io.releasehub.application.group.GroupAppService;
import io.releasehub.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("unitTest")
@Transactional
class GroupAppServiceIT {

    @Autowired
    private GroupAppService svc;

    @Test
    void childrenAndTopLevelAndTree_ShouldWork() {
        var a = svc.create("A", "A", null);
        var b = svc.create("B", "B", "A");
        var c = svc.create("C", "C", "B");
        var d = svc.create("D", "D", null);

        var top = svc.topLevel();
        assertEquals(2, top.size());
        assertTrue(top.stream().anyMatch(g -> g.getCode().equals("A")));
        assertTrue(top.stream().anyMatch(g -> g.getCode().equals("D")));

        var childrenA = svc.children("A");
        assertEquals(1, childrenA.size());
        assertEquals("B", childrenA.get(0).getCode());

        var tree = svc.tree();
        assertEquals(2, tree.size());
        var aNodeOpt = tree.stream().filter(n -> n.getCode().equals("A")).findFirst();
        assertTrue(aNodeOpt.isPresent());
        var aNode = aNodeOpt.get();
        assertEquals(1, aNode.getChildren().size());
        assertEquals("B", aNode.getChildren().get(0).getCode());
        assertEquals(1, aNode.getChildren().get(0).getChildren().size());
        assertEquals("C", aNode.getChildren().get(0).getChildren().get(0).getCode());
    }

    @Test
    void getByCode_ShouldReturnGroup_WhenExists() {
        var a = svc.create("A", "001", null);
        var b = svc.create("B", "001001", "001");
        var got = svc.getByCode("001001");
        assertEquals("B", got.getName());
        assertEquals("001", got.getParentCode());
    }

    @Test
    void deleteByCode_ShouldThrow_WhenHasChildren() {
        var a = svc.create("A", "001", null);
        var b = svc.create("B", "001001", "001");
        var ex = assertThrows(BusinessException.class, () -> svc.deleteByCode("001"));
        assertEquals("GROUP_008", ex.getCode());
    }

    @Test
    void create_ShouldAutoGenerateCode_WhenCodeIsNull() {
        // 创建顶级分组，code 为 null
        var g1 = svc.create("Group 1", null, null);
        assertEquals("001", g1.getCode());

        var g2 = svc.create("Group 2", null, null);
        assertEquals("002", g2.getCode());

        var g3 = svc.create("Group 3", null, null);
        assertEquals("003", g3.getCode());
    }

    @Test
    void create_ShouldAutoGenerateCode_WhenCodeIsBlank() {
        // 创建顶级分组，code 为空字符串
        var g1 = svc.create("Group 1", "", null);
        assertEquals("001", g1.getCode());

        var g2 = svc.create("Group 2", "  ", null);
        assertEquals("002", g2.getCode());
    }

    @Test
    void create_ShouldAutoGenerateChildCode_WhenParentExists() {
        // 创建父分组
        var parent = svc.create("Parent", null, null);
        assertEquals("001", parent.getCode());

        // 创建子分组，code 为 null
        var child1 = svc.create("Child 1", null, "001");
        assertEquals("001001", child1.getCode());

        var child2 = svc.create("Child 2", null, "001");
        assertEquals("001002", child2.getCode());

        // 创建孙子分组
        var grandChild = svc.create("Grandchild", null, "001001");
        assertEquals("001001001", grandChild.getCode());
    }

    @Test
    void create_ShouldUseProvidedCode_WhenCodeIsNotBlank() {
        // 提供自定义 code
        var g1 = svc.create("Custom Group", "CUSTOM001", null);
        assertEquals("CUSTOM001", g1.getCode());

        // 自动生成的 code 不受自定义 code 影响
        var g2 = svc.create("Auto Group", null, null);
        assertEquals("001", g2.getCode());
    }
}
