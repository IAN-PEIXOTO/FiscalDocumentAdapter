package com.fiscaladapter.sefaz.nfe;

import com.fiscaladapter.assinatura.AssinaturaXmlService;
import com.fiscaladapter.certificado.CertificadoCarregado;
import com.fiscaladapter.certificado.CertificadoDigitalService;
import com.fiscaladapter.certificado.TestCertificadoFactory;
import com.fiscaladapter.documento.nfe.ChaveAcessoService;
import com.fiscaladapter.documento.nfe.NfeXmlGenerator;
import com.fiscaladapter.documento.nfe.NfeXsdValidator;
import com.fiscaladapter.documento.nfe.NotaFiscalEletronica;
import com.fiscaladapter.documento.nfe.NotaFiscalEletronicaTestFixture;
import com.fiscaladapter.documento.nfe.TipoAmbiente;
import com.fiscaladapter.observabilidade.NfeEmissaoMetrics;
import com.fiscaladapter.sefaz.SefazComunicacaoException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Nota Fiscal de exemplo usada aqui tem uf="SP" (NotaFiscalEletronicaTestFixture),
 * cujo SVC de contingencia mapeado e SVC-AN (ver MapeamentoContingenciaSvc).
 */
class EmissaoNfeOrquestradorTest {

    private final ChaveAcessoService chaveAcessoService = new ChaveAcessoService();
    private final NfeXmlGenerator xmlGenerator = new NfeXmlGenerator(chaveAcessoService);
    private final AssinaturaXmlService assinaturaXmlService = new AssinaturaXmlService();
    private final NfeXsdValidator xsdValidator = new NfeXsdValidator();
    private final NfeAutorizacaoClient autorizacaoClient = Mockito.mock(NfeAutorizacaoClient.class);
    private final NfeConsultaProtocoloClient consultaProtocoloClient = Mockito.mock(NfeConsultaProtocoloClient.class);
    private final NfeEpecClient epecClient = Mockito.mock(NfeEpecClient.class);
    private final NfeEmissaoMetrics metrics = new NfeEmissaoMetrics(new SimpleMeterRegistry());

    private final EmissaoNfeOrquestrador orquestrador = new EmissaoNfeOrquestrador(
            chaveAcessoService, xmlGenerator, assinaturaXmlService, xsdValidator, autorizacaoClient,
            consultaProtocoloClient, epecClient, metrics);

    @Test
    void deveAutorizarNaPrimeiraTentativaSemAcionarContingencia() throws Exception {
        NotaFiscalEletronica nfe = NotaFiscalEletronicaTestFixture.notaDeExemplo();
        CertificadoCarregado certificado = certificadoDeTeste();

        when(autorizacaoClient.autorizar(anyString(), eq("SP"), eq(TipoAmbiente.HOMOLOGACAO), any(CertificadoCarregado.class)))
                .thenReturn(AutorizacaoResponse.de("100", "Autorizado o uso da NF-e", "135260000000001", "2026-03-15T10:00:00-03:00"));

        ResultadoEmissaoNfe resultado = orquestrador.emitir(nfe, certificado);

        assertThat(resultado.viaContingencia()).isFalse();
        assertThat(resultado.autorizacao().autorizada()).isTrue();
        verify(autorizacaoClient, times(1)).autorizar(anyString(), eq("SP"), eq(TipoAmbiente.HOMOLOGACAO), any(CertificadoCarregado.class));
        verify(autorizacaoClient, Mockito.never())
                .autorizar(anyString(), anyString(), anyString(), any(TipoAmbiente.class), any(CertificadoCarregado.class));
    }

