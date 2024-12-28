-- Create the table
CREATE TABLE IF NOT EXISTS message (
                                       id varchar(255) PRIMARY KEY,
                                       message VARCHAR(255) NOT NULL,
                                       author VARCHAR(255) NOT NULL,
                                       created_at TIMESTAMP
);