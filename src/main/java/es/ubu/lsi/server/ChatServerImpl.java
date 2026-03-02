/**
 * 
 */
package es.ubu.lsi.server;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

import es.ubu.lsi.common.ChatMessage;
import es.ubu.lsi.common.ChatMessage.MessageType;

/**
 * Implementación del servidor del chat
 * 
 */
public class ChatServerImpl implements ChatServer {
	
	// Puerto por defecto
	private static int DEFAULT_PORT =1500;
	
	//Identificación del cliente
	private static int  clientId=0;
	
	//Hora 
	private static SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
	
	//Puerto que se usa
	private int port;
	
	//Booleano que indica si la sesión está activa
	private boolean alive;
	
	//Socket del usuario
	private Socket socket;
	
	//Socket del servidor
	ServerSocket socketServ ;
	
	//Diccionario con los datos de los usuarios
	Map<String,ServerThreadForClient> cjtoHilosClientes;
	
	//Diccionario con los datos de los usuarios
	Map<Integer,String> cjtoClientes;
	
	//Diccionario de clientes baneados
	Map<String,Boolean> cjtoClientesBaneados;
	
	// Mensaje de patrocinio
	private static String pub = "Fernando patrocina el mensaje : ";
	
	// Mensaje de error
	private static String error = "Error I/O";
	
	// Mensaje de error
	private static String mensusuario = " El usuario ";
		
	//Logger para la gestión de errores
	private static final Logger LOGGER = Logger.getLogger(ChatServerImpl.class.getName());


	/**
	 * Constructor
	 * 
	 * Constructor con el puerto 1500 por defecto
	 */
	public ChatServerImpl() {
		this(DEFAULT_PORT);
		this.alive=true;
		this.cjtoClientes = new HashMap<>();
		this.cjtoClientesBaneados = new HashMap<>();
		this.cjtoHilosClientes = new HashMap<>() ;
	}

	/**
	 * Constructor con el puerto como argumento introducido por parámetro
	 * 
	 * @param port puerto que se usa en el chat
	 */
	public ChatServerImpl(int port) {
	    this.port = port;
	    this.alive = true;
	    this.cjtoClientes = new HashMap<>();
	    this.cjtoClientesBaneados = new HashMap<>();
	    this.cjtoHilosClientes = new HashMap<>();
	}
	
	
	@Override
	public void startup() {
	
	
		try {
			//Creación del socket para el server
			this.socketServ = new ServerSocket(this.port);
			
			//Comunicación de apertura del socket
			if (LOGGER.isLoggable(Level.INFO)) {
				LOGGER.info(pub + "Servidor iniciado a las "+ sdf.format(new Date()));
				LOGGER.info("Escuchando al puerto: " + port );
			}
			
			//Bucle de gestión de los mensajes de los clientes
			while (alive) {
				// El socket abierto para el servidor se usa para el cliente
				socket = socketServ.accept();
				
				// Inicio del hilo del cliente
				ServerThreadForClient hiloCliente = new ServerThreadForClient(socket);
				
				//Se arranca el hilo recién creado
				hiloCliente.start();
			}
			
		}catch(IOException e) {
			e.getStackTrace();
		}

	}
	
	/*
	 * para el sistema 
	 */
	@Override
	public void shutdown() {
		// Se marca como no activo al servidor
		alive = false;
		
		// Parada de los chats de los usuarios
		for (ServerThreadForClient chat :cjtoHilosClientes.values()) {
			// Se informa del cierre
			if (LOGGER.isLoggable(Level.INFO)) {
				LOGGER.info(pub + "Se va a cerrar el chat.");
			}
			chat.stopChat();
		}
		
		// Vaciado de la lista de clientes
		cjtoClientes.clear();
		
		//Una vez parados los chats, se cierra el resto
		try {
			
			// Se informa del cierre
			if (LOGGER.isLoggable(Level.INFO)) {
				LOGGER.info(pub + "Se va a apagar el servidor.");
			}
			
			//Cierre del socket
			socket.close();
			socketServ.close();
			
		}catch(IOException e) {
			LOGGER.log(Level.SEVERE, error, e);
		}
		
		//Una vez cerrado todo se sale del sistema
		System.exit(0);

	}
	
