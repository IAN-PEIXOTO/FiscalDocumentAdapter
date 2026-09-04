-- FIS-80: EmissaoAssincronaWorker.buscarElegiveis roda a cada ciclo de poll (padrao ~5s)
-- filtrando por status e proxima_tentativa_em, ordenando por criado_em - sem indice, vira table
-- scan completo a medida que o historico de emissoes assincronas cresce.
CREATE INDEX idx_emissao_assincrona_status_proxima_tentativa
    ON emissao_assincrona (status, proxima_tentativa_em, criado_em);
