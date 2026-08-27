CREATE TABLE requisicao_idempotente (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    client_id VARCHAR(64) NOT NULL,
    chave VARCHAR(200) NOT NULL,
    status VARCHAR(20) NOT NULL,
    resposta_json CLOB,
    criado_em TIMESTAMP NOT NULL,
    expira_em TIMESTAMP NOT NULL,
    CONSTRAINT uq_requisicao_idempotente UNIQUE (client_id, chave)
);
