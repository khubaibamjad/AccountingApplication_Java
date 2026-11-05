-- Common expense categories and fixed-asset accounts.
-- OR IGNORE: skips any code you already created yourself.
INSERT OR IGNORE INTO accounts (id, code, name, type) VALUES
 ('00000000-0000-0000-0000-000000005300', '5300', 'Electricity', 'EXPENSE'),
 ('00000000-0000-0000-0000-000000005310', '5310', 'Gas & Water', 'EXPENSE'),
 ('00000000-0000-0000-0000-000000005320', '5320', 'Internet & Phone', 'EXPENSE'),
 ('00000000-0000-0000-0000-000000005400', '5400', 'Fuel', 'EXPENSE'),
 ('00000000-0000-0000-0000-000000005410', '5410', 'Vehicle Maintenance', 'EXPENSE'),
 ('00000000-0000-0000-0000-000000005500', '5500', 'Office Food & Refreshments', 'EXPENSE'),
 ('00000000-0000-0000-0000-000000005600', '5600', 'Office Supplies', 'EXPENSE'),
 ('00000000-0000-0000-0000-000000005700', '5700', 'Repairs & Maintenance', 'EXPENSE'),
 ('00000000-0000-0000-0000-000000005800', '5800', 'Transport & Delivery', 'EXPENSE'),
 ('00000000-0000-0000-0000-000000005900', '5900', 'Miscellaneous Expense', 'EXPENSE'),
 ('00000000-0000-0000-0000-000000001250', '1250', 'Vehicles', 'ASSET'),
 ('00000000-0000-0000-0000-000000001260', '1260', 'Furniture & Equipment', 'ASSET'),
 ('00000000-0000-0000-0000-000000001270', '1270', 'Computers', 'ASSET');
