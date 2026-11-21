package io.releasehub.infrastructure.git;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import java.net.http.HttpClient;
import java.time.Duration;

/** 固定不跟随重定向的 HTTP/1.1 客户端，写请求只由显式操作调用发出。 */
public final class GitHttpClients {
    private GitHttpClients() {}
    public static RestTemplate create(RestTemplateBuilder builder) {
        return create(builder,Duration.ofSeconds(15));
    }
    public static RestTemplate create(RestTemplateBuilder builder,Duration readTimeout) {
        return builder.requestFactory(()->{
            var client=HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                    .followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(5)).build();
            var factory=new JdkClientHttpRequestFactory(client);factory.setReadTimeout(readTimeout);return factory;
        }).build();
    }
}
