package cl.reservas.reservas;
import jakarta.persistence.*;
import lombok.*;
@Entity
@Table(name="reservas")
@Getter @Setter @NoArgsConstructor
public class Reserva {
    @Id
    @Column(name="reserva_id",length=40)
    private String reservaId;
    @Column(name="evento_id",length=40,nullable=false,unique=true)
    private String eventoId;
    @Column(name="payload",length=10000,nullable=false)
    private String payload;
    @Column(name="publicacion",length=30,nullable=false)
    private String publicacion;
    public Reserva(String reservaId,String eventoId,String payload,String publicacion) {
        this.reservaId=reservaId;
        this.eventoId=eventoId;
        this.payload=payload;
        this.publicacion=publicacion;
    }
}
