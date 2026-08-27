package com.fiscaladapter.seguranca;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Permite que ate dois hashes bcrypt sejam validos simultaneamente para o
 * mesmo cliente, guardados concatenados por {@link #SEPARADOR}. Isso viabiliza
 * rotacao de client_secret sem downtime usando o RegisteredClientRepository
 * padrao do Spring Authorization Server, que so compara contra um unico
 * "clientSecret" por RegisteredClient - aqui esse campo carrega os dois hashes.
 */
@Component
public class DualHashPasswordEncoder implements PasswordEncoder {

    static final String SEPARADOR = "||";

    private final org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder delegate =
            new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();

    @Override
    public String encode(CharSequence rawPassword) {
        return delegate.encode(rawPassword);
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (encodedPassword == null) {
            return false;
        }
        for (String hash : encodedPassword.split(java.util.regex.Pattern.quote(SEPARADOR))) {
            if (delegate.matches(rawPassword, hash)) {
                return true;
            }
        }
        return false;
    }
}
