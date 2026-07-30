package com.shakur.cafehelp.DTO;

public class VkBotLinkConfirmRequestDTO {
    private Long vkUserId;
    private String vkDomain;
    private String code;

    public Long getVkUserId() {
        return vkUserId;
    }

    public void setVkUserId(Long vkUserId) {
        this.vkUserId = vkUserId;
    }

    public String getVkDomain() {
        return vkDomain;
    }

    public void setVkDomain(String vkDomain) {
        this.vkDomain = vkDomain;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}