    @Test
    void deveFazerFailoverParaSvcQuandoEndpointNormalFalhaRepetidamente() throws Exception {
        NotaFiscalEletronica nfe = NotaFiscalEletronicaTestFixture.notaDeExemplo();
        CertificadoCarregado certificado = certificadoDeTeste();

        when(autorizacaoClient.autorizar(anyString(), eq("SP"), eq(TipoAmbiente.HOMOLOGACAO), any(CertificadoCarregado.class)))
                .thenThrow(new SefazComunicacaoException("timeout"));
        when(autorizacaoClient.autorizar(anyString(), eq("SP"), eq("SVC-AN"), eq(TipoAmbiente.HOMOLOGACAO), any(CertificadoCarregado.class)))
                .thenReturn(AutorizacaoResponse.de("100", "Autorizado o uso da NF-e", "135260000000002", "2026-03-15T10:00:00-03:00"));

        ResultadoEmissaoNfe resultado = orquestrador.emitir(nfe, certificado);

        assertThat(resultado.viaContingencia()).isTrue();
        assertThat(resultado.autorizacao().numeroProtocolo()).isEqualTo("135260000000002");
        verify(autorizacaoClient, times(2)).autorizar(anyString(), eq("SP"), eq(TipoAmbiente.HOMOLOGACAO), any(CertificadoCarregado.class));
        verify(autorizacaoClient, times(1)).autorizar(anyString(), eq("SP"), eq("SVC-AN"), eq(TipoAmbiente.HOMOLOGACAO), any(CertificadoCarregado.class));
    }

    @Test
    void deveAcionarEpecQuandoEndpointNormalEContingenciaFalham() throws Exception {
        NotaFiscalEletronica nfe = NotaFiscalEletronicaTestFixture.notaDeExemplo();
        CertificadoCarregado certificado = certificadoDeTeste();

        when(autorizacaoClient.autorizar(anyString(), eq("SP"), eq(TipoAmbiente.HOMOLOGACAO), any(CertificadoCarregado.class)))
                .thenThrow(new SefazComunicacaoException("timeout endpoint normal"));
        when(autorizacaoClient.autorizar(anyString(), eq("SP"), eq("SVC-AN"), eq(TipoAmbiente.HOMOLOGACAO), any(CertificadoCarregado.class)))
                .thenThrow(new SefazComunicacaoException("timeout SVC-AN"));
        when(epecClient.registrar(any(NotaFiscalEletronica.class), anyString(), eq(TipoAmbiente.HOMOLOGACAO), any(CertificadoCarregado.class)))
                .thenReturn(EpecResponse.de("136", "Evento registrado e vinculado a NF-e"));

        ResultadoEmissaoNfe resultado = orquestrador.emitir(nfe, certificado);

        assertThat(resultado.viaContingencia()).isTrue();
        assertThat(resultado.viaEpec()).isTrue();
        assertThat(resultado.autorizacao().autorizada()).isFalse();
        assertThat(resultado.autorizacao().codigoStatus()).isEqualTo("136");
        assertThat(resultado.chaveAcesso().substring(34, 35)).isEqualTo("4"); // tpEmis=4 (EPEC)
    }

    @Test
    void devePropagarErroQuandoEndpointNormalContingenciaEEpecFalham() throws Exception {
        NotaFiscalEletronica nfe = NotaFiscalEletronicaTestFixture.notaDeExemplo();
        CertificadoCarregado certificado = certificadoDeTeste();

        when(autorizacaoClient.autorizar(anyString(), eq("SP"), eq(TipoAmbiente.HOMOLOGACAO), any(CertificadoCarregado.class)))
                .thenThrow(new SefazComunicacaoException("timeout endpoint normal"));
        when(autorizacaoClient.autorizar(anyString(), eq("SP"), eq("SVC-AN"), eq(TipoAmbiente.HOMOLOGACAO), any(CertificadoCarregado.class)))
                .thenThrow(new SefazComunicacaoException("timeout SVC-AN"));
        when(epecClient.registrar(any(NotaFiscalEletronica.class), anyString(), eq(TipoAmbiente.HOMOLOGACAO), any(CertificadoCarregado.class)))
                .thenThrow(new SefazComunicacaoException("timeout EPEC"));

        assertThatThrownBy(() -> orquestrador.emitir(nfe, certificado))
                .isInstanceOf(SefazComunicacaoException.class)
                .hasMessageContaining("EPEC");
    }

