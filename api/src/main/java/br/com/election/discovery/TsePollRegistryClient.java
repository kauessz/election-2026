package br.com.election.discovery;

import org.springframework.stereotype.Component;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.Flow.*;

@Component
public class TsePollRegistryClient {
    public static final int MAX_ZIP=32*1024*1024;
    private final DiscoveryConfiguration.Properties properties;
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    public TsePollRegistryClient(DiscoveryConfiguration.Properties properties){this.properties=properties;}
    public byte[] download(){
        if(!properties.enabled()) throw new IllegalArgumentException("Descoberta desabilitada.");
        var request=HttpRequest.newBuilder(properties.tseOpenDataUrl()).timeout(Duration.ofSeconds(30))
            .header("User-Agent","Election2026-PollRegistry/1.0").GET().build();
        var future=client.sendAsync(request,info->{
            if(info.statusCode()!=200) throw new IllegalArgumentException("TSE indisponível: HTTP "+info.statusCode());
            if(info.headers().firstValueAsLong("Content-Length").orElse(0)>MAX_ZIP) throw new IllegalArgumentException("ZIP excede o limite de 32 MiB.");
            return new LimitedBody(MAX_ZIP);
        });
        try {return future.get(35,TimeUnit.SECONDS).body();}
        catch(Exception e){future.cancel(true);if(e instanceof InterruptedException)Thread.currentThread().interrupt();throw new IllegalArgumentException("Não foi possível obter o ZIP oficial dentro dos limites de tamanho e tempo.");}
    }
    static class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> result=new CompletableFuture<>();
        private final ByteArrayOutputStream bytes=new ByteArrayOutputStream(); private final int limit; private Subscription subscription;
        LimitedBody(int limit){this.limit=limit;}
        public CompletionStage<byte[]> getBody(){return result;}
        public void onSubscribe(Subscription s){subscription=s;s.request(1);}
        public void onNext(List<ByteBuffer> buffers){
            for(var buffer:buffers){if(buffer.remaining()>limit-bytes.size()){subscription.cancel();result.completeExceptionally(new IllegalArgumentException("ZIP grande demais"));return;}
                byte[] part=new byte[buffer.remaining()];buffer.get(part);bytes.writeBytes(part);}
            subscription.request(1);
        }
        public void onError(Throwable error){result.completeExceptionally(error);}
        public void onComplete(){result.complete(bytes.toByteArray());}
    }
}
