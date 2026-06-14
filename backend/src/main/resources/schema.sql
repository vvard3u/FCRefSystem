create table app_users (
    id varchar(64) primary key,
    username varchar(64) not null unique,
    password varchar(255) not null,
    display_name varchar(255) not null,
    enabled boolean not null default true
);

create table app_user_roles (
    user_id varchar(64) not null,
    role varchar(64) not null,
    primary key (user_id, role),
    constraint fk_app_user_roles_user
        foreign key (user_id)
        references app_users (id)
        on delete cascade
);