	/*
	 *  Envia los mensajes recibidos a todos los clientes presentes en el chat
	 *  
	 *  @param mensaje
	 */
	@Override
	public void broadcast(ChatMessage mensaje) {

		int usuarioEnvio = mensaje.getId();
		MessageType tipo = mensaje.getType();
		String contenido = mensaje.getMessage();

		// Antes de enviar, se comprueba si es mensaje de baneo/desbaneo
		String[] analisis = contenido.trim().split("\\s+", 2);

		if (analisis.length > 1) {
			String orden = analisis[0];
			String usuarioBan = analisis[1];
			if (orden.equals("ban")&& !cjtoClientesBaneados.containsKey(usuarioBan)) {
				cjtoClientesBaneados.put(usuarioBan, true);
				// Solamente para chequear el conjunto de baneados
				cjtoClientesBaneados.forEach((key, value) -> LOGGER.info(pub + "Cliente baneado clave: " + key + " nick de usuario: " + value));

			} else if (orden.equals("unban") && cjtoClientesBaneados.containsKey(usuarioBan)) {
				cjtoClientesBaneados.remove(usuarioBan, true);
				// Solamente para chequearel conjunto de baneados
				cjtoClientesBaneados.forEach((key, value) -> LOGGER.info(pub + "Cliente baneado clave: " + key + " nick de usuario: " + value));
			}
		}
		// Envío de los mensajes a todos los clientes no baneados
		for (ServerThreadForClient cliente : cjtoHilosClientes.values()) {
			String usuario = "";
			for(Entry<String,ServerThreadForClient> entrada : cjtoHilosClientes.entrySet()) {
				if (entrada.getValue() == cliente) {
					usuario = entrada.getKey();
				}
			}
			// Si el usuario está baneado no se envía el mensaje
			if (cjtoClientesBaneados.get(usuario) != null) {
				return;
			} else {

				ChatMessage nuevo = new ChatMessage(usuarioEnvio, tipo, contenido);
				cliente.sendMessage(nuevo);

			}
		}

	}
	
	/*
	 * Elimina un cliente de la lista usando su id
	 * 
	 * @param id
	 */
	@Override
	public void remove(int id) {
		// Se informa en primer lugar al usuario que se va a cerrar 
		broadcast(new ChatMessage(id,MessageType.MESSAGE, pub + "Se va a eliminar el usuario " + id));
		
		// Se recupera el usuario para cerrarel hilo corresponciente y 
		// se para el chat del usuario
		cjtoHilosClientes.get(cjtoClientes.get(id)).stopChat();
		
		// Se elimina el hilo
		cjtoHilosClientes.remove(cjtoClientes.get(id));
		
		//Se elimina el cliente del diccionario
		cjtoClientes.remove(id);
		
		// Si el cliente está en la lista de baneados se elimina igualmente
		if(cjtoClientesBaneados.get(cjtoClientes.get(id)) != null) {
			cjtoClientesBaneados.remove(cjtoClientes.get(id));
		}

	}
	
		

	/**
	 * Método principal de la clase
	 * @param args argumento del main
	 */
	public static void main(String[] args) {
		// Se lanza el servidor
		ChatServerImpl chatServidor = new ChatServerImpl(DEFAULT_PORT);
		chatServidor.startup();

	}
	
	class ServerThreadForClient extends Thread{
		
		//Identificación del usuario
		private int id;
		
		//Nombre del usuario
		private String username;
		
		// Socket que usa el servidor
		private Socket socket;
		
		//Flujo de entrada
		private ObjectInputStream in;
		
		//Flujo de salida
		private ObjectOutputStream out;
		
		// Marca de actividad del chat
		private boolean activo = true;
		
	
		/*
		 * Constructor
		 * @param id
		 * @param socket
		 */
		public ServerThreadForClient( Socket socket) {
			this.socket = socket;
			this.activo = true;
			
			//Creación de los streams I/O
			try {
				
				this.out = new ObjectOutputStream(socket.getOutputStream());
				this.out.flush();
				this.in = new ObjectInputStream(socket.getInputStream());
				
			}catch(IOException e) {
				LOGGER.log(Level.SEVERE, error, e);
				try {// Si falla se cierra el socket
					socket.close();
				} catch (IOException e1) {
					LOGGER.log(Level.SEVERE, error, e1);
				}
			}
		}
		
		
		public void sendMessage(ChatMessage mensaje) {
			// Envio de un mensaje
			try {
				out.writeObject(mensaje);
				
			}catch(IOException e) {
				LOGGER.log(Level.SEVERE, error, e);
			}
			
		}

		/*
		 * Para un chat
		 */
		public void stopChat() {
			//Para parar el chat se pone el booleano a false
			activo=false;
			
		}
		
		/**
		 * Devuelve el siguiente valor de identificación para un usuario
		 * @return valor de identificación generado
		 */
		private synchronized int getNextId() {
			return ++clientId;
		}

		/*
		 * run tramita los mensajes que vayan llegando
		 * 
		 * espera los mensajes de los clientes y realiza las operaciones correspondientes
		 */
		@Override
		public void run() {
		    try {
		    	// Inicialización de la conexion
		        inicializarConexion();
		        
		        // gestión de los mensajes
		        gestionarMensajes();
		    } catch (IOException | ClassNotFoundException e) {
		        LOGGER.log(Level.SEVERE, error, e);
		    } finally {
		    	// cierre de recursos
		        cerrarRecursos();
		    }
		}
		
