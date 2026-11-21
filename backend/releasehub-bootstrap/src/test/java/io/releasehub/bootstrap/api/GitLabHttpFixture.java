package io.releasehub.bootstrap.api;

import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.assertThat;

/** 使用 JDK 自带 HTTP fixture，避免为 bootstrap 新增测试依赖。 */
final class GitLabHttpFixture {
    record Response(int status,String body) {}
    record Request(String method,String path,String query,String body,boolean internalHeaders) {}
    final java.util.Map<String,Response> responses=new java.util.concurrent.ConcurrentHashMap<>();
    final java.util.List<Request> requests=new java.util.concurrent.CopyOnWriteArrayList<>();
    private final com.sun.net.httpserver.HttpServer server;
    GitLabHttpFixture() {
        try {
            server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
            server.createContext("/",exchange->{
                String method=exchange.getRequestMethod();String path=exchange.getRequestURI().getRawPath();
                boolean internalHeaders=exchange.getRequestHeaders().keySet().stream()
                        .anyMatch(name->name.toLowerCase(java.util.Locale.ROOT).startsWith("x-releasehub-expected-"));
                requests.add(new Request(method,path,exchange.getRequestURI().getRawQuery(),new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8),internalHeaders));
                Response response=responses.getOrDefault(method+" "+path,new Response(404,"{}"));
                byte[] body=response.body().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type","application/json");
                exchange.sendResponseHeaders(response.status(),body.length);
                try(var output=exchange.getResponseBody()){output.write(body);}
            });
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
    }
    void start(){server.start();}
    void stop(){server.stop(0);}
    String baseUrl(){return "http://127.0.0.1:"+server.getAddress().getPort();}
    void resetAll(){responses.clear();requests.clear();}
    void resetRequests(){requests.clear();}
    void reply(String method,String path,int status,String body){responses.put(method+" "+path,new Response(status,body));}
    void verify(String method,String path,int count){assertThat(requests.stream().filter(r->r.method().equals(method)&&r.path().equals(path)).count()).isEqualTo(count);}
    static boolean isWrite(String method){return java.util.Set.of("POST","PUT","PATCH","DELETE").contains(method);}
    long writeCount(){return requests.stream().filter(request->isWrite(request.method())).count();}
    String query(String method,String path){return requests.stream().filter(request->request.method().equals(method)&&request.path().equals(path)).findFirst().orElseThrow().query();}
    com.fasterxml.jackson.databind.JsonNode body(String method,String path) {
        try{return new com.fasterxml.jackson.databind.ObjectMapper().readTree(requests.stream().filter(r->r.method().equals(method)&&r.path().equals(path)).findFirst().orElseThrow().body());}
        catch(java.io.IOException e){throw new IllegalStateException(e);}
    }
}