    @Test
    void deveRecuperarProtocoloRealQuandoSefazRespondeDuplicidade() throws Exception {
        // FIS-62: cStat 204 significa que a SEFAZ ja processou a chave antes (ex.: reenvio deste
        // orquestrador apos timeout numa tentativa que na verdade foi autorizada) - o adapter deve
        // consultar o protocolo real e devolver sucesso, nao reportar rejeicao.
        NotaFiscalEletronica nfe = NotaFiscalEletronicaTestFixture.notaDeExemplo();
        CertificadoCarregado certificado = certificadoDeTeste();

        when(autorizacaoClient.autorizar(anyString(), eq("SP"), eq(TipoAmbiente.HOMOLOGACAO), any(CertificadoCarregado.class)))
                .thenReturn(AutorizacaoResponse.de("204", "Duplicidade de NF-e", null, null));
        when(consultaProtocoloClient.consultar(anyString(), eq("SP"), eq("SP"), eq(TipoAmbiente.HOMOLOGACAO), any(CertificadoCarregado.class)))
                .thenReturn(ConsultaProtocoloResponse.de("100", "Autorizado o uso da NF-e", "135260000000009", "2026-03-15T10:00:00-03:00"));

        ResultadoEmissaoNfe resultado = orquestrador.emitir(nfe, certificado);

        assertThat(resultado.autorizacao().autorizada()).isTrue();
        assertThat(resultado.autorizacao().numeroProtocolo()).isEqualTo("135260000000009");
        verify(autorizacaoClient, times(1)).autorizar(anyString(), eq("SP"), eq(TipoAmbiente.HOMOLOGACAO), any(CertificadoCarregado.class));
    }

    @Test
    void deveRepassarADenegacaoRealQuandoDuplicidadeEraNaVerdadeUsoDenegado() throws Exception {
        // FIS-106: a consulta pode revelar que a chave, por tras do 204, na verdade foi DENEGADA
        // (110/301/302) - se devolvessemos o "204" original sem repassar o cStat real, o chamador
        // (NfeEmissaoService) nunca acionaria o arquivamento+reserva de numeracao do FIS-100.
        NotaFiscalEletronica nfe = NotaFiscalEletronicaTestFixture.notaDeExemplo();
        CertificadoCarregado certificado = certificadoDeTeste();

        when(autorizacaoClient.autorizar(anyString(), eq("SP"), eq(TipoAmbiente.HOMOLOGACAO), any(CertificadoCarregado.class)))
                .thenReturn(AutorizacaoResponse.de("204", "Duplicidade de NF-e", null, null));
        when(consultaProtocoloClient.consultar(anyString(), eq("SP"), eq("SP"), eq(TipoAmbiente.HOMOLOGACAO), any(CertificadoCarregado.class)))
                .thenReturn(ConsultaProtocoloResponse.de("301", "Uso Denegado: Irregularidade fiscal do emitente", null, null));

        ResultadoEmissaoNfe resultado = orquestrador.emitir(nfe, certificado);

        assertThat(resultado.autorizacao().autorizada()).isFalse();
        assertThat(resultado.autorizacao().denegada()).isTrue();
        assertThat(resultado.autorizacao().codigoStatus()).isEqualTo("301");
    }

