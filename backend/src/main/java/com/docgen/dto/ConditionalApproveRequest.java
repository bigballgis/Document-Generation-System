package com.docgen.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public class ConditionalApproveRequest {

    private String comment;

    @NotEmpty(message = "建议修改项列表不能为空")
    private List<String> suggestions;

    public ConditionalApproveRequest() {}

    public ConditionalApproveRequest(String comment, List<String> suggestions) {
        this.comment = comment;
        this.suggestions = suggestions;
    }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public List<String> getSuggestions() { return suggestions; }
    public void setSuggestions(List<String> suggestions) { this.suggestions = suggestions; }
}
