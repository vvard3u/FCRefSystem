package ru.fcref.system.service;

import ru.fcref.system.domain.EventRecord;

public interface SelectionStateRepository {

    void replaceSnapshot(SelectionSnapshot snapshot);

    void recordEvent(EventRecord event);

    static SelectionStateRepository noop() {
        return new SelectionStateRepository() {
            @Override
            public void replaceSnapshot(SelectionSnapshot snapshot) {
            }

            @Override
            public void recordEvent(EventRecord event) {
            }
        };
    }
}
