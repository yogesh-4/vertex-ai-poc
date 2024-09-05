package org.example.jira;



import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.SSLContexts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Configuration
public class RestTemplateConfig {

    @Value("${jiraUsername}")
    private String jiraUserName;

    @Value("${jira.api.token}")
    private String jiraAccessToken;

    @Bean("jiraRestTemplate")
    public RestTemplate nextGenRestTemplate() throws KeyStoreException, NoSuchAlgorithmException, KeyManagementException {

        HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory();

        // 2 minutes timeout
        requestFactory.setConnectionRequestTimeout((int) TimeUnit.MINUTES.toMillis(2));
        requestFactory.setConnectTimeout((int) TimeUnit.MINUTES.toMillis(2));
        requestFactory.setReadTimeout((int) TimeUnit.MINUTES.toMillis(2));

        SSLContext sslcontext = SSLContexts.custom() .loadTrustMaterial(null, (chain, authType) -> true) .build();
        SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(sslcontext, new String[]{"TLSv1.2"}, null, new NoopHostnameVerifier());
        CloseableHttpClient httpClient = HttpClients.custom().setSSLSocketFactory(sslsf).build();

        requestFactory.setHttpClient(httpClient);

        //RestTemplate restTemplate = new RestTemplate(requestFactory);
        RestTemplateBuilder restTemplateBuilder = new RestTemplateBuilder();
        RestTemplate restTemplate = restTemplateBuilder.basicAuthorization(jiraUserName,jiraAccessToken).requestFactory(requestFactory).build();
        return restTemplate;
    }
}
