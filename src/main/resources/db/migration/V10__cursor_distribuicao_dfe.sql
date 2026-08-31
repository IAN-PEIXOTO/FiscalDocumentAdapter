-- FIS-40: cursor de NSU por CNPJ para a consulta de NF-e destinadas (NFeDistribuicaoDFe).
-- A SEFAZ exige que a consulta por ultNSU seja incremental (recomeca do ultimo NSU
-- consumido, nunca do zero) e limita a frequencia de chamadas (consumo indevido -
-- cStat 656 - se consultado com frequencia excessiva) - guardamos aqui tanto o
-- progresso (ultimo_nsu) quanto o horario da ultima consulta bem-sucedida.
CREATE TABLE distribuicao_dfe_cursor (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cnpj VARCHAR(14) NOT NULL,
    ultimo_nsu VARCHAR(15) NOT NULL DEFAULT '000000000000000',
    consultado_em TIMESTAMP,
    CONSTRAINT uq_distribuicao_dfe_cursor_cnpj UNIQUE (cnpj)
);
