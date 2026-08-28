CREATE TABLE sequencia_documento (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cnpj_emissor VARCHAR(14) NOT NULL,
    uf VARCHAR(2) NOT NULL,
    serie INT NOT NULL,
    tipo_documento VARCHAR(10) NOT NULL,
    ultimo_numero BIGINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_sequencia_documento UNIQUE (cnpj_emissor, uf, serie, tipo_documento)
);
