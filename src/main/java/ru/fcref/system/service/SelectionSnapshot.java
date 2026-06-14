package ru.fcref.system.service;

import java.util.List;
import ru.fcref.system.domain.Candidate;
import ru.fcref.system.domain.EventRecord;
import ru.fcref.system.domain.Invitation;
import ru.fcref.system.domain.SelectionRegulation;
import ru.fcref.system.domain.UserAccount;

public record SelectionSnapshot(
        List<UserAccount> users,
        List<Invitation> invitations,
        List<Candidate> candidates,
        List<SelectionRegulation> regulations,
        List<EventRecord> events
) {
}
