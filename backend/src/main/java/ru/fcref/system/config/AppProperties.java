package ru.fcref.system.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private int invitationTtlDays = 30;
    private int memberInvitationQuota = 3;

    public int getInvitationTtlDays() {
        return invitationTtlDays;
    }

    public void setInvitationTtlDays(int invitationTtlDays) {
        this.invitationTtlDays = invitationTtlDays;
    }

    public int getMemberInvitationQuota() {
        return memberInvitationQuota;
    }

    public void setMemberInvitationQuota(int memberInvitationQuota) {
        this.memberInvitationQuota = memberInvitationQuota;
    }
}
