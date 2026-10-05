CREATE TABLE IF NOT EXISTS mesas (mesa_id VARCHAR(40) PRIMARY KEY, capacidad INT NOT NULL);
MERGE INTO mesas KEY(mesa_id) VALUES ('mesa-01',2),('mesa-02',4),('mesa-03',4),('mesa-04',6),('mesa-05',8);
CREATE TABLE IF NOT EXISTS asignaciones (
 asignacion_id VARCHAR(40) PRIMARY KEY, reserva_id VARCHAR(40) UNIQUE NOT NULL,
 mesa_id VARCHAR(40) NOT NULL REFERENCES mesas(mesa_id), fecha DATE NOT NULL,
 hora_inicio TIME NOT NULL, hora_fin TIME NOT NULL);
