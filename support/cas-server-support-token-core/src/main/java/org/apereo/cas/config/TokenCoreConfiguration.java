package org.apereo.cas.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apereo.cas.CipherExecutor;
import org.apereo.cas.configuration.CasConfigurationProperties;
import org.apereo.cas.configuration.model.core.util.EncryptionOptionalSigningOptionalJwtCryptographyProperties;
import org.apereo.cas.services.ServicesManager;
import org.apereo.cas.ticket.ExpirationPolicy;
import org.apereo.cas.token.JWTTokenTicketBuilder;
import org.apereo.cas.token.TokenTicketBuilder;
import org.apereo.cas.token.cipher.TokenTicketCipherExecutor;
import org.jasig.cas.client.validation.AbstractUrlBasedTicketValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * This is {@link TokenCoreConfiguration}.
 *
 * @author Misagh Moayyed
 * @since 5.0.0
 */
@Configuration("tokenCoreConfiguration")
@EnableConfigurationProperties(CasConfigurationProperties.class)
@AutoConfigureOrder(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class TokenCoreConfiguration {
    @Autowired
    @Qualifier("servicesManager")
    private ServicesManager servicesManager;

    @Autowired
    private CasConfigurationProperties casProperties;

    @Autowired
    @Qualifier("casClientTicketValidator")
    private AbstractUrlBasedTicketValidator casClientTicketValidator;

    @Autowired
    @Qualifier("grantingTicketExpirationPolicy")
    private ExpirationPolicy grantingTicketExpirationPolicy;

    @Bean
    @RefreshScope
    @ConditionalOnMissingBean(name = "tokenCipherExecutor")
    public CipherExecutor tokenCipherExecutor() {
        final EncryptionOptionalSigningOptionalJwtCryptographyProperties crypto = casProperties.getAuthn().getToken().getCrypto();
        boolean enabled = crypto.isEnabled();
        if (!enabled && (StringUtils.isNotBlank(crypto.getEncryption().getKey())) && StringUtils.isNotBlank(crypto.getSigning().getKey())) {
            LOGGER.warn("Token encryption/signing is not enabled explicitly in the configuration, yet signing/encryption keys "
                + "are defined for operations. CAS will proceed to enable the token encryption/signing functionality.");
            enabled = true;
        }

        if (enabled) {
            return new TokenTicketCipherExecutor(crypto.getEncryption().getKey(),
                crypto.getSigning().getKey(),
                crypto.getAlg(),
                crypto.isEncryptionEnabled(),
                crypto.isSigningEnabled());
        }
        LOGGER.info("Token cookie encryption/signing is turned off. This "
            + "MAY NOT be safe in a production environment. Consider using other choices to handle encryption, "
            + "signing and verification of generated tokens.");
        return CipherExecutor.noOp();
    }

    @RefreshScope
    @Bean
    @ConditionalOnMissingBean(name = "tokenTicketBuilder")
    public TokenTicketBuilder tokenTicketBuilder() {
        return new JWTTokenTicketBuilder(casClientTicketValidator,
            casProperties.getServer().getPrefix(),
            tokenCipherExecutor(),
            grantingTicketExpirationPolicy,
            this.servicesManager);
    }
}
