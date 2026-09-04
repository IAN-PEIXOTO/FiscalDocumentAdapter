-- FIS-81: expurgo periodico (IdempotenciaService.expurgarExpiradas) roda um DELETE por
-- expira_em a cada hora - sem indice, vira table scan completo a medida que o historico cresce.
CREATE INDEX idx_requisicao_idempotente_expira_em
    ON requisicao_idempotente (expira_em);
