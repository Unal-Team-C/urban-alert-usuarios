CREATE TABLE users (
    id    UUID         NOT NULL,
    name  VARCHAR(200) NOT NULL,
    email VARCHAR(300) NOT NULL,
    role  VARCHAR(20)  NOT NULL,

    CONSTRAINT pk_users PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uix_users_email ON users (email);
