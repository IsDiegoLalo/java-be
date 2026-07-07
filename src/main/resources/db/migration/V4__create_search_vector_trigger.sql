-- Function to update the search_vector column on article insert/update
CREATE OR REPLACE FUNCTION articles_search_vector_update() RETURNS trigger AS $$
BEGIN
    NEW.search_vector := to_tsvector('english', coalesce(NEW.title, '') || ' ' || coalesce(NEW.body, ''));
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger to auto-update search_vector on INSERT or UPDATE of title/body
CREATE TRIGGER trg_articles_search_vector_update
    BEFORE INSERT OR UPDATE OF title, body ON articles
    FOR EACH ROW
    EXECUTE FUNCTION articles_search_vector_update();
