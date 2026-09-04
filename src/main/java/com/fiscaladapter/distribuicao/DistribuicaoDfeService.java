package com.fiscaladapter.distribuicao;

import com.fiscaladapter.certificado.CertificadoCarregado;
import com.fiscaladapter.documento.nfe.TipoAmbiente;
import com.fiscaladapter.sefaz.SefazComunicacaoException;
import com.fiscaladapter.sefaz.nfe.NfeDistribuicaoDfeClient;
import com.fiscaladapter.sefaz.nfe.ResumoNfeDistribuicao;
import com.fiscaladapter.sefaz.nfe.RetornoDistribuicaoDfe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Consulta de NF-e destinadas a um CNPJ (FIS-40): guarda o cursor de NSU por
 * CNPJ (DistribuicaoDfeCursor), pagina a NFeDistribuicaoDFe ate esgotar o
 * lote disponivel, e calcula o prazo de manifestacao de cada resumo
 * devolvido - a SEFAZ nao devolve esse prazo pronto, o adapter e quem
 * controla e alerta antes de expirar (criterio de aceite 3).
 *
 * Prazo de manifestacao: 90 dias corridos a partir da data de autorizacao
 * (dhRecbto), pelo Ajuste SINIEF 14/2026 (efetivo desde 01/06/2026 -
 * reduziu o prazo anterior de 180 dias). Aplica-se a Confirmacao,
 * Desconhecimento e Operacao nao Realizada; Ciencia da Operacao nao tem
 * prazo/efeito fiscal proprio, mas o adapter nao distingue isso aqui porque
 * a NFeDistribuicaoDFe nao informa qual tipo de manifestacao (se alguma) ja
 * foi registrada para cada resumo - so a data limite calculada.
 */
@Service
public class DistribuicaoDfeService {

    private static final Logger log = LoggerFactory.getLogger(DistribuicaoDfeService.class);

    static final int PRAZO_MANIFESTACAO_DIAS = 90;
    static final int DIAS_ALERTA_ANTES_DO_PRAZO = 15;
    private static final Duration INTERVALO_MINIMO_ENTRE_CONSULTAS = Duration.ofHours(1);
    private static final int MAX_PAGINAS_POR_CONSULTA = 20;

    private final DistribuicaoDfeCursorRepository repository;
    private final NfeDistribuicaoDfeClient client;

    public DistribuicaoDfeService(DistribuicaoDfeCursorRepository repository, NfeDistribuicaoDfeClient client) {
        this.repository = repository;
        this.client = client;
    }

    /**
     * Sem @Transactional (FIS-77): o loop abaixo faz ate MAX_PAGINAS_POR_CONSULTA chamadas SOAP
     * sequenciais a SEFAZ (timeout de 60s cada) - uma transacao Spring envolvendo o metodo inteiro
     * seguraria uma conexao do pool HikariCP por I/O de rede puro, sem nenhuma operacao de banco
     * acontecendo nesse meio tempo, podendo esgotar o pool sob poucas consultas concorrentes. A
     * unica escrita (repository.save no fim) e atomica por si so (JpaRepository.save() ja roda
     * numa transacao propria).
     */
    public List<NfeDestinadaResponse> consultarDestinadas(String cnpj, String ufAutor, TipoAmbiente ambiente,
                                                           CertificadoCarregado certificado) {
        DistribuicaoDfeCursor cursor = repository.findByCnpj(cnpj).orElseGet(() -> new DistribuicaoDfeCursor(cnpj));

        Instant agora = Instant.now();
        if (cursor.getConsultadoEm() != null) {
            Duration desdeUltimaConsulta = Duration.between(cursor.getConsultadoEm(), agora);
            if (desdeUltimaConsulta.compareTo(INTERVALO_MINIMO_ENTRE_CONSULTAS) < 0) {
                throw new ConsultaDistribuicaoDfeMuitoFrequenteException(INTERVALO_MINIMO_ENTRE_CONSULTAS, desdeUltimaConsulta);
            }
        }

        List<ResumoNfeDistribuicao> resumos = new ArrayList<>();
        String ultNsu = cursor.getUltimoNsu();
        for (int pagina = 1; pagina <= MAX_PAGINAS_POR_CONSULTA; pagina++) {
            RetornoDistribuicaoDfe retorno = client.consultarPorNsu(cnpj, ufAutor, ultNsu, ambiente, certificado);
            if (!retorno.sucesso()) {
                throw new SefazComunicacaoException(
                        "Distribuicao DFe recusada pela SEFAZ - cStat " + retorno.cStat() + " (" + retorno.xMotivo() + ")");
            }
            resumos.addAll(retorno.resumos());

            String novoUltNsu = retorno.ultNsu() != null ? retorno.ultNsu() : ultNsu;
            boolean semProgresso = novoUltNsu.equals(ultNsu);
            ultNsu = novoUltNsu;
            boolean chegouNoTopoDoLote = retorno.maxNsu() == null || ultNsu.equals(retorno.maxNsu());

            if (chegouNoTopoDoLote || semProgresso) {
                break;
            }
            if (pagina == MAX_PAGINAS_POR_CONSULTA) {
                log.warn("Distribuicao DFe do CNPJ {} atingiu o limite de {} paginas nesta consulta sem esgotar o "
                        + "lote (ultNSU={}, maxNSU={}) - documentos restantes serao trazidos na proxima consulta.",
                        cnpj, MAX_PAGINAS_POR_CONSULTA, ultNsu, retorno.maxNsu());
            }
        }

        cursor.avancar(ultNsu, agora);
        repository.save(cursor);

        return resumos.stream().map(this::paraResposta).toList();
    }

    private NfeDestinadaResponse paraResposta(ResumoNfeDistribuicao resumo) {
        LocalDate dataLimite = resumo.dataAutorizacao() != null
                ? resumo.dataAutorizacao().toLocalDate().plusDays(PRAZO_MANIFESTACAO_DIAS)
                : null;
        Long diasRestantes = dataLimite != null ? ChronoUnit.DAYS.between(LocalDate.now(), dataLimite) : null;
        boolean prazoExpirado = diasRestantes != null && diasRestantes < 0;
        boolean alertaProximoDoPrazo = diasRestantes != null && diasRestantes >= 0 && diasRestantes <= DIAS_ALERTA_ANTES_DO_PRAZO;

        return new NfeDestinadaResponse(resumo.chaveAcesso(), resumo.cnpjEmitente(), resumo.nomeEmitente(),
                resumo.dataEmissao(), resumo.dataAutorizacao(), resumo.valorNota(), resumo.situacao().name(),
                dataLimite, diasRestantes, prazoExpirado, alertaProximoDoPrazo);
    }
}
