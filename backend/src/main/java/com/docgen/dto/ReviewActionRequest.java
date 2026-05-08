package com.docgen.dto;

public class ReviewActionRequest {

    private String comment;

    public ReviewActionRequest() {}

    public ReviewActionRequest(String comment) {
        this.comment = comment;
    }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}
