CREATE TABLE cliente_api (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    client_id VARCHAR(64) NOT NULL,
    nome VARCHAR(200) NOT NULL,
    secret_hash_primario VARCHAR(200) NOT NULL,
    secret_hash_secundario VARCHAR(200),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em TIMESTAMP NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_cliente_api_client_id UNIQUE (client_id)
);
