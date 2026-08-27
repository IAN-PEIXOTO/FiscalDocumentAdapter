CREATE TABLE certificado_emissor (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    cnpj VARCHAR(14) NOT NULL,
    alias VARCHAR(200) NOT NULL,
    subject_dn VARCHAR(500) NOT NULL,
    p12_criptografado CLOB NOT NULL,
    senha_criptografada CLOB NOT NULL,
    valido_de TIMESTAMP NOT NULL,
    valido_ate TIMESTAMP NOT NULL,
    criado_em TIMESTAMP NOT NULL,
    atualizado_em TIMESTAMP NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_certificado_emissor_cnpj UNIQUE (cnpj)
);
