INSERT INTO account (
    id,
    keycloak_subject,
    email,
    email_verified,
    status,
    created_at,
    last_login_at)
VALUES
    (
        '019c4c00-0000-7000-8000-000000000010',
        '019c4c00-0100-7000-8000-000000000001',
        'user1@mail.com',
        true,
        'ACTIVE',
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP),
    (
        '019c4c00-0000-7000-8000-000000000020',
        '019c4c00-0100-7000-8000-000000000002',
        'user2@mail.com',
        true,
        'ACTIVE',
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP),
    (
        '019c4c00-0000-7000-8000-000000000030',
        'local-seed-soren',
        'soren@example.invalid',
        false,
        'ACTIVE',
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP),
    (
        '019c4c00-0000-7000-8000-000000000101',
        '019c4c00-0100-7000-8000-000000000101',
        'manager1@mail.com',
        true,
        'ACTIVE',
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP),
    (
        '019c4c00-0000-7000-8000-000000000102',
        '019c4c00-0100-7000-8000-000000000102',
        'manager2@mail.com',
        true,
        'ACTIVE',
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP),
    (
        '019c4c00-0000-7000-8000-000000000103',
        '019c4c00-0100-7000-8000-000000000103',
        'manager3@mail.com',
        true,
        'ACTIVE',
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP),
    (
        '019c4c00-0000-7000-8000-000000000104',
        '019c4c00-0100-7000-8000-000000000104',
        'manager4@mail.com',
        true,
        'ACTIVE',
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP),
    (
        '019c4c00-0000-7000-8000-000000000105',
        '019c4c00-0100-7000-8000-000000000105',
        'manager5@mail.com',
        true,
        'ACTIVE',
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP),
    (
        '019c4c00-0000-7000-8000-000000000106',
        '019c4c00-0100-7000-8000-000000000106',
        'manager6@mail.com',
        true,
        'ACTIVE',
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP),
    (
        '019c4c00-0000-7000-8000-000000000107',
        '019c4c00-0100-7000-8000-000000000107',
        'manager7@mail.com',
        true,
        'ACTIVE',
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP),
    (
        '019c4c00-0000-7000-8000-000000000108',
        '019c4c00-0100-7000-8000-000000000108',
        'manager8@mail.com',
        true,
        'ACTIVE',
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP),
    (
        '019c4c00-0000-7000-8000-000000000109',
        '019c4c00-0100-7000-8000-000000000109',
        'manager9@mail.com',
        true,
        'ACTIVE',
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP),
    (
        '019c4c00-0000-7000-8000-000000000110',
        '019c4c00-0100-7000-8000-000000000110',
        'manager10@mail.com',
        true,
        'ACTIVE',
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);

INSERT INTO manager (id, display_name, display_name_normalized, account_id) VALUES
    (
        '019c4c00-0000-7000-8000-000000000001',
        'User 1',
        'user 1',
        '019c4c00-0000-7000-8000-000000000010'),
    (
        '019c4c00-0000-7000-8000-000000000002',
        'User 2',
        'user 2',
        '019c4c00-0000-7000-8000-000000000020'),
    (
        '019c4c00-0000-7000-8000-000000000003',
        'Soren',
        'soren',
        '019c4c00-0000-7000-8000-000000000030'),
    (
        '019c4c00-0000-7000-8000-000000000201',
        'Manager 1',
        'manager 1',
        '019c4c00-0000-7000-8000-000000000101'),
    (
        '019c4c00-0000-7000-8000-000000000202',
        'Manager 2',
        'manager 2',
        '019c4c00-0000-7000-8000-000000000102'),
    (
        '019c4c00-0000-7000-8000-000000000203',
        'Manager 3',
        'manager 3',
        '019c4c00-0000-7000-8000-000000000103'),
    (
        '019c4c00-0000-7000-8000-000000000204',
        'Manager 4',
        'manager 4',
        '019c4c00-0000-7000-8000-000000000104'),
    (
        '019c4c00-0000-7000-8000-000000000205',
        'Manager 5',
        'manager 5',
        '019c4c00-0000-7000-8000-000000000105'),
    (
        '019c4c00-0000-7000-8000-000000000206',
        'Manager 6',
        'manager 6',
        '019c4c00-0000-7000-8000-000000000106'),
    (
        '019c4c00-0000-7000-8000-000000000207',
        'Manager 7',
        'manager 7',
        '019c4c00-0000-7000-8000-000000000107'),
    (
        '019c4c00-0000-7000-8000-000000000208',
        'Manager 8',
        'manager 8',
        '019c4c00-0000-7000-8000-000000000108'),
    (
        '019c4c00-0000-7000-8000-000000000209',
        'Manager 9',
        'manager 9',
        '019c4c00-0000-7000-8000-000000000109'),
    (
        '019c4c00-0000-7000-8000-000000000210',
        'Manager 10',
        'manager 10',
        '019c4c00-0000-7000-8000-000000000110');

