package cl.reservas.disponibilidad;
import jakarta.persistence.*;
import lombok.*;
@Entity
@Table(name="mesas")
@Getter @Setter @NoArgsConstructor
public class Mesa {
    @Id
    @Column(name="mesa_id",length=40)
    private String mesaId;
    @Column(name="capacidad",nullable=false)
    private int capacidad;
    public Mesa(String mesaId,int capacidad) {
        this.mesaId=mesaId;
        this.capacidad=capacidad;
    }
}
