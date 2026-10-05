package cl.reservas.notificaciones;
 import cl.reservas.contratos.RabbitTopology;
 import org.springframework.boot.SpringApplication;
 import org.springframework.boot.autoconfigure.SpringBootApplication;
 import org.springframework.context.annotation.Import;
 @SpringBootApplication @Import(RabbitTopology.class)
 public class Application { public static void main(String[] args) { SpringApplication.run(Application.class, args); } }
