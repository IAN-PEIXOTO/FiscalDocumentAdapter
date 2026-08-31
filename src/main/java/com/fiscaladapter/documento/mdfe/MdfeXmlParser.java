package com.fiscaladapter.documento.mdfe;

import com.fiscaladapter.documento.nfe.Endereco;
import com.fiscaladapter.documento.nfe.TipoAmbiente;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reconstroi um `Mdfe` a partir do XML assinado ja arquivado (mesmo espirito
 * do `CteConsultaController.notasFiscaisTransportadas`, que extrai dados por
 * regex do XML arquivado em vez de duplicar estado em outra tabela) - usado
 * para reimprimir o DAMDFE apos o encerramento do manifesto (FIS-49), quando
 * so a chave de acesso esta disponivel no endpoint, nao o objeto de dominio
 * original da emissao.
 *
 * `identificacao.uf()` (UF do estabelecimento emitente, usada so para
 * roteamento de endpoint na emissao - ver MdfeEmissaoService) nao tem uma tag
 * XML propria (o XML so guarda o UF no digito cUF da chave e no proprio
 * enderEmit) - reconstruida aqui a partir de `enderEmit/UF`, que na pratica
 * de emissao rodoviaria domestica coincide com a UF de inicio do percurso.
 * Essa aproximacao nao afeta a impressao do DAMDFE (que nunca le esse campo).
 */
public final class MdfeXmlParser {

    private static final DateTimeFormatter DHEMI_FORMAT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private MdfeXmlParser() {
    }

    public static Mdfe paraDominio(String xmlAssinado) {
        String uf = tag(buscarBloco(xmlAssinado, "enderEmit"), "UF");
        TipoAmbiente ambiente = "1".equals(tag(xmlAssinado, "tpAmb")) ? TipoAmbiente.PRODUCAO : TipoAmbiente.HOMOLOGACAO;
        LocalDate dataEmissao = OffsetDateTime.parse(tag(xmlAssinado, "dhEmi"), DHEMI_FORMAT).toLocalDate();

        IdentificacaoMdfe identificacao = new IdentificacaoMdfe(
                uf,
                Integer.parseInt(tag(xmlAssinado, "serie")),
                Long.parseLong(tag(xmlAssinado, "nMDF")),
                dataEmissao,
                ambiente,
                tag(xmlAssinado, "UFIni"),
                tag(xmlAssinado, "UFFim"),
                tag(xmlAssinado, "cMunCarrega"),
                tag(xmlAssinado, "xMunCarrega"));

        String blocoEmit = buscarBloco(xmlAssinado, "emit");
        Endereco enderecoEmitente = new Endereco(
                tag(blocoEmit, "xLgr"),
                tag(blocoEmit, "nro"),
                tag(blocoEmit, "xBairro"),
                tag(blocoEmit, "cMun"),
                tag(blocoEmit, "xMun"),
                uf,
                tagOpcional(blocoEmit, "CEP"),
                tagOpcional(blocoEmit, "fone"));
        EmitenteMdfe emitente = new EmitenteMdfe(
                tag(blocoEmit, "CNPJ"),
                tag(blocoEmit, "xNome"),
                tagOpcional(blocoEmit, "xFant"),
                tagOpcional(blocoEmit, "IE"),
                enderecoEmitente);

        String blocoRodo = buscarBloco(xmlAssinado, "rodo");
        VeiculoTracao veiculoTracao = new VeiculoTracao(
                tag(blocoRodo, "placa"),
                new BigDecimal(tag(blocoRodo, "tara")),
                tag(blocoRodo, "tpRod"),
                tag(blocoRodo, "tpCar"),
                tagOpcional(buscarBloco(blocoRodo, "veicTracao"), "UF"));

        List<Condutor> condutores = new ArrayList<>();
        Matcher condutorMatcher = Pattern.compile("<condutor>(.*?)</condutor>", Pattern.DOTALL).matcher(blocoRodo);
        while (condutorMatcher.find()) {
            String blocoCondutor = condutorMatcher.group(1);
            condutores.add(new Condutor(tag(blocoCondutor, "xNome"), tag(blocoCondutor, "CPF")));
        }

        String blocoInfDoc = buscarBloco(xmlAssinado, "infDoc");
        List<String> chavesCte = extrairTodos(blocoInfDoc, "chCTe");
        List<String> chavesNfe = extrairTodos(blocoInfDoc, "chNFe");

        String blocoTot = buscarBloco(xmlAssinado, "tot");

        return new Mdfe(identificacao, emitente, tagOpcional(xmlAssinado, "RNTRC"), veiculoTracao, condutores,
                tag(blocoInfDoc, "cMunDescarga"), tag(blocoInfDoc, "xMunDescarga"),
                chavesCte, chavesNfe,
                new BigDecimal(tag(blocoTot, "vCarga")), new BigDecimal(tag(blocoTot, "qCarga")));
    }

    private static String buscarBloco(String xml, String tagNome) {
        Matcher matcher = Pattern.compile("<" + tagNome + ">(.*?)</" + tagNome + ">", Pattern.DOTALL).matcher(xml);
        if (!matcher.find()) {
            throw new IllegalArgumentException("XML do MDF-e nao contem o bloco <" + tagNome + ">");
        }
        return matcher.group(1);
    }

    private static String tag(String escopo, String tagNome) {
        String valor = tagOpcional(escopo, tagNome);
        if (valor == null) {
            throw new IllegalArgumentException("XML do MDF-e nao contem a tag <" + tagNome + ">");
        }
        return valor;
    }

    private static String tagOpcional(String escopo, String tagNome) {
        Matcher matcher = Pattern.compile("<" + tagNome + ">(.*?)</" + tagNome + ">", Pattern.DOTALL).matcher(escopo);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static List<String> extrairTodos(String escopo, String tagNome) {
        List<String> valores = new ArrayList<>();
        Matcher matcher = Pattern.compile("<" + tagNome + ">(.*?)</" + tagNome + ">", Pattern.DOTALL).matcher(escopo);
        while (matcher.find()) {
            valores.add(matcher.group(1));
        }
        return valores;
    }
}
