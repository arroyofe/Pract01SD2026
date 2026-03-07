/**
 * @author Fernando Arroyo
 */
package es.ubu.lsi.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

import es.ubu.lsi.common.ChatMessage;
import es.ubu.lsi.common.ChatMessage.*;

/**
 * Implementa el chat del cliente
 */
public class ChatClientImpl implements ChatClient {

	// Puerto por defecto
	private static final int DEFAULT_PORT = 1500;

	// Puerto por defecto
	private static final String DEFAULT_HOST = "localhost";

	// Logger para seguimiento de errores
	private static final Logger LOGGER = Logger.getLogger(ChatClientImpl.class.getName());
	
	// Creador del manejador de eventos
	static {
		ConsoleHandler handler = new ConsoleHandler();
		handler.setFormatter(new Formatter() {
			@Override
			public String format(LogRecord marca) {
				if (marca.getParameters() != null) {
                    return java.text.MessageFormat.format(marca.getMessage(), marca.getParameters()) + "\n";
                }

				return marca.getMessage() + "\n";
			}
		});

		LOGGER.setUseParentHandlers(false);
		LOGGER.addHandler(handler);
	}

	// Servidor que va a usar el cliente
	private final String server;

	// Código de usuario del cliente
	private final String username;

	// Puerto por el que se comunica el cliente
	private final int port;

	//Booleano que indica que la conexión está activa 
	private volatile boolean carryOn = true;

	// identificación numérica del cliente
	private int id;

	// Flujo de entrada
	private ObjectInputStream in;

	// Flujo de salida
	private ObjectOutputStream out;

	// Variable Listener
	private ChatClientListener escuchaCliente;

	// Variable socket para la gestión de las conexiones
	private Socket socket;

	// Hora
	private SimpleDateFormat hora = new SimpleDateFormat("HH:mm:ss");
	
	// hilo separado para la entrada
	private Thread hiloEntrada;

	// Mensaje de patrocinio
	private static final String PUB = "Fernando patrocina el mensaje : ";

	// Mensaje error I/O
	private static final String ERROR = "Erreur IO";

	/**
	 * Constructor Crea los datos del chat con todos los datos por parámetro
	 * 
	 * @param server   servidor
	 * @param username usuario que se conecta
	 * @param port     puerto de enlace
	 */
	public ChatClientImpl(String server, String username, int port) {
		this.server = server;
		this.username = username;
		this.port = port;

		try {
			// Creación del socket
			this.socket = new Socket(server, port);
						
			// Creación del objeto out a enviar
			out = new ObjectOutputStream(socket.getOutputStream());

		} catch (IOException e) {
			// Si falla, se informa del fallo y se cierra el proceso
			LOGGER.log(Level.SEVERE, ERROR, e);
			carryOn = false;
		}
	}

	/**
	 * Constructor Crea los datos del chat con servidor y cliente. El puerto se
	 * escoge con el valor por defecto
	 * 
	 * @param server   servidor del sisteama
	 * @param username usuario cliente
	 */
	public ChatClientImpl(String server, String username) {
		this(server, username, DEFAULT_PORT);
	}

	/**
	 * Constructor Crea los datos del chat con el cliente solamente.
	 * 
	 * El puerto y el servidor se escogen con el valor por defecto
	 * 
	 * @param username usuario del cliente
	 */
	public ChatClientImpl(String username) {
		this(DEFAULT_HOST,username, DEFAULT_PORT);
	}

	/**
	 * Metodo de inicio del chat por parte de un usuario
	 * 
	 * @return true si el arranque es correcto y false en caso contrario
	 */
	@Override
	public boolean start() {
		// Control de situación de carryOn, si es falso se devuelve false
		if (!carryOn) return false;

		try {
				
			// Si no hay primera conexión del cliente algo ha fallado
			// y se procede a la desconexión
			if (!iniciarConexion()) {
	               disconnect();
	               return false;
			}
	         
	        // Se arranca el oyente
	        arrancarOyente();
	        
	        // Y se lee el mensaje
	        //leerEntradaUsuario();
	        hiloEntrada = new Thread(() -> leerEntradaUsuario());
	        hiloEntrada.setDaemon(true);
	        hiloEntrada.start();
	        
	     // 🔥 Boucle d’attente : on sort dès que carryOn = false
	        while (carryOn) {
	            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
	        }

	        
		} finally {
			// No se hace nada para garantizar la continuidad.Solicitado por Sonar
			disconnect();
		}

		return true;
	}
	
