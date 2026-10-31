CREATE TABLE accounts
(
    id varchar(36) PRIMARY KEY,
    code varchar(36) not null unique,
    name varchar(150)not null,
    type varchar(20) not null,
);

create table parties
(
    id varchar (36) PRIMARY KEY,
    name varchar (150) not null,
    type varchar (50) not null,
    region varchar (30) not null,
);

create table journal_entries
(
    id varchar(36) PRIMARY KEY,
    entry_date DATE NOT NULL,
    description varchar(250)
);

create table journal_lines
(
    id varchar(36) PRIMARY KEY,
    entries_id varchar(36) NOT NULL REFERENCES journal_entries(id),
    account_id varchar(36) NOT NULL REFERENCES accounts(id),
    parties_id varchar(36) NOT NULL REFERENCES parties(id),
    credit decimal(15,2) NOT NULL default 0,
    debit decimal(15,2) NOT NULL default 0
);
