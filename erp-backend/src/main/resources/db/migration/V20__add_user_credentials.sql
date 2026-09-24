ALTER TABLE system_users ADD COLUMN username VARCHAR(64);
ALTER TABLE system_users ADD COLUMN password_hash VARCHAR(255);

UPDATE system_users
SET username = lower(translate(regexp_replace(full_name, '\\s+', '.', 'g'),
        'ÁČĎÉĚÍŇÓŘŠŤÚŮÝŽáčďéěíňóřšťúůýž', 'ACDEEINORSTUUYZacdeeinorstuuyz')),
    password_hash = '210000:64qnDaTbPv5GEAId8sWiXg==:3711rXWO61hbxNUf00xR5UNgT7I9UnAaAu8RRXboo14=';

ALTER TABLE system_users ALTER COLUMN username SET NOT NULL;
ALTER TABLE system_users ALTER COLUMN password_hash SET NOT NULL;
ALTER TABLE system_users ADD CONSTRAINT system_users_username_unique UNIQUE (username);