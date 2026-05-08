package com.docgen.dto;

public class UpdateProfileRequest {

    private String nickname;
    private String avatarUrl;
    private String contactInfo;

    public UpdateProfileRequest() {
    }

    public UpdateProfileRequest(String nickname, String avatarUrl, String contactInfo) {
        this.nickname = nickname;
        this.avatarUrl = avatarUrl;
        this.contactInfo = contactInfo;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getContactInfo() {
        return contactInfo;
    }

    public void setContactInfo(String contactInfo) {
        this.contactInfo = contactInfo;
    }
}
