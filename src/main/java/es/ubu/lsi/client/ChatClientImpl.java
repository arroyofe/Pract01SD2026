/**
 * 
 */
package es.ubu.lsi.client;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;


import es.ubu.lsi.common.ChatMessage;
import es.ubu.lsi.common.ChatMessage.*;

/**
 * Implementa el chat del cliente 
 * @author Fernando Arroyo
 */
public class ChatClientImpl implements ChatClient {
	
	//Puerto por defecto
	private static int defaultPort = 1500;
	
	//Puerto por defecto
	private static String defaultHost= "localhost";
	
	//Servidor que va a usar el cliente
	private String server;
	
	//Código de usuario del cliente
	private String username;
	
	//Puerto por el que se comunica el cliente
	private int port;
	
	//
	private volatile boolean carryOn = true;
	
	//identificación numérica del cliente
	private static int id;
	
	//Flujo de entrada
	private ObjectInputStream in;
	
	//Flujo de salida
	private ObjectOutputStream out;
	
	//Variable Listener
	private ChatClientListener escuchaCliente;
	
	
	//Variable socket para la gestión de las conexiones
	private static Socket socket;
	
	//Hora 
	private SimpleDateFormat hora = new SimpleDateFormat("HH:mm:ss");
	
	// Mensaje de patrocinio
	private static String pub = "Fernando patrocina el mensaje : ";
	
	// Mensaje error
	private static String error = "Erreur IO";
	
	// Logger para seguimiento de errores
	private static final Logger LOGGER = Logger.getLogger(ChatClientImpl.class.getName());

	/**
	 * Constructor Crea los datos del chat con todos los datos por parámetro
	 * 
	 * @param server servidor
	 * @param username usuario que se conecta
	 * @param port puerto de enlace
	 */
	@SuppressWarnings("static-access")
	public ChatClientImpl(String server, String username, int port) {
		this.server = server;
		this.username = username;
		this.port = port;
		
		try {
			this.socket = new Socket(this.server,this.port);
			out = new ObjectOutputStream(socket.getOutputStream());
			
		}catch (IOException e) {
			LOGGER.log(Level.SEVERE, error, e);
			System.exit(1);
			
		}
	}
	
	/**
	 * Constructor Crea los datos del chat con servidor y cliente.
	 * El puerto se escoge con el valor por defecto
	 * 
	 * @param server servidor del sisteama
	 * @param username usuario cliente
	 */
	public ChatClientImpl(String server, String username) {
		this(server,username,defaultPort);
		
	}
	
	/**
	 * Constructor Crea los datos del chat con el cliente solamente.
	 * 
	 * El puerto y el servidor se escogen con el valor por defecto
	 * 
	 * @param username usuario del cliente
	 */
	public ChatClientImpl(String username) {
		this(username,defaultHost,defaultPort);
		
	}
	/**
	 * Metodo de inicio del chat por parte de un usuario
	 * 
	 * @return true si el arranque es correcto y false en caso contrario 
	 */
	@Override
	public boolean start() {
		try {
			//Mensaje de bienvenida si la conexión es correcta
			LOGGER.log(Level.INFO, "{0} Son las: [{1}]. Conexión establecida correctamente", 
			           new Object[]{pub, hora.format(new Date())});
			
			//Envío del nombre de usuario al servidor
			ChatMessage mensaje = new ChatMessage(0,MessageType.MESSAGE,username);
			
			try {
				out.flush();
				in = new ObjectInputStream(socket.getInputStream());
				sendMessage(mensaje);
				mensaje = (ChatMessage) in.readObject();
				
				// Se actualiza el id con id del servidor
				id=mensaje.getId();
				if (LOGGER.isLoggable(Level.INFO)) {
					LOGGER.log(Level.INFO, "{0} Son las: [{1}]. El id recibido del servidor es:" + id, 
					           new Object[]{pub, hora.format(new Date())});
				}
				
				//Se lanza el hilo del cliente
				escuchaCliente = new ChatClientListener(in);
				new Thread(escuchaCliente).start();
				
			}catch(IOException | ClassNotFoundException e) {
				e.getStackTrace();
				System.exit(1);
			}
			
			try (Scanner entrada = new Scanner(System.in)){
				
				while (carryOn) {
					String texto = entrada.nextLine();
					
					switch(texto.toUpperCase()){
					case "LOGOUT":
						sendMessage(new ChatMessage(id,MessageType.LOGOUT,""));
						carryOn=false;
						break;
					
					case "SHUTDOWN":
						sendMessage(new ChatMessage(id,MessageType.SHUTDOWN,""));
						carryOn = false;
						break;
					
					default:
						sendMessage(new ChatMessage(id,MessageType.MESSAGE,texto));
						break;
					
					}
				}
				
			}
			
		}finally{
			//Al terminar el chat se desconecta al usuario
			disconnect();
		}
		
		return true;
	}
	
