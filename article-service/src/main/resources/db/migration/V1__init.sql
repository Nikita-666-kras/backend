CREATE TABLE articles (
    id UUID PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    slug VARCHAR(220) NOT NULL UNIQUE,
    short_description VARCHAR(400) NOT NULL,
    content TEXT NOT NULL,
    html_content TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    reading_time INTEGER NOT NULL,
    views BIGINT NOT NULL,
    likes BIGINT NOT NULL,
    author_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE article_media (
    article_id UUID NOT NULL REFERENCES articles(id) ON DELETE CASCADE,
    media_object_name VARCHAR(255) NOT NULL
);

CREATE TABLE article_tags (
    article_id UUID NOT NULL REFERENCES articles(id) ON DELETE CASCADE,
    tag VARCHAR(100) NOT NULL
);

CREATE TABLE article_categories (
    article_id UUID NOT NULL REFERENCES articles(id) ON DELETE CASCADE,
    category VARCHAR(100) NOT NULL
);
