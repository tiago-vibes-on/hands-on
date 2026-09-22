INSERT INTO manager (id, display_name) VALUES
    ('019c4c00-0000-7000-8000-000000000001', 'Tiago');

INSERT INTO agency (
    id,
    name,
    leader_id,
    gold,
    reputation,
    agency_level,
    training_level,
    rest_level,
    size_level,
    reputation_level,
    intelligence_level)
VALUES (
    '019c4c00-0001-7000-8000-000000000001',
    'Dawnwatch Agency',
    '019c4c00-0000-7000-8000-000000000001',
    2480,
    340,
    4,
    4,
    3,
    4,
    3,
    3);

INSERT INTO party (id, name, agency_id) VALUES
    ('019c4c00-0002-7000-8000-000000000001', 'Broken Pass Party', '019c4c00-0001-7000-8000-000000000001');

INSERT INTO quest (
    id,
    title,
    status,
    creature_name,
    creatures_defeated,
    creatures_required,
    party_id)
VALUES (
    '019c4c00-0003-7000-8000-000000000001',
    'Trolls at Broken Pass',
    'IN_PROGRESS',
    'Troll',
    0,
    3,
    '019c4c00-0002-7000-8000-000000000001');

INSERT INTO hero (
    id,
    name,
    alias,
    hero_class,
    level,
    magic_level,
    current_health,
    current_mana,
    stamina,
    activity,
    agency_id,
    party_id)
VALUES
    (
        '019c4c00-0010-7000-8000-000000000001',
        'Brom Ironwall',
        'Ironwall',
        'WARRIOR',
        1,
        0,
        300,
        50,
        79,
        'ON_QUEST',
        '019c4c00-0001-7000-8000-000000000001',
        '019c4c00-0002-7000-8000-000000000001'),
    (
        '019c4c00-0010-7000-8000-000000000002',
        'Elara Moonweaver',
        'Moonweaver',
        'MAGE',
        1,
        15,
        100,
        500,
        24,
        'ON_QUEST',
        '019c4c00-0001-7000-8000-000000000001',
        '019c4c00-0002-7000-8000-000000000001'),
    (
        '019c4c00-0010-7000-8000-000000000003',
        'Kael Swiftarrow',
        'Swiftarrow',
        'ARCHER',
        1,
        0,
        200,
        200,
        91,
        'ON_QUEST',
        '019c4c00-0001-7000-8000-000000000001',
        '019c4c00-0002-7000-8000-000000000001'),
    (
        '019c4c00-0010-7000-8000-000000000004',
        'Dorian Oakshield',
        'Oakshield',
        'WARRIOR',
        1,
        0,
        300,
        50,
        100,
        'TRAINING',
        '019c4c00-0001-7000-8000-000000000001',
        NULL),
    (
        '019c4c00-0010-7000-8000-000000000005',
        'Runa Emberveil',
        'Emberveil',
        'MAGE',
        1,
        0,
        100,
        500,
        100,
        'RESTING',
        '019c4c00-0001-7000-8000-000000000001',
        NULL),
    (
        '019c4c00-0010-7000-8000-000000000006',
        'Lyra Hawkeye',
        'Hawkeye',
        'ARCHER',
        1,
        0,
        200,
        200,
        100,
        'TRAINING',
        '019c4c00-0001-7000-8000-000000000001',
        NULL);

