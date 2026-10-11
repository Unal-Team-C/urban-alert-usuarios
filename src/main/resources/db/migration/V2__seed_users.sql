INSERT INTO users (id, name, email, role) VALUES
    ('018f4c2a-0000-7000-8000-000000000001', 'Administrator', 'admin@urbanalert.com',     'ADMIN'),
    ('018f4c2a-0000-7000-8000-000000000002', 'Usuario Dos',   'usuario02@example.com',    'USER'),
    ('018f4c2a-0000-7000-8000-000000000003', 'Usuario Tres',  'usuario03@example.com',    'USER'),
    ('018f4c2a-0000-7000-8000-000000000004', 'Usuario Cuatro','usuario04@example.com',    'USER'),
    ('018f4c2a-0000-7000-8000-000000000005', 'Usuario Cinco', 'usuario05@example.com',    'USER'),
    ('018f4c2a-0000-7000-8000-000000000006', 'Usuario Seis',  'usuario06@example.com',    'USER'),
    ('018f4c2a-0000-7000-8000-000000000007', 'Usuario Siete', 'usuario07@example.com',    'USER'),
    ('018f4c2a-0000-7000-8000-000000000008', 'test-user',     'urbanalerttest@proton.me', 'USER')
ON CONFLICT DO NOTHING;
