-- Proyección de lectura propia de evaluation-query-api (esquema consulta).
-- No tiene llaves foráneas hacia public: los identificadores de origen se copian como referencias.
-- Query es el único escritor de estas tablas. La sincronización y el backfill se implementan por separado.

-- Se necesita registrar solicitantes aunque todavía no tengan evaluaciones para que HU15 pueda distinguir
-- un historial vacío (200) de un solicitante inexistente (404), sin consultar public.solicitante.
CREATE TABLE proyeccion_solicitante (
    id_solicitante BIGINT PRIMARY KEY,
    CONSTRAINT ck_proyeccion_solicitante_id CHECK (id_solicitante > 0)
);

-- Snapshot del resumen de evaluación. Los NULL conservan datos históricos anteriores a HU12 y a la saga HU14.
CREATE TABLE proyeccion_evaluacion (
    id_evaluacion     BIGINT       PRIMARY KEY,
    id_solicitante    BIGINT       NOT NULL,
    fecha_evaluacion  TIMESTAMP    NOT NULL,
    score_total       INTEGER      NULL,
    categoria         VARCHAR(10)  NULL,
    version_politica  INTEGER      NULL,
    estado            VARCHAR(10)  NULL,
    CONSTRAINT fk_proyeccion_evaluacion_solicitante
        FOREIGN KEY (id_solicitante) REFERENCES proyeccion_solicitante (id_solicitante),
    CONSTRAINT ck_proyeccion_evaluacion_id CHECK (id_evaluacion > 0),
    CONSTRAINT ck_proyeccion_evaluacion_categoria
        CHECK (categoria IS NULL OR categoria IN ('BAJO', 'MEDIO', 'ALTO')),
    CONSTRAINT ck_proyeccion_evaluacion_version_politica
        CHECK (version_politica IS NULL OR version_politica > 0),
    CONSTRAINT ck_proyeccion_evaluacion_estado
        CHECK (estado IS NULL OR estado IN ('PENDIENTE', 'COMPLETADA'))
);

CREATE INDEX ix_proyeccion_evaluacion_solicitante_fecha_id
    ON proyeccion_evaluacion (id_solicitante, fecha_evaluacion DESC, id_evaluacion DESC);

-- Snapshot inmutable de cada factor aplicado. Conserva el detalle de origen para mantener un orden estable y
-- no consulta la regla actual, que puede cambiar después de la evaluación.
CREATE TABLE proyeccion_factor_evaluacion (
    id_evaluacion             BIGINT       NOT NULL,
    orden                     INTEGER      NOT NULL,
    id_detalle_origen         BIGINT       NOT NULL,
    id_regla                  BIGINT       NOT NULL,
    variable                  VARCHAR(30)  NOT NULL,
    operador_aplicado         VARCHAR(2)   NOT NULL,
    valor_condicion_aplicado  VARCHAR(255) NOT NULL,
    puntaje_regla_aplicado    INTEGER      NOT NULL,
    condicion_cumplida        BOOLEAN      NOT NULL,
    contribucion              INTEGER      NOT NULL,
    CONSTRAINT pk_proyeccion_factor_evaluacion PRIMARY KEY (id_evaluacion, orden),
    CONSTRAINT fk_proyeccion_factor_evaluacion
        FOREIGN KEY (id_evaluacion) REFERENCES proyeccion_evaluacion (id_evaluacion),
    CONSTRAINT uq_proyeccion_factor_detalle UNIQUE (id_evaluacion, id_detalle_origen),
    CONSTRAINT ck_proyeccion_factor_orden CHECK (orden > 0),
    CONSTRAINT ck_proyeccion_factor_detalle_origen CHECK (id_detalle_origen > 0),
    CONSTRAINT ck_proyeccion_factor_regla CHECK (id_regla > 0),
    CONSTRAINT ck_proyeccion_factor_operador
        CHECK (operador_aplicado IN ('=', '>', '>=', '<', '<=')),
    CONSTRAINT ck_proyeccion_factor_contribucion CHECK (
        (condicion_cumplida AND contribucion = puntaje_regla_aplicado)
        OR (NOT condicion_cumplida AND contribucion = 0)
    )
);
