package ru.wink.winkaipreviz.config;

import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ExchangeFilterFunctions;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class AiWebClientConfig {

    @Bean
    public WebClient aiWebClient(@Value("${ai.parser.base-url}") String baseUrl) {
        var httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(60))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(60, TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(60, TimeUnit.SECONDS)));

        return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new org.springframework.http.client.reactive.ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .filter(ExchangeFilterFunctions.statusError(
                        HttpStatusCode::isError,
                        r -> new RuntimeException("AI error: " + r.statusCode())))
                .build();
    }
}
