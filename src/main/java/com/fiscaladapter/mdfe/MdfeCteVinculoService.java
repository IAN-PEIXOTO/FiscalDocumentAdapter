package com.fiscaladapter.mdfe;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/** Ver javadoc de {@link MdfeCteVinculo} (FIS-61). */
@Service
public class MdfeCteVinculoService {

    private final MdfeCteVinculoRepository repository;

    public MdfeCteVinculoService(MdfeCteVinculoRepository repository) {
        this.repository = repository;
    }

    /**
     * Registra que `chaveMdfe` manifestou cada uma de `chavesCte` - chamado na emissao do MDF-e
     * (`MdfeEmissaoService`), so quando autorizado. Idempotente por chave de CT-e: registrar a
     * mesma chave de CT-e duas vezes (ex.: reprocessamento) nao falha, so ignora a segunda vez.
     */
    public void registrar(String chaveMdfe, List<String> chavesCte) {
        for (String chaveCte : chavesCte) {
            try {
                repository.save(new MdfeCteVinculo(chaveCte, chaveMdfe, Instant.now()));
            } catch (DataIntegrityViolationException jaRegistrado) {
                // CT-e ja vinculado a um MDF-e - nada a fazer
            }
        }
    }

    /** @return a chave do MDF-e que ja manifestou este CT-e, ou null se nenhum. */
    public String mdfeVinculado(String chaveCte) {
        return repository.findByChaveCte(chaveCte).map(MdfeCteVinculo::getChaveMdfe).orElse(null);
    }
}
