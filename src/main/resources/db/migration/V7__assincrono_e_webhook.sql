-- FIS-25: processamento assincrono de emissao (fila) + webhook de notificacao de status.
ALTER TABLE cliente_api ADD COLUMN webhook_url VARCHAR(500);

CREATE TABLE emissao_assincrona (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    client_id VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    status VARCHAR(20) NOT NULL,
    pedido_json CLOB NOT NULL,
    resultado_json CLOB,
    erro_mensagem VARCHAR(2000),
    tentativas_notificacao INT NOT NULL DEFAULT 0,
    criado_em TIMESTAMP NOT NULL,
    atualizado_em TIMESTAMP NOT NULL,
    CONSTRAINT uq_emissao_assincrona UNIQUE (client_id, idempotency_key)
);
