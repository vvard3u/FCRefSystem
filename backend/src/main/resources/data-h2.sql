merge into app_users (id, username, password, display_name, enabled) key(id) values
    ('admin-1', 'admin', '{noop}admin', 'Administrator', true),
    ('member-1', 'member', '{noop}member', 'Active club member', true),
    ('privileged-1', 'privileged', '{noop}privileged', 'Privileged club member', true),
    ('interviewer-1', 'interviewer', '{noop}interviewer', 'Interviewer', true),
    ('candidate-user-1', 'candidate', '{noop}candidate', 'Candidate', true);

merge into app_user_roles (user_id, role) key(user_id, role) values
    ('admin-1', 'MEMBER'),
    ('admin-1', 'ADMIN'),
    ('member-1', 'MEMBER'),
    ('privileged-1', 'MEMBER'),
    ('privileged-1', 'PRIVILEGED_MEMBER'),
    ('interviewer-1', 'MEMBER'),
    ('interviewer-1', 'INTERVIEWER'),
    ('candidate-user-1', 'CANDIDATE');