-- Disposable borrowing fixtures: no gold, below/exact price, and enough for multiple heroes.

-- Large local-only wallet for user2 to exercise game and market flows.

INSERT INTO agency (id, name, name_normalized, leader_id, reputation, agency_level, training_level, rest_level, size_level, reputation_level, intelligence_level) VALUES
    ('019c4c00-0001-7000-8000-000000000001', 'Dawnwatch Agency', 'dawnwatch agency', '019c4c00-0000-7000-8000-000000000001', 340, 4, 4, 3, 4, 3, 3);


INSERT INTO agency (id, name, name_normalized, leader_id, reputation, agency_level, training_level, rest_level, size_level, reputation_level, intelligence_level) VALUES
    ('019c4c00-0001-7000-8000-000000000002', 'Ironridge Exchange', 'ironridge exchange', '019c4c00-0000-7000-8000-000000000002', 120, 2, 2, 2, 2, 2, 2);


INSERT INTO agency (id, name, name_normalized, leader_id, reputation, agency_level, training_level, rest_level, size_level, reputation_level, intelligence_level) VALUES
    ('019c4c00-0001-7000-8000-000000000003', 'Silverkeep Guild', 'silverkeep guild', '019c4c00-0000-7000-8000-000000000208', 40, 1, 1, 1, 1, 1, 1);


INSERT INTO agency_member (id, agency_id, manager_id, role, joined_at) VALUES
    (
        '019c4c00-0001-7000-8000-000000000010',
        '019c4c00-0001-7000-8000-000000000001',
        '019c4c00-0000-7000-8000-000000000001',
        'LEADER',
        CURRENT_TIMESTAMP),
    (
        '019c4c00-0001-7000-8000-000000000020',
        '019c4c00-0001-7000-8000-000000000002',
        '019c4c00-0000-7000-8000-000000000002',
        'LEADER',
        CURRENT_TIMESTAMP),
    (
        '019c4c00-0001-7000-8000-000000000030',
        '019c4c00-0001-7000-8000-000000000001',
        '019c4c00-0000-7000-8000-000000000003',
        'MANAGER',
        CURRENT_TIMESTAMP),
    (
        '019c4c00-0001-7000-8000-000000000101',
        '019c4c00-0001-7000-8000-000000000001',
        '019c4c00-0000-7000-8000-000000000201',
        'MANAGER',
        CURRENT_TIMESTAMP),
    (
        '019c4c00-0001-7000-8000-000000000102',
        '019c4c00-0001-7000-8000-000000000001',
        '019c4c00-0000-7000-8000-000000000202',
        'MANAGER',
        CURRENT_TIMESTAMP),
    (
        '019c4c00-0001-7000-8000-000000000103',
        '019c4c00-0001-7000-8000-000000000001',
        '019c4c00-0000-7000-8000-000000000203',
        'MANAGER',
        CURRENT_TIMESTAMP),
    (
        '019c4c00-0001-7000-8000-000000000104',
        '019c4c00-0001-7000-8000-000000000001',
        '019c4c00-0000-7000-8000-000000000204',
        'MANAGER',
        CURRENT_TIMESTAMP),
    (
        '019c4c00-0001-7000-8000-000000000105',
        '019c4c00-0001-7000-8000-000000000002',
        '019c4c00-0000-7000-8000-000000000205',
        'MANAGER',
        CURRENT_TIMESTAMP),
    (
        '019c4c00-0001-7000-8000-000000000106',
        '019c4c00-0001-7000-8000-000000000002',
        '019c4c00-0000-7000-8000-000000000206',
        'MANAGER',
        CURRENT_TIMESTAMP),
    (
        '019c4c00-0001-7000-8000-000000000107',
        '019c4c00-0001-7000-8000-000000000002',
        '019c4c00-0000-7000-8000-000000000207',
        'MANAGER',
        CURRENT_TIMESTAMP),
    (
        '019c4c00-0001-7000-8000-000000000108',
        '019c4c00-0001-7000-8000-000000000003',
        '019c4c00-0000-7000-8000-000000000208',
        'LEADER',
        CURRENT_TIMESTAMP),
    (
        '019c4c00-0001-7000-8000-000000000109',
        '019c4c00-0001-7000-8000-000000000003',
        '019c4c00-0000-7000-8000-000000000209',
        'MANAGER',
        CURRENT_TIMESTAMP),
    (
        '019c4c00-0001-7000-8000-000000000110',
        '019c4c00-0001-7000-8000-000000000003',
        '019c4c00-0000-7000-8000-000000000210',
        'MANAGER',
        CURRENT_TIMESTAMP);

