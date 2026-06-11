-- Create some realistic items
INSERT INTO inventory_items (id, sku, location_id, qty_on_hand, qty_reserved, created_at, updated_at) VALUES 
(gen_random_uuid(), 'LAPTOP-MACBOOK-M3', gen_random_uuid(), 150, 0, now(), now()),
(gen_random_uuid(), 'LAPTOP-MACBOOK-PRO', gen_random_uuid(), 85, 0, now(), now()),
(gen_random_uuid(), 'LAPTOP-DELL-XPS15', gen_random_uuid(), 42, 0, now(), now()),
(gen_random_uuid(), 'PHONE-IPHONE-15-PRO', gen_random_uuid(), 310, 0, now(), now()),
(gen_random_uuid(), 'PHONE-SAMSUNG-S24', gen_random_uuid(), 205, 0, now(), now()),
(gen_random_uuid(), 'MONITOR-LG-32-4K', gen_random_uuid(), 55, 0, now(), now()),
(gen_random_uuid(), 'MONITOR-DELL-U27', gen_random_uuid(), 110, 0, now(), now()),
(gen_random_uuid(), 'AUDIO-AIRPODS-PRO', gen_random_uuid(), 450, 0, now(), now()),
(gen_random_uuid(), 'AUDIO-SONY-WH1000', gen_random_uuid(), 120, 0, now(), now()),
(gen_random_uuid(), 'ACC-MAGIC-MOUSE', gen_random_uuid(), 200, 0, now(), now());
