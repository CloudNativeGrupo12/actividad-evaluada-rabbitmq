package cl.reservas.notificaciones;
import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
@Entity
@Table(name="procesados")
@Getter @Setter @NoArgsConstructor
public class Procesado {
    @Id
    @Column(name="evento_id",length=40)
    private String eventoId;
    @Column(name="reserva_id",length=40,nullable=false)
    private String reservaId;
    @Column(name="payload",length=10000,nullable=false)
    private String payload;
    @Column(name="resultado",length=300,nullable=false)
    private String resultado;
    @Column(name="procesado_en",nullable=false)
    private OffsetDateTime procesadoEn;
    public Procesado(String eventoId,String reservaId,String payload,String resultado,OffsetDateTime procesadoEn) {
        this.eventoId=eventoId;
        this.reservaId=reservaId;
        this.payload=payload;
        this.resultado=resultado;
        this.procesadoEn=procesadoEn;
    }
}
