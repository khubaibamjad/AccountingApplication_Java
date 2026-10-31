CREATE TABLE accounts
(
    id varchar(36) PRIMARY KEY,
    code varchar(36) not null unique,
    name varchar(150)not null,
    type varchar(20) not null,
)

