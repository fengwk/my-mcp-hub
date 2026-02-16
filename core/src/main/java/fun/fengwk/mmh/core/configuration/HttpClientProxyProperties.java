package fun.fengwk.mmh.core.configuration;

import fun.fengwk.convention4j.common.http.client.HttpClientFactory;
import fun.fengwk.convention4j.common.lang.StringUtils;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.util.Objects;

/**
 * Proxy configuration for HttpClient.
 *
 * @author fengwk
 */
@Slf4j
@Data
@ConfigurationProperties(prefix = "mmh.http.proxy")
public class HttpClientProxyProperties {

    /**
     * HTTP proxy in URL form, e.g. http://host:port or host:port.
     */
    private String httpProxy;

    /**
     * HTTPS proxy in URL form, e.g. http://host:port or host:port.
     */
    private String httpsProxy;

    @PostConstruct
    public void init() {
        String httpProxy = getHttpProxy();
        String httpsProxy = getHttpsProxy();
        addProxy(httpProxy);
        if (!Objects.equals(httpProxy, httpsProxy)) {
            addProxy(httpsProxy);
        }
    }

    private void addProxy(String proxyStr) {
        Proxy proxy = parseProxy(proxyStr);
        if (proxy == null) {
            return;
        }
        HttpClientFactory.getDefaultConfigurableListableProxies().addProxy(proxy);
        log.info("http proxy configured: {}", proxyStr);
    }

    private Proxy parseProxy(String proxyStr) {
        if (StringUtils.isBlank(proxyStr)) {
            return null;
        }
        try {
            String uriStr = proxyStr;
            if (!uriStr.contains("://")) {
                uriStr = "http://" + uriStr;
            }
            URI uri = new URI(uriStr);
            String scheme = uri.getScheme();
            Proxy.Type type = scheme != null && scheme.toLowerCase().startsWith("socks")
                ? Proxy.Type.SOCKS
                : Proxy.Type.HTTP;
            String host = uri.getHost();
            int port = uri.getPort();
            if (host == null) {
                String[] parts = proxyStr.split(":");
                host = parts[0];
                port = parts.length > 1 ? Integer.parseInt(parts[1]) : 80;
            }
            if (port == -1) {
                port = 80;
            }
            return new Proxy(type, new InetSocketAddress(host, port));
        } catch (Exception ex) {
            throw new IllegalArgumentException("invalid proxy: " + proxyStr, ex);
        }
    }

}
