ALTER TABLE website_pages ADD COLUMN IF NOT EXISTS content TEXT NOT NULL DEFAULT '';

UPDATE website_pages
SET content = CASE slug
    WHEN '/' THEN 'Vítejte na našem webu.'
    WHEN '/katalog' THEN 'Prohlédněte si náš produktový katalog.'
    WHEN '/podzimni-nabidka' THEN 'Podzimní nabídka pro naše zákazníky.'
    WHEN '/kontakt' THEN 'Kontaktujte náš tým.'
    ELSE ''
END
WHERE content = '';