		/**
		 * Inicializa la conexión en el método run
		 * @throws IOException
		 * @throws ClassNotFoundException
		 */
		private void inicializarConexion() throws IOException, ClassNotFoundException {
		    // Lectura del primer mensaje del cliente
			ChatMessage primerMensaje = (ChatMessage) in.readObject();
		    this.username = primerMensaje.getMessage();
		    
		    // Control de registro del cliente
		    if (cjtoClientes.containsValue(username)) {
		        out.writeObject(new ChatMessage(0, MessageType.LOGOUT,
		                pub + "Ese usuario ya está registrado"));
		        return;
		    }
		    
		    // Se añade la información del nuevo usuario y del hilo
		    this.id = getNextId();
		    cjtoClientes.put(id, username);
		    cjtoHilosClientes.put(username, this);
		    
		    // Información de la connexión de un nuevo usuario
		    logConexion();
		    
		    // Envío del mensaje de bienvenidsa
		    enviarBienvenida();
		}

		/**
		 * Información que presenta el servidor al iniciar un login un usuario
		 */
		private void logConexion() {
			/* Cuando se trata de la primera conexión se dan las informaciones sobre
			 * el usuario que se ha conectado, el socket y la hora de conexión
			 */
		    if (LOGGER.isLoggable(Level.INFO)) {
		        LOGGER.info(String.format(
		            "%s El usuario %s con ip %s y con id:%s se ha conectado a las: %s",
		            pub, username, socket.getInetAddress().getHostName(), id,
		            ChatServerImpl.sdf.format(new Date())
		        ));
		        // Igualmente se informa de cuántos clientes están conectados en total l
		        LOGGER.info(pub + "Clientes conectados: " + cjtoHilosClientes.size());
		    }
		}
		
		/**
		 * Mensaje de bienvenida que se envía al usuario al conectarse por primera vez
		 * @throws IOException
		 */
		private void enviarBienvenida() throws IOException {
			// Mensaje
		    String bienvenida = String.format(
		        "%sHola %s. Tu id es: %s. Puedes empezar a chatear",
		        pub, username, id
		    );
		    // Envío
		    out.writeObject(new ChatMessage(id, MessageType.MESSAGE, bienvenida));
		}
		
		/**
		 * Gestiona los mensajes del cliente
		 * @throws IOException
		 * @throws ClassNotFoundException
		 */
		private void gestionarMensajes() throws IOException, ClassNotFoundException {
		    while (activo) {
		    	// Lectura del mensaje del cliente
		        ChatMessage mensaje = (ChatMessage) in.readObject();
		        // Se procesa el mensaje del cliente
		        procesarMensaje(mensaje);
		    }
		}
		
		/**
		 * Procesamiento del mensaje del cliente
		 * @param mensaje
		 * @throws IOException
		 */
		private void procesarMensaje(ChatMessage mensaje){
		    switch (mensaje.getType()) {
		        case LOGOUT -> procesarLogout();
		        case SHUTDOWN -> procesarShutdown();
		        default -> procesarPublicacion(mensaje);
		    }
		}
		
		/**
		 * Gestión del mensaje de Logout
		 */
		private void procesarLogout() {
		    logEvento(pub + mensusuario + id +  " se ha desconectado");
		    activo = false;
		    remove(id);
		}
		
		/**
		 * Gestión del mensaje de Shutdown
		 */
		private void procesarShutdown() {
		    logEvento(pub + mensusuario + id + " ha cerrado el servidor");
		    activo = false;
		    System.exit(1);
		}
		
		/**
		 * Gestión de otros mensajes
		 * @param mensaje mensaje recibido del cliente
		 * @throws IOException
		 */
		private void procesarPublicacion(ChatMessage mensaje){
		    
			logEvento(pub + mensusuario +id + " ha publicado: ");
		    
		    broadcast(mensaje);
		}
		
		/**
		 * Informe sobre la acción llevada a cabo por el cliente
		 * @param accion acción que ha efectuado el cliente
		 */
		private void logEvento(String accion) {
		    if (LOGGER.isLoggable(Level.INFO)) {
		        LOGGER.info(String.format(
		            "%s El usuario %s con id:%s %s a las: %s",
		            pub, username, id, accion,
		            ChatServerImpl.sdf.format(new Date())
		        ));
		    }
		}
		
		/**
		 * Cierre de los recursos al finalizar
		 */
		private void cerrarRecursos() {
		    try {
		        in.close();
		        out.close();
		        socket.close();
		    } catch (IOException e) {
		        LOGGER.log(Level.SEVERE, error, e);
		    }
		}
		
		
		
		
	}

}
