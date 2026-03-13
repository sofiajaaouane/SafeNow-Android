-- Reference SQLite schema for SafeNow (mirrors Room entities).
-- Room creates tables automatically; this file is for documentation or manual use.

CREATE TABLE IF NOT EXISTS user (
    id_user TEXT NOT NULL PRIMARY KEY,
    nom TEXT NOT NULL,
    prenom TEXT NOT NULL,
    num_tel TEXT NOT NULL UNIQUE,
    password TEXT NOT NULL,
    email TEXT UNIQUE,
    description TEXT,
    blood_type TEXT
);

CREATE TABLE IF NOT EXISTS alert (
    id_alert TEXT NOT NULL PRIMARY KEY,
    created_at TEXT DEFAULT (datetime('current_timestamp')),
    type_alert TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS amitier (
    id_user1 TEXT NOT NULL,
    id_user2 TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'PENDING',
    PRIMARY KEY (id_user1, id_user2),
    FOREIGN KEY (id_user1) REFERENCES user(id_user) ON DELETE CASCADE,
    FOREIGN KEY (id_user2) REFERENCES user(id_user) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS declaration_alert (
    id_user TEXT NOT NULL,
    id_alert TEXT NOT NULL,
    localisation TEXT,
    latitude REAL,
    longitude REAL,
    status TEXT,
    created_at TEXT DEFAULT (datetime('current_timestamp')),
    PRIMARY KEY (id_user, id_alert),
    FOREIGN KEY (id_user) REFERENCES user(id_user) ON DELETE CASCADE,
    FOREIGN KEY (id_alert) REFERENCES alert(id_alert) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS disease (
    id_disease TEXT NOT NULL PRIMARY KEY,
    name TEXT NOT NULL,
    description TEXT,
    created_at TEXT DEFAULT (datetime('current_timestamp')),
    id_user TEXT NOT NULL,
    FOREIGN KEY (id_user) REFERENCES user(id_user) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS emergency_group (
    id_group TEXT NOT NULL PRIMARY KEY,
    name TEXT NOT NULL,
    description TEXT,
    sos_global INTEGER NOT NULL DEFAULT 1,
    created_at TEXT DEFAULT (datetime('current_timestamp')),
    id_admin TEXT NOT NULL,
    FOREIGN KEY (id_admin) REFERENCES user(id_user) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS group_member (
    id_group TEXT NOT NULL,
    id_user TEXT NOT NULL,
    PRIMARY KEY (id_group, id_user),
    FOREIGN KEY (id_group) REFERENCES emergency_group(id_group) ON DELETE CASCADE,
    FOREIGN KEY (id_user) REFERENCES user(id_user) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS items (
    id_item TEXT NOT NULL PRIMARY KEY,
    type TEXT NOT NULL,
    name TEXT NOT NULL,
    description TEXT,
    created_at TEXT DEFAULT (datetime('current_timestamp')),
    id_group TEXT NOT NULL,
    FOREIGN KEY (id_group) REFERENCES emergency_group(id_group) ON DELETE CASCADE
);
