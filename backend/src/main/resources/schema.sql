create table if not exists app_users (
    id varchar(64) primary key,
    username varchar(64) not null unique,
    password varchar(255) not null,
    display_name varchar(255) not null,
    enabled boolean not null default true
);

create table if not exists app_user_roles (
    user_id varchar(64) not null,
    role varchar(64) not null,
    primary key (user_id, role),
    constraint fk_app_user_roles_user
        foreign key (user_id)
        references app_users (id)
        on delete cascade
);

create table if not exists selection_regulations (
    id varchar(64) primary key,
    name varchar(255) not null,
    description text,
    created_at timestamp with time zone not null,
    created_by_user_id varchar(64),
    active boolean not null
);

create table if not exists selection_stage_rules (
    regulation_id varchar(64) not null,
    stage_id varchar(64) not null,
    stage_order integer not null,
    name varchar(255) not null,
    stage_type varchar(32) not null,
    attempt_limit integer not null,
    due_days integer not null,
    threshold_percent integer,
    criteria text,
    requires_submission boolean not null,
    primary key (regulation_id, stage_id)
);

create table if not exists invitations (
    id varchar(64) primary key,
    token varchar(128) not null unique,
    author_user_id varchar(64) not null,
    comment text,
    created_at timestamp with time zone not null,
    expires_at timestamp with time zone not null,
    status varchar(32) not null,
    activated_by_candidate_id varchar(64),
    activated_at timestamp with time zone
);

create table if not exists candidates (
    id varchar(64) primary key,
    full_name varchar(255) not null,
    candidate_user_id varchar(64),
    invitation_id varchar(64),
    invited_by_user_id varchar(64),
    registered_at timestamp with time zone not null,
    status varchar(32) not null,
    current_stage_id varchar(64)
);

create table if not exists candidate_stage_progress (
    id varchar(64) primary key,
    candidate_id varchar(64) not null,
    stage_id varchar(64) not null,
    stage_name varchar(255) not null,
    stage_type varchar(32) not null,
    attempt_limit integer not null,
    state varchar(32) not null,
    attempt_number integer not null,
    submitted_result text,
    submitted_at timestamp with time zone,
    verdict varchar(32),
    report text,
    decided_by_user_id varchar(64),
    decided_at timestamp with time zone
);

create table if not exists voting_sessions (
    id varchar(64) primary key,
    candidate_id varchar(64) not null,
    stage_id varchar(64),
    opened_by_user_id varchar(64),
    opened_at timestamp with time zone not null,
    closes_at timestamp with time zone not null,
    threshold_percent integer not null,
    status varchar(32) not null,
    accepted boolean,
    closed_at timestamp with time zone,
    closed_by_user_id varchar(64)
);

create table if not exists votes (
    id varchar(64) primary key,
    voting_session_id varchar(64) not null,
    voter_user_id varchar(64) not null,
    choice varchar(32) not null,
    reason text not null,
    created_at timestamp with time zone not null
);

create table if not exists complaints (
    id varchar(64) primary key,
    candidate_id varchar(64) not null,
    actor_user_id varchar(64) not null,
    reason text not null,
    created_at timestamp with time zone not null
);

create table if not exists block_records (
    id varchar(64) primary key,
    candidate_id varchar(64) not null,
    actor_user_id varchar(64) not null,
    category varchar(255) not null,
    reason text not null,
    created_at timestamp with time zone not null,
    active boolean not null,
    resolved_by_user_id varchar(64),
    resolution_reason text,
    resolved_at timestamp with time zone
);

create table if not exists selection_events (
    id varchar(96) primary key,
    event_type varchar(64) not null,
    actor_user_id varchar(64),
    candidate_id varchar(64),
    aggregate_id varchar(64),
    details text not null,
    occurred_at timestamp with time zone not null
);
