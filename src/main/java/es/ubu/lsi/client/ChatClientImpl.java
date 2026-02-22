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
	private static int DEFAULT_PORT = 1500;
	
	//Puerto por defecto
	private static String DEFAULT_HOST= "localhost";
	
	//Servidor que va a usar el cliente
	private String server;
	
	//Código de usuario del cliente
	private String username;
	
	//Puerto por el que se comunica el cliente
	private int port;
	
	//
	private boolean carryOn = true;
	
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
	
	// Logger para seguimiento de errores
	private static final Logger LOGGER = Logger.getLogger(ChatClientImpl.class.getName());

	/**
	 * Instantiates a new chat client impl.
	 *
	 * @param server the server
	 * @param username the username
	 * @param port the port
	 */
	/*
	 * Constructor Crea los datos del chat con todos los datos por parámetro
	 * 
	 * @param server
	 * @param username
	 * @param port
	 */
	public ChatClientImpl(String server, String username, int port) {
		this.server = server;
		this.username = username;
		this.port = port;
		
		try {
			this.socket = new Socket(this.server,this.port);
			out = new ObjectOutputStream(socket.getOutputStream());
			
		}catch (IOException e) {
			LOGGER.log(Level.SEVERE, "Erreur IO", e);
			System.exit(1);;
			
		}
	}
	
	/*
	 * Constructor Crea los datos del chat con servidor y cliente.
	 * El puerto se escoge con el valor por defecto
	 * 
	 * @param server
	 * @param username
	 */
	public ChatClientImpl(String server, String username) {
		this(server,username,DEFAULT_PORT);
		
	}
	
	/*
	 * Constructor Crea los datos del chat con el cliente solamente.
	 * 
	 * El puerto y el servidor se escogen con el valor por defecto
	 * 
	 * @param username
	 */
	public ChatClientImpl(String username) {
		this(username,DEFAULT_HOST,DEFAULT_PORT);
		
	}
	/*
	 * Metodo de inicio del chat por parte de un usuario
	 * 
	 * @return true si el arranque es correcto y false en caso contrario 
	 */
	@Override
	public boolean start() {
		try {
			//Mensaje de bienvenida si la conexión es correcta
			System.out.println("Fernando patrocina el mensaje: Son las: [" + hora.format(new Date() ) + 
					"]. Conexión establecida correctamente");
			
			//Envío del nombre de usuario al servidor
			ChatMessage mensaje = new ChatMessage(0,MessageType.MESSAGE,username);
			
			try {
				in = new ObjectInputStream(socket.getInputStream());
				sendMessage(mensaje);
				mensaje = (ChatMessage) in.readObject();
				
				// Se actualiza el id con id del servidor
				id=mensaje.getId();
				System.out.println("Fernando patrocina el mensaje: Son las: [" + hora.format(new Date() ) + 
						"]. El id recibido del servidor es: " + id);
				
				//Se lanza el hilo del cliente
				new Thread(new ChatClientListener(in)).start();
				
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
						return true;
					
					case "SHUTDOWN":
						sendMessage(new ChatMessage(id,MessageType.SHUTDOWN,""));
						carryOn = false;
						return true;
					
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
	
	/*
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
			LOGGER.log(Level.SEVERE, "Erreur IO", e);
			disconnect();
		}

	}
	
	/*
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
			LOGGER.log(Level.SEVERE, "Erreur IO", e);
		}
		
		System.exit(0);

	}
	
	public int getId() {
		return this.id;
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) throws IOException {
		// Variables para gestión de los argumentos
		String server = null; // identificación del servidor que se recibe por parámetro
		String username = null; // identificación del usuario que se recibe por parámetro
		int port =0;
		// En función de los parámetros recibidos se actualiza el valor de las variables
		switch (args.length) {
		case 1: // Cuando hay solamente un argumento, éste corresponde al usuario
			server = DEFAULT_HOST; // El servidor será el servidor por defecto
			username = args[0]; // El usuario será el que se pase por argumento
			port=DEFAULT_PORT; // El puerto sera el puerto por defecto
			break;

		case 2: // en este caso se reciben ambos datos
			server = args[0]; // el servidor en la primera posición
			username = args[1]; // el usuario en la segunda
			port=DEFAULT_PORT; // El puerto sera el puerto por defecto
			break;
			
		case 3: // en este caso se reciben ambos datos
			server = args[0]; // el servidor en la primera posición
			username = args[1]; // el usuario en la segunda
			port=Integer.parseInt(args[0]); // El puerto en la tecera
			break;

		default: // En cualquier otro caso se envia mensaje de advertencia
			System.out.println("Fernando patrocina el mensaje:"
					+ "Error. Pasar por parámetos [servidor] (opcional) <usuario> (obligatorio) [puerto] (opcional)");
			break;
		}

		System.out.println("Fernando patrocina el mensaje: Bienvenido: " + username);
		System.out.println("Fernando patrocina el mensaje: Conectando al puerto: " + port +
				" del servidor: " + server);

		// Una vez registrados los parámetros se lanza el chat
		ChatClientImpl cliente = new ChatClientImpl(server, username,port);
		cliente.start();
		

	}
	
	class ChatClientListener implements Runnable{
		//Variable de Entrada de datos
		private ObjectInputStream in;
		
		// Booleano para saber si el cliente está activo y actuar en consecuencia
		private boolean activo=true;
		
		/*
		 * Arranca el oyente
		 * 
		 * @param in
		 * @param active
		 */
		public ChatClientListener(ObjectInputStream in) {
			this.in = in;
			activo = true;
		}

		/*
		 * Para el chat del cliente 
		 */
		public void pararChat() {
			activo= false;
			carryOn = false;
		}
		
		
		/*
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
						
						LOGGER.log(Level.SEVERE, "Erreur IO", e);
					}
				}
		}
	}
		

}