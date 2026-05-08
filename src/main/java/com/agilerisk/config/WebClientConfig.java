package com.agilerisk.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class WebClientConfig {

    @Bean(name = "overBudgetWebClient")
    public WebClient overBudgetWebClient(AiProperties props) {
        return buildClient(props.getOverBudget());
    }

    @Bean(name = "requirementChangeWebClient")
    public WebClient requirementChangeWebClient(AiProperties props) {
        return buildClient(props.getRequirementChange());
    }

    @Bean(name = "communicationCollaborationWebClient")
    public WebClient communicationCollaborationWebClient(AiProperties props) {
        return buildClient(props.getCommunicationCollaboration());
    }

    private WebClient buildClient(AiProperties.Endpoint cfg) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 2000)
                .responseTimeout(Duration.ofMillis(cfg.getTimeoutMs()))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(cfg.getTimeoutMs(), TimeUnit.MILLISECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(cfg.getTimeoutMs(), TimeUnit.MILLISECONDS)));

        WebClient.Builder builder = WebClient.builder()
                .baseUrl(cfg.getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);

        if (cfg.getApiKey() != null && !cfg.getApiKey().isBlank()) {
            builder.defaultHeader("X-API-Key", cfg.getApiKey());
        }
        return builder.build();
    }
}
