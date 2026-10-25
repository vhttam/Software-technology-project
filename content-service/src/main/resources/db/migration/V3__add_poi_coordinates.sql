ALTER TABLE points_of_interest ADD latitude DECIMAL(10, 8);
ALTER TABLE points_of_interest ADD longitude DECIMAL(11, 8);
ALTER TABLE points_of_interest ADD trigger_radius INT DEFAULT 30;
