package com.xfusion.fusiondesk.model;

public enum TicketCategory {
    ACCOUNT_ACCESS("账号权限"), SOFTWARE_FAILURE("软件故障"), NETWORK("网络问题"),
    HARDWARE_OFFICE("硬件/办公设备"), BUSINESS_SYSTEM("业务系统"), OTHER("其他");

    private final String displayName;
    TicketCategory(String displayName) { this.displayName = displayName; }
    public String displayName() { return displayName; }
}
