-- Add new configuration fields to reforger_server table
ALTER TABLE reforger_server
    ADD cross_platform BIT(1) NOT NULL DEFAULT 1,
    ADD fast_validation BIT(1) NOT NULL DEFAULT 1,
    ADD server_max_view_distance INT NOT NULL DEFAULT 2500,
    ADD server_min_grass_distance INT NOT NULL DEFAULT 50,
    ADD network_view_distance INT NOT NULL DEFAULT 1500;

-- Create table for admins (Steam64 IDs)
CREATE TABLE IF NOT EXISTS reforger_server_admins
(
    reforger_server_id BIGINT       NOT NULL,
    admins             VARCHAR(255) NULL,
    CONSTRAINT fk_reforger_server_admins FOREIGN KEY (reforger_server_id) REFERENCES reforger_server (id) ON DELETE CASCADE
);

-- Create table for supported platforms
CREATE TABLE IF NOT EXISTS reforger_server_supported_platforms
(
    reforger_server_id   BIGINT       NOT NULL,
    supported_platforms  VARCHAR(255) NULL,
    CONSTRAINT fk_reforger_server_platforms FOREIGN KEY (reforger_server_id) REFERENCES reforger_server (id) ON DELETE CASCADE
);

-- Add default supported platforms for existing servers
INSERT INTO reforger_server_supported_platforms (reforger_server_id, supported_platforms)
SELECT id, 'PLATFORM_PC' FROM reforger_server;
