package no.nb.mlt.wls.infrastructure

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("proxy")
class ProxyConfig(
    val httpProxyHost: String,
    val httpProxyPort: String,
    val httpsProxyHost: String,
    val httpsProxyPort: String,
    val nonProxyHosts: String
)