    @Test
    void deveConsultarOEndpointDoSvcAoRecuperarDuplicidadeDuranteContingencia() throws Exception {
        // FIS-101: em contingencia, a autorizacao vai para o SVC, mas a consulta de recuperacao de
        // duplicidade (204) mandava sempre para o endpoint da UF - que provavelmente ainda esta
        // fora do ar (motivo de ter acionado a contingencia). A consulta precisa ir para o MESMO
        // endpoint usado na autorizacao (SVC-AN, aqui).
        NotaFiscalEletronica nfe = NotaFiscalEletronicaTestFixture.notaDeExemplo();
        CertificadoCarregado certificado = certificadoDeTeste();

        when(autorizacaoClient.autorizar(anyString(), eq("SP"), eq(TipoAmbiente.HOMOLOGACAO), any(CertificadoCarregado.class)))
                .thenThrow(new SefazComunicacaoException("timeout endpoint normal"));
        when(autorizacaoClient.autorizar(anyString(), eq("SP"), eq("SVC-AN"), eq(TipoAmbiente.HOMOLOGACAO), any(CertificadoCarregado.class)))
                .thenReturn(AutorizacaoResponse.de("204", "Duplicidade de NF-e", null, null));
        when(consultaProtocoloClient.consultar(anyString(), eq("SP"), eq("SVC-AN"), eq(TipoAmbiente.HOMOLOGACAO), any(CertificadoCarregado.class)))
                .thenReturn(ConsultaProtocoloResponse.de("100", "Autorizado o uso da NF-e", "135260000000010", "2026-03-15T10:00:00-03:00"));

        ResultadoEmissaoNfe resultado = orquestrador.emitir(nfe, certificado);

        assertThat(resultado.autorizacao().autorizada()).isTrue();
        assertThat(resultado.autorizacao().numeroProtocolo()).isEqualTo("135260000000010");
        assertThat(resultado.viaContingencia()).isTrue();
        verify(consultaProtocoloClient, times(1))
                .consultar(anyString(), eq("SP"), eq("SVC-AN"), eq(TipoAmbiente.HOMOLOGACAO), any(CertificadoCarregado.class));
        verify(consultaProtocoloClient, org.mockito.Mockito.never())
                .consultar(anyString(), eq("SP"), eq("SP"), eq(TipoAmbiente.HOMOLOGACAO), any(CertificadoCarregado.class));
    }

    @Test
    void deveManterRejeicaoQuandoDuplicidadeNaoEConfirmadaComoAutorizada() throws Exception {
        NotaFiscalEletronica nfe = NotaFiscalEletronicaTestFixture.notaDeExemplo();
        CertificadoCarregado certificado = certificadoDeTeste();

        when(autorizacaoClient.autorizar(anyString(), eq("SP"), eq(TipoAmbiente.HOMOLOGACAO), any(CertificadoCarregado.class)))
                .thenReturn(AutorizacaoResponse.de("204", "Duplicidade de NF-e", null, null));
        // FIS-106: "225" (rejeicao de schema) nao e nem autorizada() nem denegada() - o caso onde a
        // duplicidade de fato deve ser mantida como esta, sem repassar nenhuma outra situacao real.
        when(consultaProtocoloClient.consultar(anyString(), eq("SP"), eq("SP"), eq(TipoAmbiente.HOMOLOGACAO), any(CertificadoCarregado.class)))
                .thenReturn(ConsultaProtocoloResponse.de("225", "Falha no schema XML", null, null));

        ResultadoEmissaoNfe resultado = orquestrador.emitir(nfe, certificado);

        assertThat(resultado.autorizacao().autorizada()).isFalse();
        assertThat(resultado.autorizacao().codigoStatus()).isEqualTo("204");
    }

    @Test
    void chaveDeAcessoDeveRefletirOModeloRealDoDocumento() throws Exception {
        // FIS-43: prepararDocumento tinha TipoDocumentoFiscal.NFE fixo, gerando mod=55 na chave
        // mesmo para um documento com tipoDocumento=NFCE (cujo XML ja teria mod=65 corretamente).
        NotaFiscalEletronica nfce = NotaFiscalEletronicaTestFixture.notaNfceSemDestinatario();
        CertificadoCarregado certificado = certificadoDeTeste();

        when(autorizacaoClient.autorizar(anyString(), eq("SP"), eq(TipoAmbiente.HOMOLOGACAO), any(CertificadoCarregado.class)))
                .thenReturn(AutorizacaoResponse.de("100", "Autorizado o uso da NF-e", "135260000000001", "2026-03-15T10:00:00-03:00"));

        ResultadoEmissaoNfe resultado = orquestrador.emitir(nfce, certificado);

        assertThat(resultado.chaveAcesso().substring(20, 22)).isEqualTo("65");
    }

    private CertificadoCarregado certificadoDeTeste() throws Exception {
        char[] senha = "senha123".toCharArray();
        byte[] p12 = TestCertificadoFactory.gerarP12("12345678000199", senha,
                Date.from(Instant.now().minus(Duration.ofDays(1))), Date.from(Instant.now().plus(Duration.ofDays(365))));
        return new CertificadoDigitalService().carregar(TestCertificadoFactory.comoStream(p12), senha);
    }
}
