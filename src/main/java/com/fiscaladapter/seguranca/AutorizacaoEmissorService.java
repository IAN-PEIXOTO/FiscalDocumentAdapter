package com.fiscaladapter.seguranca;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Isolamento multi-tenant (FIS-10): cada CNPJ emissor pertence a um unico
 * client_id, o primeiro que o registrou. Chamado tanto no cadastro do
 * certificado (garantirAutorizacao - reivindica o CNPJ na primeira vez, ou
 * confirma que quem esta reenviando e o dono) quanto em toda operacao
 * subsequente sobre esse CNPJ (validarAcesso - so verifica, nunca reivindica).
 */
@Service
public class AutorizacaoEmissorService {

    private final EmissorAutorizadoRepository repository;

    public AutorizacaoEmissorService(EmissorAutorizadoRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void garantirAutorizacao(String clientId, String cnpj) {
        repository.findByCnpj(cnpj).ifPresentOrElse(
                registro -> validarDono(registro, clientId, cnpj),
                () -> repository.save(new EmissorAutorizado(cnpj, clientId)));
    }

    /**
     * Nao lanca excecao quando o CNPJ nunca foi reivindicado por ninguem -
     * nesse caso o problema real e "certificado nao cadastrado" (o chamador
     * seguinte, tipicamente CertificadoEmissorService, e quem deve reportar
     * isso), nao "acesso negado". So bloqueia quando outro client_id ja e o
     * dono.
     */
    @Transactional(readOnly = true)
    public void validarAcesso(String clientId, String cnpj) {
        repository.findByCnpj(cnpj).ifPresent(registro -> validarDono(registro, clientId, cnpj));
    }

    private void validarDono(EmissorAutorizado registro, String clientId, String cnpj) {
        if (!registro.getClientId().equals(clientId)) {
            throw new EmissorNaoAutorizadoException(cnpj);
        }
    }
}
