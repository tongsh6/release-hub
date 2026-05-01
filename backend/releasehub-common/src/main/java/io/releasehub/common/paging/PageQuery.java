package io.releasehub.common.paging;

public class PageQuery {
    private int page = 0;
    private int size = 20;

    public PageQuery() {}
    public PageQuery(int page, int size) {
        this.page = Math.max(page, 0);
        this.size = Math.max(size, 1);
    }

    public int getPage() { return page; }
    public void setPage(int page) { this.page = Math.max(page, 0); }
    public int getSize() { return size; }
    public void setSize(int size) { this.size = Math.max(size, 1); }
}

