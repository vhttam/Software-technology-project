ALTER TABLE points_of_interest ADD is_deleted BIT NOT NULL DEFAULT 0;
ALTER TABLE points_of_interest ADD deleted_at VARCHAR(35);
