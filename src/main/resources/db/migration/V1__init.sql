CREATE TABLE accounts
(
    id   varchar(36) PRIMARY KEY,
    code varchar(36)  not null unique,
    name varchar(150) not null,
    type varchar(20)  not null
);

create table parties
(
    id     varchar(36) PRIMARY KEY,
    name   varchar(150) not null,
    type   varchar(50)  not null,
    region varchar(30)  not null
);

create table journal_entries
(
    id          varchar(36) PRIMARY KEY,
    entry_date  DATE NOT NULL,
    description varchar(250)
);

create table journal_lines
(
    id         varchar(36) PRIMARY KEY,
    entries_id varchar(36)    NOT NULL REFERENCES journal_entries (id),
    account_id varchar(36)    NOT NULL REFERENCES accounts (id),
    parties_id varchar(36)    NOT NULL REFERENCES parties (id),
    credit     decimal(15, 2) NOT NULL default 0,
    debit      decimal(15, 2) NOT NULL default 0
);


INSERT INTO accounts (id, code, name, type)
VALUES ('00000000-0000-0000-0000-000000001000', '1000', 'Cash', 'ASSET'),
       ('00000000-0000-0000-0000-000000001010', '1010', 'Bank', 'ASSET'),
       ('00000000-0000-0000-0000-000000001100', '1100', 'Accounts Receivable', 'ASSET'),
       ('00000000-0000-0000-0000-000000001200', '1200', 'Inventory', 'ASSET'),
       ('00000000-0000-0000-0000-000000002000', '2000', 'Accounts Payable', 'LIABILITY'),
       ('00000000-0000-0000-0000-000000003000', '3000', 'Owner Capital', 'EQUITY'),
       ('00000000-0000-0000-0000-000000004000', '4000', 'Sales Income', 'INCOME'),
       ('00000000-0000-0000-0000-000000005000', '5000', 'Cost of Goods Sold', 'EXPENSE'),
       ('00000000-0000-0000-0000-000000005100', '5100', 'Rent Expense', 'EXPENSE'),
       ('00000000-0000-0000-0000-000000005200', '5200', 'Salaries Expense', 'EXPENSE');