	private boolean iniciarConexion() {
        try {
        	//Primera conexión
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());

            sendMessage(new ChatMessage(0, MessageType.MESSAGE, username));
            
            ChatMessage response = (ChatMessage) in.readObject();
            id = Integer.parseInt(response.getMessage());
            
            ChatMessage bienvenida = (ChatMessage) in.readObject();
            System.out.println(bienvenida.getMessage());

            LOGGER.log(Level.INFO,
                    "{0} Son las: [{1}]. El id creado por el servidor para el usuario {2} es: {3}",
                    new Object[]{PUB, hora.format(new Date()),username, id});

            return true;

        } catch (IOException | ClassNotFoundException e) {
            LOGGER.log(Level.SEVERE, ERROR, e);
            carryOn = false;
            return false;
        }
    }

	
	/**
	 * Arranca el Oyente
	 */
	private void arrancarOyente() {
		// Se inicializa el oyente
		escuchaCliente = new ChatClientListener(in);
        new Thread(escuchaCliente).start();
	}
	
	/**
	 * Lee la entrada enviada por el usuario
	 */
	private void leerEntradaUsuario() {
		// Inicialización de la entrada
		BufferedReader entrada = new BufferedReader(new InputStreamReader(System.in));

        while (carryOn) {
        	try {
        		if(entrada.ready()) {
        			//Se toma el valor que se va introduciendo por el cliente
        			String texto = entrada.readLine();
            
        			// Se procede según el caso
        			switch (texto.toUpperCase()) {
                		case "LOGOUT" -> {// Si es logout se para el chat del cliente
                			sendMessage(new ChatMessage(id, MessageType.LOGOUT, ""));
                			carryOn = false;
                			break;
                		}
                		case "SHUTDOWN" -> {// Si es shtudown se para el servidor
                			sendMessage(new ChatMessage(id, MessageType.SHUTDOWN, ""));
                			carryOn = false;
                			break;
        
                		} // En caso sontrario se continua normalmente
                		default -> sendMessage(new ChatMessage(id, MessageType.MESSAGE, texto));
        			}
        		}else {
        			Thread.sleep(50);
        		}
        	}catch (Exception e) {
        		break; // SE interrumpe y se sale inmediatamente
        	}
        }
		
	}


	/**
	 * Gestión de mensajes del servidor relativos a cada usuario
	 * 
	 * @param message mensaje a enviar
	 * 
	 */
	@Override
	public void sendMessage(ChatMessage message) {

		try {
			out.writeObject(message);
		// Si falla se informa y se desconecta
		} catch (IOException e) {
			LOGGER.log(Level.SEVERE, ERROR, e);
			disconnect();
		}

	}

	/**
	 * Desconexion del cliente
	 */
	@Override
	public void disconnect() {

		// Se pasa el carriOn a false para indicar el final
		carryOn = false;

		// Se para el oyente si existe todavía
		if (escuchaCliente != null) {
			escuchaCliente.pararChat();
		}

		// Se cierran los streams y el socket
		try {
			if (out != null)
				out.close();
			if (in != null)
				in.close();
			if (socket != null && !socket.isClosed()) {
				socket.close();
			}

		} catch (IOException e) {
			LOGGER.log(Level.SEVERE, ERROR, e);
		}
	}

	/**
	 * Permite obtener el número de identificación del usuario
	 * 
	 * @return id el número de identificación
	 */
	public int getId() {
		return this.id;
	}

	/**
	 * Método main principal
	 * 
	 * @param args argumento del main
	 */
	public static void main(String[] args) {
		// Variables para gestión de los argumentos
		String server = DEFAULT_HOST; // identificación del servidor que se recibe por parámetro
		String username = null; // identificación del usuario que se recibe por parámetro
		int port = DEFAULT_PORT;
		// En función de los parámetros recibidos se actualiza el valor de las variables
		switch (args.length) {
		
			// Cuando hay solamente un argumento, éste corresponde al usuario
			case 1 -> username = args[0]; // El usuario será el que se pase por argumento
		
			// en este caso se reciben ambos datos
			case 2 -> {
				server = args[0]; // el servidor en la primera posición
				username = args[1]; // el usuario en la segunda
			}
			
			// en este caso se reciben ambos datos
			case 3 -> {
				server = args[0]; // el servidor en la primera posición
				username = args[1]; // el usuario en la segunda
				port = Integer.parseInt(args[2]); // El puerto en la tecera
			}

			// En cualquier otro caso se envia mensaje de advertencia
			default -> {
				LOGGER.info(PUB + "Error. Pasar por parámetros [servidor] (opcional) <usuario> (obligatorio) [puerto] (opcional)");
				return;
			}
		}
		
		// Una vez registrados los parámetros se lanza el chat
		ChatClientImpl cliente = new ChatClientImpl(server, username, port);
		cliente.start();

	}

	// ********************************************************************************
    // Classe interna para gestión de la escucha de los clientes
    // ********************************************************************************
	/**
	 * Clase interna que permite quedar a la escucha de los clientes
	 */
	class ChatClientListener implements Runnable {
		// Variable de Entrada de datos
		private final ObjectInputStream in;

		/**
		 * Arranca el oyente
		 * 
		 * @param in
		 * @param active
		 */
		public ChatClientListener(ObjectInputStream in) {
			this.in = in;
		}

		/**
		 * Para el chat del cliente
		 */
		public void pararChat() {
		    carryOn = false;
		    try {
		        in.close();   // desbloquea readObject()
		    } catch (IOException e) {
		        // ignorar
		    }
		}

		/**
		 * Arranca el chat
		 */
		@Override
		public void run() {

			// Se reciben los mensajes en bucle
			while (carryOn) {

				try {
					// Recepción del mensaje
					ChatMessage mensaje = (ChatMessage) in.readObject();
					
					// 🔥 Ajoute ceci :
		            System.out.println("Type reçu : " + mensaje.getType());

					
					// En función del tipo de mensaje se realiza una acción
					switch (mensaje.getType()) {
						//Mensaje normal
		               	case MESSAGE -> 
		               		System.out.println(mensaje.getMessage());
		               		
		               	// Mensaje logout
		               	case LOGOUT -> {
		               		System.out.println("Has sido desconectado.");
		                    carryOn = false;
		                    break;
		               	}
		               	
		               	// Mensaje Shutdown
		               	case SHUTDOWN -> {
		                    System.out.println("El servidor se ha cerrado.");
		                    ChatClientImpl.this.carryOn = false;
		                    ChatClientImpl.this.hiloEntrada.interrupt();
		                    break;
		               	}
		            }
				} catch (IOException e) {
					// Para salir limpiamente en caso de logout
					break;
				} catch (ClassNotFoundException e) {
					// En este es un error grave y se cierra
					carryOn = false;
				}
			}
		}
	}

}