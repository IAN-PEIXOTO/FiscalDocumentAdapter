package com.fiscaladapter.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** FIS-27: descoberta de versao da API e dos layouts de documento fiscal suportados, sem autenticacao. */
@SpringBootTest
@AutoConfigureMockMvc
class VersaoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void deveExporVersaoDaApiELayoutsSuportadosSemAutenticacao() throws Exception {
        mockMvc.perform(get("/api/versao"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versaoApi").value("v1"))
                .andExpect(jsonPath("$.layoutsDocumentosFiscais.NFE").value("4.00"))
                .andExpect(jsonPath("$.layoutsDocumentosFiscais.NFCE").value("4.00"))
                .andExpect(jsonPath("$.layoutsDocumentosFiscais.CTE").value("4.00"))
                .andExpect(jsonPath("$.layoutsDocumentosFiscais.MDFE").value("3.00"))
                .andExpect(jsonPath("$.padroesNfseSuportados[0]").value("ABRASF 2.01"));
    }
}
