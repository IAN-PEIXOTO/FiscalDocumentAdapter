package com.fiscaladapter.api.nfe;

import com.fiscaladapter.certificado.CertificadoCarregado;
import com.fiscaladapter.certificado.CertificadoEmissorService;
import com.fiscaladapter.documento.TipoDocumentoFiscal;
import com.fiscaladapter.documento.nfe.NotaFiscalEletronica;
import com.fiscaladapter.documento.nfe.danfe.DadosImpressaoDanfe;
import com.fiscaladapter.documento.nfe.danfe.DanfeGenerator;
import com.fiscaladapter.documento.nfe.danfe.OrientacaoDanfe;
import com.fiscaladapter.documento.nfe.rvn.RegraNegocioService;
import com.fiscaladapter.numeracao.NumeracaoSequencialService;
import com.fiscaladapter.retencao.RetencaoDocumentoFiscalService;
import com.fiscaladapter.sefaz.nfe.EmissaoNfeOrquestrador;
import com.fiscaladapter.sefaz.nfe.ResultadoEmissaoNfe;
import com.fiscaladapter.sefaz.rejeicao.CatalogoRejeicaoSefaz;
import com.fiscaladapter.sefaz.rejeicao.RejeicaoSefaz;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Base64;

/**
 * Pipeline completo de emissao de uma NFe (mapeamento, RVN, certificado,
 * transmissao, reserva de numero, DANFE) - extraido do NfeController (FIS-25)
 * para ser reutilizado tanto pelo endpoint sincrono (POST /api/v1/nfe)
 * quanto pelo worker de processamento assincrono
 * (EmissaoAssincronaWorker/POST /api/v1/nfe/assincrono), sem duplicar logica.
 */
@Service
public class NfeEmissaoService {

    private final NfeRequestMapper mapper;
    private final CertificadoEmissorService certificadoEmissorService;
    private final RegraNegocioService regraNegocioService;
    private final EmissaoNfeOrquestrador emissaoNfeOrquestrador;
    private final DanfeGenerator danfeGenerator;
    private final NumeracaoSequencialService numeracaoSequencialService;
    private final RetencaoDocumentoFiscalService retencaoDocumentoFiscalService;

    public NfeEmissaoService(NfeRequestMapper mapper, CertificadoEmissorService certificadoEmissorService,
                              RegraNegocioService regraNegocioService, EmissaoNfeOrquestrador emissaoNfeOrquestrador,
                              DanfeGenerator danfeGenerator, NumeracaoSequencialService numeracaoSequencialService,
                              RetencaoDocumentoFiscalService retencaoDocumentoFiscalService) {
        this.mapper = mapper;
        this.certificadoEmissorService = certificadoEmissorService;
        this.regraNegocioService = regraNegocioService;
        this.emissaoNfeOrquestrador = emissaoNfeOrquestrador;
        this.danfeGenerator = danfeGenerator;
        this.numeracaoSequencialService = numeracaoSequencialService;
        this.retencaoDocumentoFiscalService = retencaoDocumentoFiscalService;
    }

    public NfeResponse processar(NfePedidoEmissaoRequest documento, String clientId) {
        NotaFiscalEletronica nfe = mapper.paraDominio(documento);

        // dest e opcional no DTO so para a NFC-e reaproveitar o mesmo mapeamento (FIS-17) -
        // este endpoint e exclusivamente NFe (modelo 55), que sempre exige destinatario.
        if (nfe.destinatario() == null) {
            throw new IllegalArgumentException("infNFe.dest e obrigatorio para NFe (modelo 55)");
        }

        regraNegocioService.validar(nfe);

        CertificadoCarregado certificadoCarregado =
                certificadoEmissorService.carregar(clientId, nfe.emitente().cnpjSemMascara());

        ResultadoEmissaoNfe resultado = emissaoNfeOrquestrador.emitir(nfe, certificadoCarregado);

        // so reserva o numero quando o documento efetivamente "valeu" perante o fisco (FIS-23) -
        // uma submissao rejeitada nao consome o numero, o ERP pode corrigir e reenviar o mesmo.
        if (resultado.autorizacao().autorizada() || resultado.viaEpec()) {
            numeracaoSequencialService.reservar(nfe.emitente().cnpjSemMascara(), nfe.identificacao().uf(),
                    nfe.identificacao().serie(), TipoDocumentoFiscal.NFE, nfe.identificacao().numero());

            // retencao legal do XML autorizado, no minimo 5 anos (FIS-26/34)
            retencaoDocumentoFiscalService.arquivar(resultado.chaveAcesso(), nfe.emitente().cnpjSemMascara(),
                    TipoDocumentoFiscal.NFE, resultado.autorizacao().numeroProtocolo(), resultado.xmlAssinado(),
                    nfe.identificacao().dataEmissao());
        }

        // so classifica como "rejeicao" uma negativa de fato - EPEC libera a nota provisoriamente
        // (autorizada=false, mas nao e um erro do cliente nem exige nenhuma acao dele agora).
        RejeicaoSefaz rejeicao = (!resultado.autorizacao().autorizada() && !resultado.viaEpec())
                ? CatalogoRejeicaoSefaz.classificar(resultado.autorizacao().codigoStatus(), resultado.autorizacao().motivo())
                : null;

        return new NfeResponse(resultado.chaveAcesso(), resultado.xmlAssinado(),
                resultado.autorizacao().autorizada(), resultado.autorizacao().codigoStatus(),
                resultado.autorizacao().motivo(), resultado.autorizacao().numeroProtocolo(),
                resultado.viaContingencia(), resultado.viaEpec(), gerarDanfeSePermitido(nfe, resultado),
                rejeicao != null ? rejeicao.mensagem() : null, rejeicao != null ? rejeicao.categoria() : null);
    }

    /**
     * O DANFE so tem validade legal para acompanhar a mercadoria quando a NFe
     * foi autorizada ou ao menos liberada provisoriamente via EPEC - nao faz
     * sentido (e seria enganoso) gerar o documento para uma nota rejeitada.
     */
    private String gerarDanfeSePermitido(NotaFiscalEletronica nfe, ResultadoEmissaoNfe resultado) {
        if (!resultado.autorizacao().autorizada() && !resultado.viaEpec()) {
            return null;
        }

        OffsetDateTime dataHoraAutorizacao = resultado.autorizacao().dhRecbto() != null
                ? OffsetDateTime.parse(resultado.autorizacao().dhRecbto())
                : null;

        DadosImpressaoDanfe dados = new DadosImpressaoDanfe(
                OrientacaoDanfe.RETRATO,
                resultado.viaContingencia() || resultado.viaEpec(),
                resultado.autorizacao().numeroProtocolo(),
                dataHoraAutorizacao);

        byte[] pdf = danfeGenerator.gerar(nfe, resultado.chaveAcesso(), dados);
        return Base64.getEncoder().encodeToString(pdf);
    }
}
