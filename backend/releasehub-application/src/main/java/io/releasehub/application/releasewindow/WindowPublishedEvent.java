package io.releasehub.application.releasewindow;

/**
 * 发布窗口已发布事件。由 publish() 发布，供监听器在事务提交后触发后置编排。
 */
public class WindowPublishedEvent {
    private final String windowId;

    public WindowPublishedEvent(String windowId) {
        this.windowId = windowId;
    }

    public String getWindowId() {
        return windowId;
    }
}
