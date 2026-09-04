package com.fiscaladapter.mdfe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/** Ver javadoc de {@link MdfeCteVinculo} (FIS-61). */
@Service
public class MdfeCteVinculoService {

    private static final Logger log = LoggerFactory.getLogger(MdfeCteVinculoService.class);

    private final MdfeCteVinculoRepository repository;

    public MdfeCteVinculoService(MdfeCteVinculoRepository repository) {
        this.repository = repository;
    }

    /**
     * Registra que `chaveMdfe` manifestou cada uma de `chavesCte` - chamado na emissao do MDF-e
     * (`MdfeEmissaoService`), so quando autorizado. Idempotente por chave de CT-e: registrar a
     * mesma chave de CT-e duas vezes (ex.: reprocessamento) nao falha, so ignora a segunda vez.
     *
     * FIS-86: DataIntegrityViolationException tambem cobre violacao de NOT NULL/CHECK/tamanho de
     * coluna, nao so a constraint unica esperada - loga antes de ignorar para nao mascarar um
     * `chaveCte` malformado como se fosse um caso normal de idempotencia.
     */
    public void registrar(String chaveMdfe, List<String> chavesCte) {
        for (String chaveCte : chavesCte) {
            try {
                repository.save(new MdfeCteVinculo(chaveCte, chaveMdfe, Instant.now()));
            } catch (DataIntegrityViolationException jaRegistrado) {
                log.warn("Falha ao registrar vinculo CT-e->MDF-e (tratado como ja registrado, mas pode ser outra "
                                + "violacao de integridade) - chaveCte={}, chaveMdfe={}: {}",
                        chaveCte, chaveMdfe, jaRegistrado.getMessage());
            }
        }
    }

    /** @return a chave do MDF-e que ja manifestou este CT-e, ou null se nenhum. */
    public String mdfeVinculado(String chaveCte) {
        return repository.findByChaveCte(chaveCte).map(MdfeCteVinculo::getChaveMdfe).orElse(null);
    }
}
