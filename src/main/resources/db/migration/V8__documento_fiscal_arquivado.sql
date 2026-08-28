-- FIS-26/34: retencao legal do XML autorizado (minimo 5 anos - aqui simplesmente
-- nao ha rotina de exclusao, entao a retencao e por tempo indeterminado, o que
-- ja satisfaz "no minimo 5 anos").
CREATE TABLE documento_fiscal_arquivado (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    chave_acesso VARCHAR(44) NOT NULL,
    cnpj_emissor VARCHAR(14) NOT NULL,
    tipo_documento VARCHAR(10) NOT NULL,
    numero_protocolo VARCHAR(30),
    xml_assinado_criptografado CLOB NOT NULL,
    data_emissao DATE NOT NULL,
    arquivado_em TIMESTAMP NOT NULL,
    CONSTRAINT uq_documento_fiscal_arquivado_chave UNIQUE (chave_acesso)
);
