package com.campuslab.rabbitmqadmin.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigTest {

    @Test
    void normalizaLosAppRolesDeAzureAd() {
        // Azure AD entrega los roles tal como se definieron en el App Registration
        assertThat(SecurityConfig.normalizarRol("Admin")).isEqualTo("ADMIN");
        assertThat(SecurityConfig.normalizarRol(" Técnico ")).isEqualTo("TECNICO");
        assertThat(SecurityConfig.normalizarRol("AUDITOR")).isEqualTo("AUDITOR");
    }
}
