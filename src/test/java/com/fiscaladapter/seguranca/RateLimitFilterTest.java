package com.fiscaladapter.seguranca;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Prova que o rate limit usa a identidade autenticada quando disponivel, em vez do parametro
 * "client_id" livremente escolhido pelo chamador (FIS-69) - sem isso, um cliente autenticado
 * varia esse parametro a cada chamada e nunca esbarra no limite.
 */
class RateLimitFilterTest {

    private final RateLimitFilter filter = new RateLimitFilter();

    @AfterEach
    void limparContextoDeSeguranca() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void naoDeveSerContornavelVariandoOParametroClientIdQuandoAutenticado() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("cliente-real", null));

        // 60 requisicoes (limite) variando client_id a cada chamada - se o parametro fosse
        // usado como chave, cada uma cairia num balde novo e nunca atingiria o limite.
        for (int i = 0; i < 60; i++) {
            assertThat(chamarFiltro("id-diferente-" + UUID.randomUUID())).isTrue();
        }

        // a 61a chamada, ainda variando o parametro, deve ser bloqueada - prova que a chave
        // usada foi a identidade autenticada ("cliente-real"), nao o parametro.
        assertThat(chamarFiltro("id-diferente-" + UUID.randomUUID())).isFalse();
    }

    @Test
    void deveUsarParametroClientIdQuandoNaoHaAutenticacao() throws Exception {
        // cenario do endpoint /oauth2/token: ainda nao ha Authentication no SecurityContext
        // nesse ponto do fluxo OAuth2 client_credentials - o parametro e o unico sinal disponivel.
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("client_id", "cliente-sem-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoMoreInteractions(chain);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    /** @return true se a requisicao passou (chain.doFilter chamado), false se foi bloqueada com 429. */
    private boolean chamarFiltro(String clientIdParam) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("client_id", clientIdParam);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        return response.getStatus() != 429;
    }
}
