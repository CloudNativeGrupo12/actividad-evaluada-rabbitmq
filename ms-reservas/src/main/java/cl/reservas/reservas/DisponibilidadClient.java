package cl.reservas.reservas;
import cl.reservas.contratos.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import java.net.http.HttpClient;
import java.time.Duration;
@Component
public class DisponibilidadClient {
    private final RestClient http;
    public DisponibilidadClient(@Value("${disponibilidad.url}") String url) {
        var factory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(Duration.ofSeconds(5));
        http=RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }
    public AsignacionRespuesta asignar(String id, ReservaSolicitud s) {
        return http.post().uri("/asignaciones").body(new AsignacionSolicitud(id,s.fechaReserva(),s.horaInicio(),s.horaFin(),s.cantidadPersonas()))
            .retrieve().body(AsignacionRespuesta.class);
    }
    public void liberar(String id) { http.delete().uri("/asignaciones/{id}",id).retrieve().toBodilessEntity(); }
}
