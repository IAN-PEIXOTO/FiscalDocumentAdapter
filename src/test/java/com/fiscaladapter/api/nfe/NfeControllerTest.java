package com.fiscaladapter.api.nfe;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiscaladapter.certificado.TestCertificadoFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class NfeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void deveEmitirNfeComSucessoRetornandoChaveEXmlAssinado() throws Exception {
        byte[] p12 = TestCertificadoFactory.gerarP12(
                "12345678000199", "senha123".toCharArray(),
                Date.from(Instant.now().minus(Duration.ofDays(1))),
                Date.from(Instant.now().plus(Duration.ofDays(365))));

        MockMultipartFile documento = new MockMultipartFile(
                "documento", "documento.json", "application/json",
                objectMapper.writeValueAsBytes(requestValido()));
        MockMultipartFile certificado = new MockMultipartFile(
                "certificado", "certificado.p12", "application/x-pkcs12", p12);

        mockMvc.perform(multipart("/api/v1/nfe")
                        .file(documento)
                        .file(certificado)
                        .param("senhaCertificado", "senha123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chaveAcesso").isNotEmpty())
                .andExpect(jsonPath("$.xmlAssinado").exists());
    }

    @Test
    void deveRejeitarDocumentoComCamposObrigatoriosFaltando() throws Exception {
        MockMultipartFile documentoInvalido = new MockMultipartFile(
                "documento", "documento.json", "application/json", "{}".getBytes());
        MockMultipartFile certificado = new MockMultipartFile(
                "certificado", "certificado.p12", "application/x-pkcs12", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/v1/nfe")
                        .file(documentoInvalido)
                        .file(certificado)
                        .param("senhaCertificado", "qualquer"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Dados invalidos no documento enviado"));
    }

    private NfeRequest requestValido() {
        EnderecoRequest enderecoEmitente = new EnderecoRequest("Rua Teste", "100", "Centro", "3550308", "Sao Paulo", "SP", "01000000", "1130000000");
        EmitenteRequest emitente = new EmitenteRequest("12345678000199", "EMPRESA TESTE LTDA", "TESTE", "111222333", "1", enderecoEmitente);

        EnderecoRequest enderecoDestinatario = new EnderecoRequest("Av. Cliente", "200", "Jardins", "3550308", "Sao Paulo", "SP", "02000000", null);
        DestinatarioRequest destinatario = new DestinatarioRequest("98765432100", "CLIENTE TESTE", "9", null, "cliente@teste.com", enderecoDestinatario);

        ImpostoItemRequest imposto = new ImpostoItemRequest("0", "00",
                BigDecimal.valueOf(100.00), BigDecimal.valueOf(18.00), BigDecimal.valueOf(18.00),
                BigDecimal.ZERO, BigDecimal.valueOf(1.65), BigDecimal.valueOf(7.60));

        ItemRequest item = new ItemRequest(1, "PROD001", "PRODUTO TESTE", "61099010", "5102", "UN",
                BigDecimal.ONE, BigDecimal.valueOf(100.00), BigDecimal.valueOf(100.00), imposto);

        IdentificacaoRequest identificacao = new IdentificacaoRequest("SP", "VENDA DE MERCADORIA", 1, 42L,
                LocalDate.of(2026, 3, 15), "HOMOLOGACAO", 1, true, "3550308");

        PagamentoRequest pagamento = new PagamentoRequest("01", BigDecimal.valueOf(100.00));

        return new NfeRequest(identificacao, emitente, destinatario, List.of(item), List.of(pagamento));
    }
}