INSERT INTO rune (id, code, name, symbol, stats, description, effect, effect_value) VALUES
    ('019c4c00-0020-7000-8000-000000000001', 'attack-rune', 'Attack Rune', '✦', '+8 attack', 'A carved rune that strengthens every basic attack.', 'ATTACK', 8),
    ('019c4c00-0020-7000-8000-000000000002', 'guard-rune', 'Guard Rune', '◈', '+6 armor', 'A protective rune etched with an unbroken circle.', 'ARMOR', 6),
    ('019c4c00-0020-7000-8000-000000000003', 'vitality-rune', 'Vitality Rune', '✚', '+20 health', 'A living rune that reinforces a hero''s endurance.', 'HEALTH', 20),
    ('019c4c00-0020-7000-8000-000000000004', 'haste-rune', 'Haste Rune', '🪶', '+4% attack speed', 'A quicksilver rune that accelerates basic attacks.', 'ATTACK_SPEED', 0.04),
    ('019c4c00-0020-7000-8000-000000000005', 'mana-rune', 'Mana Rune', '♦', '+30 mana', 'A blue rune that stores a reserve of magical energy.', 'MANA', 30),
    ('019c4c00-0020-7000-8000-000000000006', 'critical-chance-rune', 'Critical Chance Rune', '✧', '+1% critical chance', 'A precise rune that gives its bearer a chance to critically strike.', 'CRITICAL_CHANCE', 0.01),
    ('019c4c00-0020-7000-8000-000000000007', 'critical-damage-rune', 'Critical Damage Rune', '✹', '+10% critical damage', 'A forceful rune that increases the damage dealt by critical hits.', 'CRITICAL_DAMAGE', 0.1);

INSERT INTO agency_rune (id, agency_id, rune_id, quantity) VALUES
    ('019c4c00-0030-7000-8000-000000000001', '019c4c00-0001-7000-8000-000000000001', '019c4c00-0020-7000-8000-000000000001', 1),
    ('019c4c00-0030-7000-8000-000000000002', '019c4c00-0001-7000-8000-000000000001', '019c4c00-0020-7000-8000-000000000002', 0),
    ('019c4c00-0030-7000-8000-000000000003', '019c4c00-0001-7000-8000-000000000001', '019c4c00-0020-7000-8000-000000000003', 1),
    ('019c4c00-0030-7000-8000-000000000004', '019c4c00-0001-7000-8000-000000000001', '019c4c00-0020-7000-8000-000000000004', 0),
    ('019c4c00-0030-7000-8000-000000000005', '019c4c00-0001-7000-8000-000000000001', '019c4c00-0020-7000-8000-000000000005', 0),
    ('019c4c00-0030-7000-8000-000000000006', '019c4c00-0001-7000-8000-000000000001', '019c4c00-0020-7000-8000-000000000006', 0),
    ('019c4c00-0030-7000-8000-000000000007', '019c4c00-0001-7000-8000-000000000001', '019c4c00-0020-7000-8000-000000000007', 0);

INSERT INTO hero_rune (id, hero_id, rune_id, slot_index) VALUES
    ('019c4c00-0040-7000-8000-000000000001', '019c4c00-0010-7000-8000-000000000001', '019c4c00-0020-7000-8000-000000000001', 0),
    ('019c4c00-0040-7000-8000-000000000002', '019c4c00-0010-7000-8000-000000000001', '019c4c00-0020-7000-8000-000000000002', 1),
    ('019c4c00-0040-7000-8000-000000000003', '019c4c00-0010-7000-8000-000000000001', '019c4c00-0020-7000-8000-000000000006', 2),
    ('019c4c00-0040-7000-8000-000000000004', '019c4c00-0010-7000-8000-000000000002', '019c4c00-0020-7000-8000-000000000005', 0),
    ('019c4c00-0040-7000-8000-000000000005', '019c4c00-0010-7000-8000-000000000002', '019c4c00-0020-7000-8000-000000000006', 1),
    ('019c4c00-0040-7000-8000-000000000006', '019c4c00-0010-7000-8000-000000000002', '019c4c00-0020-7000-8000-000000000007', 2),
    ('019c4c00-0040-7000-8000-000000000007', '019c4c00-0010-7000-8000-000000000003', '019c4c00-0020-7000-8000-000000000003', 0),
    ('019c4c00-0040-7000-8000-000000000008', '019c4c00-0010-7000-8000-000000000003', '019c4c00-0020-7000-8000-000000000004', 1),
    ('019c4c00-0040-7000-8000-000000000009', '019c4c00-0010-7000-8000-000000000003', '019c4c00-0020-7000-8000-000000000006', 2);
