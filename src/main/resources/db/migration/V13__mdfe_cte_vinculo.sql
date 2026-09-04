-- FIS-61: indice de vinculo CT-e -> MDF-e, gravado no momento da emissao do MDF-e.
-- Substitui a varredura O(n) que descriptografava todos os MDF-e arquivados de um emissor a
-- cada consulta/cancelamento de um unico CT-e (CteConsultaController.mdfeVinculado).
CREATE TABLE mdfe_cte_vinculo (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    chave_cte VARCHAR(44) NOT NULL,
    chave_mdfe VARCHAR(44) NOT NULL,
    criado_em TIMESTAMP NOT NULL,
    CONSTRAINT uq_mdfe_cte_vinculo_chave_cte UNIQUE (chave_cte)
);