INSERT INTO party (id, name, agency_id, manager_id) VALUES
    ('019c4c00-0002-7000-8000-000000000001', 'Broken Pass Party',
     '019c4c00-0001-7000-8000-000000000001', '019c4c00-0000-7000-8000-000000000001');

INSERT INTO party (id, name, agency_id, manager_id)
VALUES ('019c4c00-0002-7000-8000-000000000002', 'Main Party',
        '019c4c00-0001-7000-8000-000000000002', '019c4c00-0000-7000-8000-000000000002');

-- One deterministic Main Party for every other seeded Manager except User1,
-- whose existing Broken Pass Party is already their party.
INSERT INTO party (id, name, agency_id, manager_id)
SELECT ('019c4c00-0002-7001-8000-' || right(manager.id::text, 12))::uuid,
       'Main Party', membership.agency_id, manager.id
FROM manager
JOIN agency_member AS membership ON membership.manager_id = manager.id
WHERE manager.id NOT IN (
    '019c4c00-0000-7000-8000-000000000001',
    '019c4c00-0000-7000-8000-000000000002');

INSERT INTO hero (
    id,
    name,
    alias,
    hero_class,
    experience,
    melee_points,
    distance_points,
    magic_points,
    shield_points,
    current_health,
    current_mana,
    stamina_milliseconds,
    activity,
    last_resource_synchronized_at,
    agency_id,
    party_id)
VALUES
    (
        '019c4c00-0010-7000-8000-000000000001',
        'Brom Ironwall',
        'Ironwall',
        'WARRIOR',
        0,
        0,
        0,
        0,
        0,
        300,
        50,
        100224000,
        'RESTING',
        CURRENT_TIMESTAMP,
        '019c4c00-0001-7000-8000-000000000001',
        '019c4c00-0002-7000-8000-000000000001'),
    (
        '019c4c00-0010-7000-8000-000000000002',
        'Elara Moonweaver',
        'Moonweaver',
        'MAGE',
        0,
        0,
        0,
        4058,
        0,
        100,
        500,
        41472000,
        'RESTING',
        CURRENT_TIMESTAMP,
        '019c4c00-0001-7000-8000-000000000001',
        '019c4c00-0002-7000-8000-000000000001'),
    (
        '019c4c00-0010-7000-8000-000000000003',
        'Kael Swiftarrow',
        'Swiftarrow',
        'ARCHER',
        0,
        0,
        0,
        0,
        0,
        200,
        200,
        157248000,
        'RESTING',
        CURRENT_TIMESTAMP,
        '019c4c00-0001-7000-8000-000000000001',
        '019c4c00-0002-7000-8000-000000000001'),
    (
        '019c4c00-0010-7000-8000-000000000004',
        'Dorian Oakshield',
        'Oakshield',
        'WARRIOR',
        0,
        0,
        0,
        0,
        0,
        300,
        50,
        172800000,
        'TRAINING',
        CURRENT_TIMESTAMP,
        '019c4c00-0001-7000-8000-000000000001',
        NULL),
    (
        '019c4c00-0010-7000-8000-000000000005',
        'Runa Emberveil',
        'Emberveil',
        'MAGE',
        0,
        0,
        0,
        0,
        0,
        100,
        500,
        172800000,
        'RESTING',
        CURRENT_TIMESTAMP,
        '019c4c00-0001-7000-8000-000000000001',
        NULL),
    (
        '019c4c00-0010-7000-8000-000000000006',
        'Lyra Hawkeye',
        'Hawkeye',
        'ARCHER',
        0,
        0,
        0,
        0,
        0,
        200,
        200,
        172800000,
        'TRAINING',
        CURRENT_TIMESTAMP,
        '019c4c00-0001-7000-8000-000000000001',
        NULL),
    (
        '019c4c00-0010-7000-8000-000000000007',
        'Alden Steelward',
        'Steelward',
        'WARRIOR',
        0,
        0,
        0,
        0,
        0,
        300,
        50,
        172800000,
        'TRAINING',
        CURRENT_TIMESTAMP,
        NULL,
        NULL),
    (
        '019c4c00-0010-7000-8000-000000000008',
        'Seris Dawnflame',
        'Dawnflame',
        'MAGE',
        0,
        0,
        0,
        0,
        0,
        100,
        500,
        172800000,
        'TRAINING',
        CURRENT_TIMESTAMP,
        NULL,
        NULL),
    (
        '019c4c00-0010-7000-8000-000000000009',
        'Tarin Windmark',
        'Windmark',
        'ARCHER',
        0,
        0,
        0,
        0,
        0,
        200,
        200,
        172800000,
        'TRAINING',
        CURRENT_TIMESTAMP,
        NULL,
        NULL);

