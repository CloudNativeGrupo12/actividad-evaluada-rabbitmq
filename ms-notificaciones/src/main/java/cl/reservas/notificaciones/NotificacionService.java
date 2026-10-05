package cl.reservas.notificaciones;
import cl.reservas.contratos.*;
import org.springframework.stereotype.Service;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.beans.factory.annotation.Value;
@Service
public class NotificacionService {
    private final JavaMailSender mail;
    private final RegistroRepository repository;
    private final String remitente;
    public NotificacionService(JavaMailSender mail,RegistroRepository repository,@Value("${correo.remitente}") String remitente) {
        this.mail=mail; this.repository=repository; this.remitente=remitente;
    }
    public synchronized void procesar(ReservaConfirmada evento,String payload) {
        if (repository.existe(evento.eventoId())) return;
        var mensaje=new SimpleMailMessage();
        mensaje.setFrom(remitente); mensaje.setTo(evento.emailCliente());
        mensaje.setSubject("Reserva confirmada "+evento.reservaId());
        mensaje.setText("Tu reserva está confirmada.\nMesa: "+evento.mesaId()+"\nFecha: "+evento.fechaReserva()
            +"\nHorario: "+evento.horaInicio()+" a "+evento.horaFin()+"\nPersonas: "+evento.cantidadPersonas());
        mail.send(mensaje);
        repository.guardar(evento,payload,"CORREO_ENVIADO");
    }
}
