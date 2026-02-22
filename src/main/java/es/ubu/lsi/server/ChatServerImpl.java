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
		this.cjtoClientes = new HashMap<Integer,String>();
		this. cjtoClientesBaneados = new HashMap<String,Boolean>();
		this.cjtoHilosClientes = new HashMap<String,ServerThreadForClient>() ;
	}

	/**
	 * Constructor
	 * 
	 * Constructor con el puerto como argumento introducido por parámetro
	 * 
	 * @param port
	 * @param alive
	 */
	public ChatServerImpl(int port) {
		this.port=port;
		this.alive=true;
		this.cjtoClientes = new HashMap<Integer,String>();
		this. cjtoClientesBaneados = new HashMap<String,Boolean>();
		this.cjtoHilosClientes = new HashMap<String,ServerThreadForClient>() ;
	}
	
	private synchronized int getNextId() {
		return ++clientId;
	}
	
	@Override
	public void startup() {
	
		// Determinación del puerto
	//	this.port = DEFAULT_PORT;
		
		try {
			//Creación del socket para el server
			this.socketServ = new ServerSocket(this.port);
			
			//Comunicación de apertura del socket
			System.out.println("Fernando patrocina el mensaje: Servidor iniciado a las "
					+ sdf.format(new Date()));
			System.out.println("Escuchando al puerto: " + port );
			
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
			System.out.println("Se va a cerrar el chat.");
			chat.stopChat();
		}
		
		// Vaciado de la lista de clientes
		cjtoClientes.clear();
		
		//Una vez parados los chats, se cierra el resto
		try {
			
			// Se informa del cierre
			System.out.println("Se va a apagar el servidor.");
			
			//Cierre del socket
			socket.close();
			socketServ.close();
			
		}catch(IOException e) {
			LOGGER.log(Level.SEVERE, "Erreur IO", e);
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
				cjtoClientesBaneados.forEach((key, value) -> System.out.println("Cliente baneado clave: " + key + " nick de usuario: " + value));

			} else if (orden.equals("unban") && cjtoClientesBaneados.containsKey(usuarioBan)) {
				cjtoClientesBaneados.remove(usuarioBan, true);
				// Solamente para chequearel conjunto de baneados
				cjtoClientesBaneados.forEach((key, value) -> System.out.println("Cliente baneado clave: " + key + " nick de usuario: " + value));
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
		broadcast(new ChatMessage(id,MessageType.MESSAGE,"Fernando patrocina el mensaje: se va a eliminar el usuario " + id));
		
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
	 * @param args
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
		
		private boolean activo = true;
		

		/*
		 * Constructor
		 * @param id
		 * @param socket
		 */
		public ServerThreadForClient( Socket socket) {
		//	this.id = id;
		//	this.username = username; 
			this.socket = socket;
			this.activo = true;
			
			//Creación de los streams I/O
			try {
				this.in = new ObjectInputStream(socket.getInputStream());
				this.out = new ObjectOutputStream(socket.getOutputStream());
				
			}catch(IOException e) {
				e.printStackTrace();
				try {// Si falla se cierra el socket
					socket.close();
				} catch (IOException e1) {
					LOGGER.log(Level.SEVERE, "Erreur IO", e);
				}
			}
		}
		
		public void sendMessage(ChatMessage mensaje) {
			// Envio de un mensaje
			try {
				out.writeObject(mensaje);
				
			}catch(IOException e) {
				LOGGER.log(Level.SEVERE, "Erreur IO", e);
			}
			
		}

		/*
		 * Para un chat
		 */
		public void stopChat() {
			//Para parar el chat se pone el booleano a false
			activo=false;
			
		}
		
		

		/*
		 * run tramita los mensajes que vayan llegando
		 * 
		 * espera los mensajes de los clientes y realiza las operaciones correspondientes
		 */
		public void run() {
			
			try {
			
				// Lectura del primer mensaje con el nombre de usuario
				ChatMessage primerMensaje = (ChatMessage) in.readObject();
				
				this.username = primerMensaje.getMessage();
				
				String usuario = primerMensaje.getMessage();
				
				if (cjtoClientes.containsValue(usuario)) {
					out.writeObject(new ChatMessage(0,MessageType.LOGOUT,"Ese usuario ya está registrado"));
				}else {
					// Se añade la información del nuevo usuario y del hilo
					this.id = getNextId();
					cjtoClientes.put(id,usuario);
					cjtoHilosClientes.put(usuario, this);
					
				}
				
							
				//Se informa de la conexión del usuario
				System.out.println("Fernando patrocina el mensaje : El usuario " + usuario + " con ip " 
						+ this.socket.getInetAddress().getHostName() +" y con id:" 
						+ this.id +" se ha conectado a las: " + ChatServerImpl.sdf.format(new Date( ))  );
				
				System.out.println("Clientes conectados: "+ cjtoHilosClientes.size());
				
				String bienvenida = String.format("Hola " + username + "Tu id es: " + id +
						"Puedes empezar a chatear");
				
				out.writeObject(new ChatMessage(id,MessageType.MESSAGE,bienvenida));
				
				
				//Bucle degestión de los mensajes
				while(activo) {
					//Se lee el mensaje del cliente
					ChatMessage mensaje = (ChatMessage) in.readObject();
					
					//Si el cliente envía el mensaje de logout se le desactiv
					if(mensaje.getType()==MessageType.LOGOUT) {
						
						System.out.println("Fernando patrocina el mensaje : " + this.username +" con id:" 
								+ this.id + " se ha desconectado a las: " + ChatServerImpl.sdf.format(new Date( )));						
						activo = false;
						remove(id);
						
					}else if(mensaje.getType()==MessageType.SHUTDOWN) {
						activo = false;//SHUTDOWN no se requiere en la práctica
						System.out.println("Fernando patrocina el mensaje : " + this.username +" con id:" 
								+ this.id + " se ha cerrado el servidor a las: " + ChatServerImpl.sdf.format(new Date( )));	
						System.exit(1);
					}else { //En caso contrario se publica el mensaje 
						System.out.println("Fernando patrocina el mensaje : El usuario " + this.username +" con id:" 
								+ this.id + " ha publicado a las: " + ChatServerImpl.sdf.format(new Date( )));
						
						broadcast(mensaje);
						
					}
					
				}
				
			}catch(IOException e) {
				
				LOGGER.log(Level.SEVERE, "Erreur IO", e);
				
			} catch (ClassNotFoundException e) {
				
				LOGGER.log(Level.SEVERE, "Erreur IO", e);
				
			}finally {// Se cierra el chat y sus componentes cuanto finaliza el cliente
				try {
					in.close();
					out.close();
					socket.close();
				//	chatServer.remove(id);
				} catch (IOException e) {
					
					LOGGER.log(Level.SEVERE, "Erreur IO", e);
				}		
			}
			
		}
		
	}

}