-- Oakshield remains free; Emberveil and Hawkeye exercise paid borrowing.
UPDATE hero SET borrowing_fee_gold = 25 WHERE id = '019c4c00-0010-7000-8000-000000000005';
UPDATE hero SET borrowing_fee_gold = 100 WHERE id = '019c4c00-0010-7000-8000-000000000006';

-- Economic seed state is now in Assets; reset Core, Assets and Market together.







INSERT INTO feed_post (
    id,
    agency_id,
    author_type,
    author_id,
    author_name,
    content,
    published_at)
VALUES
    (
        '019c4c00-0060-7000-8000-000000000001',
        '019c4c00-0001-7000-8000-000000000001',
        'AGENCY',
        '019c4c00-0001-7000-8000-000000000001',
        'Dawnwatch Agency',
        'The party has reached Broken Pass. The road will be open again soon.',
        CURRENT_TIMESTAMP - INTERVAL '12 minutes'),
    (
        '019c4c00-0060-7000-8000-000000000002',
        '019c4c00-0001-7000-8000-000000000001',
        'HERO',
        '019c4c00-0010-7000-8000-000000000002',
        'Elara Moonweaver',
        'Rested, prepared, and ready for whatever waits beyond the pass.',
        CURRENT_TIMESTAMP - INTERVAL '1 hour');

-- Each Manager begins with a separate personal roster. Agency and recruitable
-- heroes above remain untouched. UUIDs and aliases are deterministic per owner.
INSERT INTO hero (
    id, name, alias, hero_class, experience, melee_points, distance_points,
    magic_points, shield_points, current_health, current_mana,
    stamina_milliseconds, activity, last_resource_synchronized_at,
    agency_id, manager_id, party_id)
SELECT
    ('019c4c00-0030-700' || starter.slot || '-8000-' || right(manager.id::text, 12))::uuid,
    'Starter ' || starter.name,
    'starter-' || right(manager.id::text, 12) || '-' || lower(starter.hero_class),
    starter.hero_class, 0, 0, 0, 0, 0,
    starter.health, starter.mana, 172800000, 'TRAINING', CURRENT_TIMESTAMP,
    NULL, manager.id, NULL
FROM manager
CROSS JOIN (VALUES
    (1, 'Warrior', 'WARRIOR', 300, 50),
    (2, 'Mage', 'MAGE', 100, 500),
    (3, 'Archer', 'ARCHER', 200, 200)
) AS starter(slot, name, hero_class, health, mana);

-- Seeded Main Parties start with their Managers three personal starter heroes.
UPDATE hero AS starter SET party_id = party.id
FROM party
WHERE party.manager_id = starter.manager_id
  AND party.name = 'Main Party'
  AND starter.party_id IS NULL;

-- User2 is the seeded Map showcase; ordinary starter skills still begin at Level 1.
UPDATE hero SET magic_points = 4058
WHERE manager_id = '019c4c00-0000-7000-8000-000000000002'
  AND hero_class = 'MAGE';

-- Equip user2's three personal Map heroes so the live battle shows rune loadouts.
