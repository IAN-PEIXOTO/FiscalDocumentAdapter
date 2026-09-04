package com.fiscaladapter.certificado;

import com.fiscaladapter.seguranca.AutorizacaoEmissorService;
import com.fiscaladapter.seguranca.CriptografiaEmRepousoService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.Base64;

/**
 * Registro e recuperacao de certificados A1 por emissor (FIS-2). Substitui o
 * antigo fluxo em que o .p12 e a senha eram enviados a cada chamada de
 * emissao: agora o cliente registra o certificado uma vez (POST
 * /api/v1/certificados) e as emissoes seguintes so precisam do CNPJ do
 * emissor, ja presente no proprio payload da NFe.
 *
 * Multi-tenant (FIS-10): registrar() e carregar() sempre exigem o client_id
 * de quem esta chamando e passam por AutorizacaoEmissorService, garantindo
 * que um client_id nao possa emitir/consultar/cadastrar por cima do CNPJ de
 * outro tenant so por conhecer o numero.
 */
@Service
public class CertificadoEmissorService {

    private final CertificadoEmissorRepository repository;
    private final CertificadoDigitalService certificadoDigitalService;
    private final CriptografiaEmRepousoService criptografiaEmRepousoService;
    private final AutorizacaoEmissorService autorizacaoEmissorService;

    public CertificadoEmissorService(CertificadoEmissorRepository repository,
                                      CertificadoDigitalService certificadoDigitalService,
                                      CriptografiaEmRepousoService criptografiaEmRepousoService,
                                      AutorizacaoEmissorService autorizacaoEmissorService) {
        this.repository = repository;
        this.certificadoDigitalService = certificadoDigitalService;
        this.criptografiaEmRepousoService = criptografiaEmRepousoService;
        this.autorizacaoEmissorService = autorizacaoEmissorService;
    }

    @Transactional
    public CertificadoInfo registrar(String clientId, byte[] arquivoP12, char[] senha) {
        CertificadoCarregado certificado = certificadoDigitalService.carregar(new ByteArrayInputStream(arquivoP12), senha);
        CertificadoInfo info = certificado.info();

        // FIS-85: certificado sem o RDN do OID ICP-Brasil (2.16.76.1.3.3) - ex.: certificado de
        // teste/homologacao generico - faria info.cnpj() vir null, violando a constraint NOT NULL
        // da coluna e estourando um 500 generico em vez de um erro de validacao claro.
        if (info.cnpj() == null) {
            throw new CertificadoInvalidoException(
                    "Certificado nao segue o padrao ICP-Brasil - nao foi possivel extrair o CNPJ do titular");
        }

        autorizacaoEmissorService.garantirAutorizacao(clientId, info.cnpj());

        String p12Criptografado = Base64.getEncoder()
                .encodeToString(criptografiaEmRepousoService.criptografarBytes(arquivoP12));
        String senhaCriptografada = criptografiaEmRepousoService.criptografarSensivel(senha);

        repository.findByCnpj(info.cnpj()).ifPresentOrElse(
                existente -> existente.atualizarDadosDoCertificado(info, p12Criptografado, senhaCriptografada),
                () -> repository.save(new CertificadoEmissor(info.cnpj(), info, p12Criptografado, senhaCriptografada)));

        return info;
    }

    @Transactional(readOnly = true)
    public CertificadoCarregado carregar(String clientId, String cnpj) {
        autorizacaoEmissorService.validarAcesso(clientId, cnpj);

        CertificadoEmissor registro = repository.findByCnpj(cnpj)
                .orElseThrow(() -> new CertificadoNaoEncontradoException(cnpj));

        byte[] arquivoP12 = criptografiaEmRepousoService
                .descriptografarBytes(Base64.getDecoder().decode(registro.getP12Criptografado()));
        char[] senha = criptografiaEmRepousoService.descriptografarSensivel(registro.getSenhaCriptografada());

        try {
            CertificadoCarregado certificado = certificadoDigitalService.carregar(new ByteArrayInputStream(arquivoP12), senha);
            certificadoDigitalService.validarNaoExpirado(certificado.info());
            return certificado;
        } finally {
            Arrays.fill(senha, '\0');
        }
    }

    @Transactional
    public void remover(String clientId, String cnpj) {
        autorizacaoEmissorService.validarAcesso(clientId, cnpj);
        repository.deleteByCnpj(cnpj);
    }
}
