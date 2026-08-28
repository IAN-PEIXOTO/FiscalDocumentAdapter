CREATE TABLE emissor_autorizado (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cnpj VARCHAR(14) NOT NULL,
    client_id VARCHAR(64) NOT NULL,
    criado_em TIMESTAMP NOT NULL,
    CONSTRAINT uq_emissor_autorizado_cnpj UNIQUE (cnpj)
);
