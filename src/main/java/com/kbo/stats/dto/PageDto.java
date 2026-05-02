package com.kbo.stats.dto;

import lombok.Getter;

import java.util.List;

@Getter
public class PageDto<T> {
    private final List<T> content;
    private final int page;
    private final int size;
    private final long total;
    private final int totalPages;

    public PageDto(List<T> content, int page, int size, long total) {
        this.content = content;
        this.page = page;
        this.size = size;
        this.total = total;
        this.totalPages = (int) Math.ceil((double) total / size);
    }

    public boolean hasPrevious() {
        return page > 1;
    }

    public boolean hasNext() {
        return page < totalPages;
    }

    public int getStartPage() {
        return Math.max(1, page - 2);
    }

    public int getEndPage() {
        return Math.min(totalPages, page + 2);
    }
}
