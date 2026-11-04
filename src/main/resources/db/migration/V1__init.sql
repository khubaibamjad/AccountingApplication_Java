CREATE TABLE accounts
(
    id   VARCHAR(36)  PRIMARY KEY,
    code VARCHAR(20)  NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    type VARCHAR(20)  NOT NULL
);

CREATE TABLE parties
(
    id         VARCHAR(36)  PRIMARY KEY,
    name       VARCHAR(150) NOT NULL,
    type       VARCHAR(20)  NOT NULL,                      -- VENDOR, CUSTOMER, BOTH
    phone      VARCHAR(30),                                -- optional
    region     VARCHAR(50),                                -- optional
    account_id VARCHAR(36)  NOT NULL UNIQUE REFERENCES accounts (id)
);

CREATE TABLE journal_entries
(
    id          VARCHAR(36) PRIMARY KEY,
    entry_date  DATE NOT NULL,
    description VARCHAR(250)
);

CREATE TABLE journal_lines
(
    id         VARCHAR(36)    PRIMARY KEY,
    entry_id   VARCHAR(36)    NOT NULL REFERENCES journal_entries (id),
    account_id VARCHAR(36)    NOT NULL REFERENCES accounts (id),
    debit      DECIMAL(15, 2) NOT NULL DEFAULT 0,
    credit     DECIMAL(15, 2) NOT NULL DEFAULT 0
);

INSERT INTO accounts (id, code, name, type)
VALUES ('00000000-0000-0000-0000-000000001000', '1000', 'Cash', 'ASSET'),
       ('00000000-0000-0000-0000-000000001010', '1010', 'Bank', 'ASSET'),
       ('00000000-0000-0000-0000-000000001200', '1200', 'Inventory', 'ASSET'),
       ('00000000-0000-0000-0000-000000003000', '3000', 'Owner Capital', 'EQUITY'),
       ('00000000-0000-0000-0000-000000004000', '4000', 'Sales Income', 'INCOME'),
       ('00000000-0000-0000-0000-000000005000', '5000', 'Cost of Goods Sold', 'EXPENSE'),
       ('00000000-0000-0000-0000-000000005100', '5100', 'Rent Expense', 'EXPENSE'),
       ('00000000-0000-0000-0000-000000005200', '5200', 'Salaries Expense', 'EXPENSE');