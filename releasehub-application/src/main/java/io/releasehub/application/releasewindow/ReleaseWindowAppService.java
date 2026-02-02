package io.releasehub.application.releasewindow;

import io.releasehub.application.group.GroupPort;
import io.releasehub.application.release.ReleaseRunService;
import io.releasehub.application.window.WindowIterationPort;
import io.releasehub.common.exception.BusinessException;
import io.releasehub.common.exception.NotFoundException;
import io.releasehub.common.exception.ValidationException;
import io.releasehub.common.paging.PageResult;
import io.releasehub.domain.releasewindow.ReleaseWindow;
import io.releasehub.domain.releasewindow.ReleaseWindowId;
import io.releasehub.domain.run.Run;
import io.releasehub.domain.window.WindowIteration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReleaseWindowAppService {

    private static final DateTimeFormatter KEY_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private final ReleaseWindowPort releaseWindowPort;
    private final WindowIterationPort windowIterationPort;
    private final ReleaseRunService releaseRunService;
    private final GroupPort groupPort;
    private final Clock clock = Clock.systemUTC();

    @Transactional
    public ReleaseWindowView create(String name, String description, Instant plannedReleaseAt, String groupCode) {
        String windowKey = generateWindowKey();
        ensureLeafGroup(groupCode);
        ReleaseWindow rw = ReleaseWindow.createDraft(windowKey, name, description, plannedReleaseAt, groupCode, Instant.now(clock));
        releaseWindowPort.save(rw);
        return ReleaseWindowView.from(rw);
    }

    /**
     * 自动生成发布窗口标识
     * 格式: RW-yyyyMMdd-xxxx (xxxx 为随机4位)
     */
    private String generateWindowKey() {
        String datePart = KEY_DATE_FORMAT.format(Instant.now(clock).atZone(ZoneId.systemDefault()));
        String randomPart = UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        return "RW-" + datePart + "-" + randomPart;
    }

    private void ensureLeafGroup(String groupCode) {
        if (groupCode == null || groupCode.isBlank()) {
            throw ValidationException.groupCodeRequired();
        }
        groupPort.findByCode(groupCode)
                 .orElseThrow(() -> NotFoundException.groupCode(groupCode));
        if (groupPort.countChildren(groupCode) > 0) {
            throw BusinessException.groupHasChildren(groupCode);
        }
    }

    public ReleaseWindowView get(String id) {
        ReleaseWindow rw = releaseWindowPort.findById(ReleaseWindowId.of(id))
                                            .orElseThrow(() -> NotFoundException.releaseWindow(id));
        return ReleaseWindowView.from(rw);
    }

    public List<ReleaseWindowView> list() {
        return releaseWindowPort.findAll().stream()
                                .map(ReleaseWindowView::from)
                                .toList();
    }

    public PageResult<ReleaseWindowView> listPaged(String name, int page, int size) {
        PageResult<ReleaseWindow> result = releaseWindowPort.findPaged(name, page, size);
        List<ReleaseWindowView> views = result.items().stream()
                                              .map(ReleaseWindowView::from)
                                              .toList();
        return new PageResult<>(views, result.total());
    }

    @Transactional
    public ReleaseWindowView publish(String id) {
        ReleaseWindow rw = findById(id);

        // 验证关联迭代
        List<WindowIteration> iterations = windowIterationPort.listByWindow(ReleaseWindowId.of(id));
        if (iterations.isEmpty()) {
            throw BusinessException.rwNoIterations(id);
        }

        rw.publish(Instant.now(clock));
        releaseWindowPort.save(rw);

        // 创建发布运行任务并异步执行
        try {
            Run run = releaseRunService.createReleaseRun(id, rw.getWindowKey(), "system"); // TODO: 获取当前用户
            releaseRunService.executeRunAsync(run.getId().value());
            log.info("Created and started release run {} for window {}", run.getId().value(), id);
        } catch (Exception e) {
            log.error("Failed to create release run for window {}: {}", id, e.getMessage());
            // 发布成功但运行任务创建失败时，不回滚发布状态
        }

        return ReleaseWindowView.from(rw);
    }

    private ReleaseWindow findById(String id) {
        return releaseWindowPort.findById(ReleaseWindowId.of(id))
                                .orElseThrow(() -> NotFoundException.releaseWindow(id));
    }

    @Transactional
    public ReleaseWindowView freeze(String id) {
        ReleaseWindow rw = findById(id);
        rw.freeze(Instant.now(clock));
        releaseWindowPort.save(rw);
        return ReleaseWindowView.from(rw);
    }

    @Transactional
    public ReleaseWindowView unfreeze(String id) {
        ReleaseWindow rw = findById(id);
        rw.unfreeze(Instant.now(clock));
        releaseWindowPort.save(rw);
        return ReleaseWindowView.from(rw);
    }

    @Transactional
    public ReleaseWindowView close(String id) {
        ReleaseWindow rw = findById(id);
        rw.close(Instant.now(clock));
        releaseWindowPort.save(rw);
        return ReleaseWindowView.from(rw);
    }
}
