package com.docgen.dto;

import java.util.List;

public class TemplateQueryRequest {

    private String keyword;
    private Long categoryId;
    private List<Long> tagIds;

    public TemplateQueryRequest() {}

    public TemplateQueryRequest(String keyword) {
        this.keyword = keyword;
    }

    public TemplateQueryRequest(String keyword, Long categoryId, List<Long> tagIds) {
        this.keyword = keyword;
        this.categoryId = categoryId;
        this.tagIds = tagIds;
    }

    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }

    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }

    public List<Long> getTagIds() { return tagIds; }
    public void setTagIds(List<Long> tagIds) { this.tagIds = tagIds; }
}
