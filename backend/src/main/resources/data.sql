delete from selection_events;

delete from app_user_roles
where role = 'INTERVIEWER'
   or user_id = 'interviewer-1'
   or user_id like 'candidate-user-%';

delete from app_users
where id = 'interviewer-1'
   or id like 'candidate-user-%';

insert into app_users (id, username, password, display_name, enabled) values
    ('admin-1', 'admin', '{noop}admin', 'Administrator', true),
    ('member-1', 'member', '{noop}member', 'Active club member', true),
    ('privileged-1', 'privileged', '{noop}privileged', 'Privileged club member', true),
    ('privileged-2', 'privileged2', '{noop}privileged2', 'Second privileged club member', true)
on conflict (id) do nothing;

insert into app_user_roles (user_id, role) values
    ('admin-1', 'MEMBER'),
    ('admin-1', 'ADMIN'),
    ('member-1', 'MEMBER'),
    ('privileged-1', 'MEMBER'),
    ('privileged-1', 'PRIVILEGED_MEMBER'),
    ('privileged-2', 'MEMBER'),
    ('privileged-2', 'PRIVILEGED_MEMBER')
on conflict (user_id, role) do nothing;
