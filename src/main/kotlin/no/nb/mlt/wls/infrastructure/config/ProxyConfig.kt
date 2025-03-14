package no.nb.mlt.wls.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("proxy")
class ProxyConfig(
    val httpProxyHost: String,
    val httpProxyPort: Int,
    val nonProxyHosts: String
)
