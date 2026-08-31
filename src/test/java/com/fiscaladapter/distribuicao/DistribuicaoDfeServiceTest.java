package com.fiscaladapter.distribuicao;

import com.fiscaladapter.certificado.CertificadoCarregado;
import com.fiscaladapter.documento.nfe.TipoAmbiente;
import com.fiscaladapter.sefaz.SefazComunicacaoException;
import com.fiscaladapter.sefaz.nfe.NfeDistribuicaoDfeClient;
import com.fiscaladapter.sefaz.nfe.ResumoNfeDistribuicao;
import com.fiscaladapter.sefaz.nfe.RetornoDistribuicaoDfe;
import com.fiscaladapter.sefaz.nfe.SituacaoNfeDistribuicao;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DistribuicaoDfeServiceTest {

    private static final String CNPJ = "18715523000105";

    private final DistribuicaoDfeCursorRepository repository = Mockito.mock(DistribuicaoDfeCursorRepository.class);
    private final NfeDistribuicaoDfeClient client = Mockito.mock(NfeDistribuicaoDfeClient.class);
    private final DistribuicaoDfeService service = new DistribuicaoDfeService(repository, client);
    private final CertificadoCarregado certificado = Mockito.mock(CertificadoCarregado.class);

    @Test
    void deveConsultarDoZeroQuandoNuncaConsultouAntes() {
        when(repository.findByCnpj(CNPJ)).thenReturn(Optional.empty());
        when(client.consultarPorNsu(eq(CNPJ), eq("SP"), eq("000000000000000"), any(), any()))
                .thenReturn(new RetornoDistribuicaoDfe("137", "Nenhum documento localizado",
                        "000000000000010", "000000000000010", List.of()));

        List<NfeDestinadaResponse> resultado = service.consultarDestinadas(CNPJ, "SP", TipoAmbiente.HOMOLOGACAO, certificado);

        assertThat(resultado).isEmpty();
        verify(client, times(1)).consultarPorNsu(anyString(), anyString(), eq("000000000000000"), any(), any());
        verify(repository, times(1)).save(any(DistribuicaoDfeCursor.class));
    }

    @Test
    void deveContinuarDoUltimoNsuConsumido() {
        DistribuicaoDfeCursor cursorExistente = new DistribuicaoDfeCursor(CNPJ);
        cursorExistente.avancar("000000000000050", Instant.now().minus(java.time.Duration.ofHours(2)));
        when(repository.findByCnpj(CNPJ)).thenReturn(Optional.of(cursorExistente));
        when(client.consultarPorNsu(eq(CNPJ), eq("SP"), eq("000000000000050"), any(), any()))
                .thenReturn(new RetornoDistribuicaoDfe("137", "Nenhum documento localizado",
                        "000000000000050", "000000000000050", List.of()));

        service.consultarDestinadas(CNPJ, "SP", TipoAmbiente.HOMOLOGACAO, certificado);

        verify(client, times(1)).consultarPorNsu(anyString(), anyString(), eq("000000000000050"), any(), any());
    }

    @Test
    void deveBloquearConsultaMuitoFrequente() {
        DistribuicaoDfeCursor cursorRecente = new DistribuicaoDfeCursor(CNPJ);
        cursorRecente.avancar("000000000000050", Instant.now().minus(java.time.Duration.ofMinutes(10)));
        when(repository.findByCnpj(CNPJ)).thenReturn(Optional.of(cursorRecente));

        assertThatThrownBy(() -> service.consultarDestinadas(CNPJ, "SP", TipoAmbiente.HOMOLOGACAO, certificado))
                .isInstanceOf(ConsultaDistribuicaoDfeMuitoFrequenteException.class);

        verify(client, times(0)).consultarPorNsu(anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    void devePaginarAteEsgotarOLoteDisponivel() {
        when(repository.findByCnpj(CNPJ)).thenReturn(Optional.empty());
        ResumoNfeDistribuicao resumo1 = resumoDe("chave1");
        ResumoNfeDistribuicao resumo2 = resumoDe("chave2");

        when(client.consultarPorNsu(eq(CNPJ), eq("SP"), eq("000000000000000"), any(), any()))
                .thenReturn(new RetornoDistribuicaoDfe("138", "Documento localizado",
                        "000000000000010", "000000000000020", List.of(resumo1)));
        when(client.consultarPorNsu(eq(CNPJ), eq("SP"), eq("000000000000010"), any(), any()))
                .thenReturn(new RetornoDistribuicaoDfe("138", "Documento localizado",
                        "000000000000020", "000000000000020", List.of(resumo2)));

        List<NfeDestinadaResponse> resultado = service.consultarDestinadas(CNPJ, "SP", TipoAmbiente.HOMOLOGACAO, certificado);

        assertThat(resultado).extracting(NfeDestinadaResponse::chaveAcesso).containsExactly("chave1", "chave2");
        verify(client, times(2)).consultarPorNsu(anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    void deveLancarExcecaoQuandoSefazRecusaAConsulta() {
        when(repository.findByCnpj(CNPJ)).thenReturn(Optional.empty());
        when(client.consultarPorNsu(anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(new RetornoDistribuicaoDfe("656", "Rejeicao: Consumo Indevido", null, null, List.of()));

        assertThatThrownBy(() -> service.consultarDestinadas(CNPJ, "SP", TipoAmbiente.HOMOLOGACAO, certificado))
                .isInstanceOf(SefazComunicacaoException.class);
    }

    @Test
    void deveCalcularPrazoDeManifestacaoComAlertaProximoDoVencimento() {
        OffsetDateTime dataAutorizacao = OffsetDateTime.now(ZoneOffset.of("-03:00"))
                .minusDays(DistribuicaoDfeService.PRAZO_MANIFESTACAO_DIAS - 5);
        ResumoNfeDistribuicao resumo = new ResumoNfeDistribuicao("chaveProxima", "11222333000181", "Fornecedor",
                dataAutorizacao, dataAutorizacao, new BigDecimal("100.00"), SituacaoNfeDistribuicao.AUTORIZADA);

        when(repository.findByCnpj(CNPJ)).thenReturn(Optional.empty());
        when(client.consultarPorNsu(anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(new RetornoDistribuicaoDfe("138", "Documento localizado",
                        "000000000000001", "000000000000001", List.of(resumo)));

        NfeDestinadaResponse resposta = service.consultarDestinadas(CNPJ, "SP", TipoAmbiente.HOMOLOGACAO, certificado).get(0);

        assertThat(resposta.prazoExpirado()).isFalse();
        assertThat(resposta.alertaProximoDoPrazo()).isTrue();
        assertThat(resposta.dataLimiteManifestacao())
                .isEqualTo(dataAutorizacao.toLocalDate().plusDays(DistribuicaoDfeService.PRAZO_MANIFESTACAO_DIAS));
    }

    @Test
    void deveMarcarPrazoExpiradoQuandoJaPassouDoLimite() {
        OffsetDateTime dataAutorizacao = OffsetDateTime.now(ZoneOffset.of("-03:00"))
                .minusDays(DistribuicaoDfeService.PRAZO_MANIFESTACAO_DIAS + 10);
        ResumoNfeDistribuicao resumo = new ResumoNfeDistribuicao("chaveExpirada", "11222333000181", "Fornecedor",
                dataAutorizacao, dataAutorizacao, new BigDecimal("100.00"), SituacaoNfeDistribuicao.AUTORIZADA);

        when(repository.findByCnpj(CNPJ)).thenReturn(Optional.empty());
        when(client.consultarPorNsu(anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(new RetornoDistribuicaoDfe("138", "Documento localizado",
                        "000000000000001", "000000000000001", List.of(resumo)));

        NfeDestinadaResponse resposta = service.consultarDestinadas(CNPJ, "SP", TipoAmbiente.HOMOLOGACAO, certificado).get(0);

        assertThat(resposta.prazoExpirado()).isTrue();
        assertThat(resposta.alertaProximoDoPrazo()).isFalse();
    }

    private ResumoNfeDistribuicao resumoDe(String chave) {
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.of("-03:00"));
        return new ResumoNfeDistribuicao(chave, "11222333000181", "Fornecedor", agora, agora,
                new BigDecimal("50.00"), SituacaoNfeDistribuicao.AUTORIZADA);
    }
}
