package com.fiscaladapter.homologacao;

import com.fiscaladapter.certificado.CertificadoCarregado;
import com.fiscaladapter.certificado.CertificadoDigitalService;
import com.fiscaladapter.documento.nfe.TipoAmbiente;
import com.fiscaladapter.sefaz.SefazHttpClientFactory;
import com.fiscaladapter.sefaz.nfe.NfeStatusServicoClient;
import com.fiscaladapter.sefaz.nfe.SefazEndpointRegistry;
import com.fiscaladapter.sefaz.nfe.StatusServicoResponse;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.FileInputStream;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teste MANUAL contra a SEFAZ-PR de homologacao DE VERDADE (nao um servidor de teste local) -
 * unico jeito de provar que o pipeline completo (carregar o .pfx, TLS mutuo com o certificado
 * real, montar o envelope SOAP, enviar pela rede, interpretar a resposta) funciona contra a
 * infraestrutura real do governo, algo que nenhum teste com mock/ServidorSoapDeTeste local
 * consegue garantir (ver docs/00-visao-geral.md, secao "Referencias e limitacoes gerais" - varios
 * pontos do adapter sao marcados como "nao verificados contra homologacao real").
 *
 * So chama NFeStatusServico4 (consulta se o webservice esta no ar) - de proposito, para nao ter
 * nenhum efeito colateral (nao consome numero de documento, nao gera nenhum registro fiscal).
 * Confirma sucesso na conexao HTTPS+certificado E na interpretacao da resposta, mesmo que a SEFAZ
 * responda "servico paralisado" no momento do teste (o que importa aqui e a comunicacao ter
 * acontecido, nao qual status ela reporta).
 *
 * Excluido do `mvn verify` por padrao (@Tag("homologacao-real") + excludedGroups no pom.xml) - so
 * roda localmente, com o certificado do desenvolvedor, e e pulado automaticamente
 * (@EnabledIfEnvironmentVariable) se as variaveis de ambiente nao estiverem definidas, entao nunca
 * quebra o build de quem nao tem o certificado.
 *
 * Como rodar (PowerShell):
 *   $env:FISCALADAPTER_TESTE_CERT_CAMINHO = "C:\caminho\para\o\certificado.pfx"
 *   $env:FISCALADAPTER_TESTE_CERT_SENHA = "a-senha-do-certificado"
 *   ./mvnw.cmd -Dsurefire.excludedGroups= -Dtest=SefazPrConectividadeRealTest test
 */
@Tag("homologacao-real")
@EnabledIfEnvironmentVariable(named = "FISCALADAPTER_TESTE_CERT_CAMINHO", matches = ".+")
class SefazPrConectividadeRealTest {

    private static final String UF = "PR";

    @Test
    void statusServicoDeveResponderComUmCstatValido() throws Exception {
        CertificadoCarregado certificado = carregarCertificadoDoAmbiente();

        NfeStatusServicoClient client = new NfeStatusServicoClient(new SefazEndpointRegistry(), new SefazHttpClientFactory());

        StatusServicoResponse resposta = client.consultar(UF, TipoAmbiente.HOMOLOGACAO, certificado);

        // O que prova que o pipeline real funciona e ter recebido QUALQUER resposta estruturada
        // da SEFAZ (cStat + motivo) sem excecao de rede/TLS/parsing - nao exigimos "107" (servico
        // em operacao) porque a disponibilidade do servico do governo esta fora do nosso controle.
        System.out.println("SEFAZ-" + UF + " homologacao respondeu cStat=" + resposta.codigoStatus()
                + " motivo=\"" + resposta.motivo() + "\" servicoEmOperacao=" + resposta.servicoEmOperacao());
        assertThat(resposta.codigoStatus()).isNotBlank();
        assertThat(resposta.motivo()).isNotBlank();
    }

    private CertificadoCarregado carregarCertificadoDoAmbiente() throws Exception {
        String caminho = System.getenv("FISCALADAPTER_TESTE_CERT_CAMINHO");
        String senha = System.getenv("FISCALADAPTER_TESTE_CERT_SENHA");
        if (senha == null || senha.isBlank()) {
            throw new IllegalStateException(
                    "Defina FISCALADAPTER_TESTE_CERT_SENHA (senha do certificado) antes de rodar este teste.");
        }
        try (InputStream arquivo = new FileInputStream(caminho)) {
            return new CertificadoDigitalService().carregar(arquivo, senha.toCharArray());
        }
    }
}
