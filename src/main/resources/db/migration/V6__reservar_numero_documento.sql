-- FIS-23: sequencia_documento deixa de ser um contador (ultimo_numero) e
-- passa a ser uma linha por numero de documento ja reservado/utilizado - a
-- constraint unica agora inclui o proprio numero, que e informado pelo
-- cliente (compativel com a API ACBr) em vez de gerado pelo adapter.
ALTER TABLE sequencia_documento DROP CONSTRAINT uq_sequencia_documento;
ALTER TABLE sequencia_documento DROP COLUMN ultimo_numero;
ALTER TABLE sequencia_documento DROP COLUMN version;
ALTER TABLE sequencia_documento ADD COLUMN numero BIGINT NOT NULL;
ALTER TABLE sequencia_documento ADD CONSTRAINT uq_sequencia_documento UNIQUE (cnpj_emissor, uf, serie, tipo_documento, numero);
