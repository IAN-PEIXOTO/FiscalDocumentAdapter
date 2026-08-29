-- Correcoes identificadas apos revisao das cards originais FIS-30/FIS-31
-- (reprocessamento automatico com backoff, e assinatura HMAC do webhook).
ALTER TABLE emissao_assincrona ADD COLUMN tentativas_processamento INT NOT NULL DEFAULT 0;
ALTER TABLE emissao_assincrona ADD COLUMN proxima_tentativa_em TIMESTAMP;

ALTER TABLE cliente_api ADD COLUMN webhook_secret_criptografado VARCHAR(500);
