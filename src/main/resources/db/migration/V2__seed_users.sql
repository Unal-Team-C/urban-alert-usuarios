INSERT INTO users (id, name, email, role) VALUES
    ('018f4c2a-0000-7000-8000-000000000001', 'Administrator',   'admin@urbanalert.com',           'ADMIN'),
    ('018f4c2a-0000-7000-8000-000000000002', 'Felipe Caslo',    'facaslo.99@gmail.com',           'USER'),
    ('018f4c2a-0000-7000-8000-000000000003', 'Julian Da Rivas', 'juliandarivas@gmail.com',        'USER'),
    ('018f4c2a-0000-7000-8000-000000000004', 'Luis Gomez Banoy','luisgomezbanoy@gmail.com',       'USER'),
    ('018f4c2a-0000-7000-8000-000000000005', 'Diana Fer',       'dianafer0814@gmail.com',         'USER'),
    ('018f4c2a-0000-7000-8000-000000000006', 'Nelson Ferrucho', 'nelson.ferrucho.unal@gmail.com', 'USER'),
    ('018f4c2a-0000-7000-8000-000000000007', 'Andres Lugo',     'anfellr11@gmail.com',            'USER')
ON CONFLICT DO NOTHING;
