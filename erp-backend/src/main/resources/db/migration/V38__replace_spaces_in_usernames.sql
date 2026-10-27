UPDATE system_users
SET username = replace(username, ' ', '.')
WHERE username LIKE '% %';
