-- FIS-43: a chave de idempotencia (client_id + chave) passou a ser compartilhada por mais de
-- um endpoint de emissao (NFe e agora NFC-e) - sem um discriminador, um client_id que
-- reusasse o mesmo Idempotency-Key entre POST /api/v1/nfe e POST /api/v1/nfce receberia de
-- volta a resposta cacheada do outro endpoint (tipo incompativel na desserializacao).
-- tipo_operacao passa a fazer parte da chave unica. Default 'NFE' para linhas existentes -
-- unico consumidor ate aqui era a NFe.
ALTER TABLE requisicao_idempotente ADD COLUMN tipo_operacao VARCHAR(50) NOT NULL DEFAULT 'NFE';

ALTER TABLE requisicao_idempotente DROP CONSTRAINT uq_requisicao_idempotente;
ALTER TABLE requisicao_idempotente ADD CONSTRAINT uq_requisicao_idempotente UNIQUE (client_id, tipo_operacao, chave);
