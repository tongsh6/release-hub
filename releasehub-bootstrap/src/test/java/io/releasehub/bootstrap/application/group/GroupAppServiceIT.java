package io.releasehub.bootstrap.application.group;

import io.releasehub.application.group.GroupAppService;
import io.releasehub.common.exception.BizException;
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
        var ex = assertThrows(BizException.class, () -> svc.deleteByCode("001"));
        assertEquals("GROUP_DELETE_HAS_CHILDREN", ex.getCode());
    }
}
