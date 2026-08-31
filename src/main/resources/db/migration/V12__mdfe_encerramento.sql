-- FIS-54: registra o encerramento de um MDF-e (a SEFAZ nao devolve esse status na
-- consulta de situacao usada por este adapter) para bloquear cancelamento apos o
-- encerramento (AC2) e informar a consulta (AC3).
CREATE TABLE mdfe_encerramento (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    chave_acesso VARCHAR(44) NOT NULL,
    codigo_municipio_encerramento VARCHAR(7) NOT NULL,
    data_encerramento DATE NOT NULL,
    encerrado_em TIMESTAMP NOT NULL,
    CONSTRAINT uq_mdfe_encerramento_chave UNIQUE (chave_acesso)
);
