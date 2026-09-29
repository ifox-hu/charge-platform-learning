-- Apply once to the existing charge_platform database before deploying the coordinate fields.
-- AMap/browser coordinates used by the clients are stored as GCJ-02.
ALTER TABLE station
    ADD COLUMN latitude DECIMAL(10, 7) NULL,
    ADD COLUMN longitude DECIMAL(10, 7) NULL,
    ADD COLUMN coordinate_type VARCHAR(16) NULL;

CREATE INDEX idx_station_coordinates ON station (latitude, longitude);
