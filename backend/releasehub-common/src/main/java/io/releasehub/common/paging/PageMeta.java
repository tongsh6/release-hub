package io.releasehub.common.paging;

public class PageMeta {
    private int page;
    private int size;
    private long total;
    private int totalPages;
    private boolean hasNext;
    private boolean hasPrevious;

    public PageMeta() {}

    public PageMeta(int page, int size, long total) {
        int safePage = Math.max(page, 1);
        this.page = safePage;
        this.size = size;
        this.total = total;
        this.totalPages = (int) Math.ceil((double) total / Math.max(size, 1));
        this.hasPrevious = safePage > 1;
        this.hasNext = safePage < this.totalPages;
    }

    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }
    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }
    public int getTotalPages() { return totalPages; }
    public void setTotalPages(int totalPages) { this.totalPages = totalPages; }
    public boolean isHasNext() { return hasNext; }
    public void setHasNext(boolean hasNext) { this.hasNext = hasNext; }
    public boolean isHasPrevious() { return hasPrevious; }
    public void setHasPrevious(boolean hasPrevious) { this.hasPrevious = hasPrevious; }
}