	/**
	 * Gestión de mensajes del servidor relativos a cada usuario
	 * 
	 * @param mensaje a enviar
	 * 
	 */
	@Override
	public void sendMessage(ChatMessage message) {

		try {
			out.writeObject (message);
		}catch (IOException e){
			LOGGER.log(Level.SEVERE, error, e);
			disconnect();
		}

	}
	
	/**
	 * Desconexion del cliente
	 */
	@Override
	public void disconnect() {
		
		// Se para el oyente
		escuchaCliente.pararChat();
		
		// Se pasa el carriOn a false para indicar el final
		carryOn = false;
		
				
		//Se cierran los streams y el socket
		try {
			out.close();
			in.close();
			socket.close();
			
		}catch(IOException e) {
			LOGGER.log(Level.SEVERE, error, e);
		}
		
		System.exit(0);

	}
	/**
	 * Permite obtener el número de identificación del usuario
	 * 
	 * @return id el número de identificación
	 */
	@SuppressWarnings("static-access")
	public int getId() {
		return this.id;
	}

	/**
	 * Método main principal
	 * @param args argumento del main
	 * @throws IOException en caso de error de entrada:salida
	 */
	public static void main(String[] args) throws IOException {
		// Variables para gestión de los argumentos
		String server = null; // identificación del servidor que se recibe por parámetro
		String username = null; // identificación del usuario que se recibe por parámetro
		int port =0;
		// En función de los parámetros recibidos se actualiza el valor de las variables
		switch (args.length) {
		case 1: // Cuando hay solamente un argumento, éste corresponde al usuario
			server = defaultHost; // El servidor será el servidor por defecto
			username = args[0]; // El usuario será el que se pase por argumento
			port=defaultPort; // El puerto sera el puerto por defecto
			break;

		case 2: // en este caso se reciben ambos datos
			server = args[0]; // el servidor en la primera posición
			username = args[1]; // el usuario en la segunda
			port=defaultPort; // El puerto sera el puerto por defecto
			break;
			
		case 3: // en este caso se reciben ambos datos
			server = args[0]; // el servidor en la primera posición
			username = args[1]; // el usuario en la segunda
			port=Integer.parseInt(args[0]); // El puerto en la tecera
			break;

		default: // En cualquier otro caso se envia mensaje de advertencia
			if (LOGGER.isLoggable(Level.INFO)) {
				LOGGER.info(pub + "Error. Pasar por parámetos [servidor] (opcional) <usuario> (obligatorio) [puerto] (opcional)");
			}
			break;
		}
		if (LOGGER.isLoggable(Level.INFO)) {

			LOGGER.info(pub + "Bienvenido: " + username);
			LOGGER.info(pub + "Conectando al puerto: " + port + " del servidor: " + server);
		}

		// Una vez registrados los parámetros se lanza el chat
		ChatClientImpl cliente = new ChatClientImpl(server, username,port);
		cliente.start();
		

	}
	/**
	 * Clase interna que permite quedar a la escucha de los clientes
	 */
	class ChatClientListener implements Runnable{
		//Variable de Entrada de datos
		private ObjectInputStream in;
		
		// Booleano para saber si el cliente está activo y actuar en consecuencia
		@SuppressWarnings("unused")
		private boolean activo=true;
		
		/**
		 * Arranca el oyente
		 * 
		 * @param in
		 * @param active
		 */
		public ChatClientListener(ObjectInputStream in) {
			this.in = in;
			activo = true;
		}

		/**
		 * Para el chat del cliente 
		 */
		public void pararChat() {
			activo= false;
			carryOn = false;
		}
		
		
		/**
		 * Arranca el chat
		 */
		@Override
		public void run() {
			
				// Se reciben los mensajes en  bucle
				while (carryOn) {
					ChatMessage mensaje;
					try {
						mensaje = (ChatMessage) in.readObject();
						System.out.println(mensaje.getMessage());
						
					} catch (ClassNotFoundException | IOException e) {
						
						LOGGER.log(Level.SEVERE, error, e);
					}
				}
		}
	}
		

}