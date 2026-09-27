UPDATE system_users
SET username = trim(both '.' FROM regexp_replace(
    lower(translate(full_name,
        'ÁČĎÉĚÍŇÓŘŠŤÚŮÝŽáčďéěíňóřšťúůýž',
        'ACDEEINORSTUUYZacdeeinorstuuyz')),
    '[^a-z0-9._-]+', '.', 'g'))
WHERE username !~ '^[a-z0-9._-]{3,64}$';
