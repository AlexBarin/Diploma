create table if not exists products (
    id text primary key,
    name text not null,
    category text not null,
    price_cents integer not null,
    popularity integer not null default 0
);

create table if not exists orders (
    id bigserial primary key,
    session_id text not null,
    status text not null,
    item_count integer not null,
    total_cents integer not null,
    phase_name text not null,
    created_at timestamptz not null default now(),
    processed_at timestamptz
);

insert into products (id, name, category, price_cents, popularity) values
    ('sock-classic-blue', 'Classic Blue Socks', 'socks', 690, 100),
    ('sock-neon-run', 'Neon Run Socks', 'sport', 890, 93),
    ('sock-merino-black', 'Merino Black Socks', 'premium', 1490, 91),
    ('sock-winter-thermal', 'Winter Thermal Socks', 'winter', 1790, 88),
    ('sock-office-gray', 'Office Gray Socks', 'business', 790, 84),
    ('sock-kids-orange', 'Kids Orange Socks', 'kids', 590, 81),
    ('sock-trail-green', 'Trail Green Socks', 'outdoor', 1190, 79),
    ('sock-compression-red', 'Compression Red Socks', 'sport', 1590, 77),
    ('sock-cotton-white', 'Cotton White Socks', 'basic', 490, 76),
    ('sock-luxury-silk', 'Luxury Silk Socks', 'premium', 2390, 74),
    ('sock-gift-pack', 'Gift Pack of Five', 'bundle', 3290, 72),
    ('sock-ankle-light', 'Ankle Light Socks', 'summer', 650, 69)
on conflict (id) do nothing;
