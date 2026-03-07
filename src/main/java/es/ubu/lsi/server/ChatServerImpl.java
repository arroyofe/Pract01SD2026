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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import es.ubu.lsi.common.ChatMessage;
import es.ubu.lsi.common.ChatMessage.MessageType;

/**
 * Implementa el servidor
 */
public class ChatServerImpl implements ChatServer {
	// Establecimiento del puerto por defecto
    private static final int DEFAULT_PORT = 1500;
    // Logger para gestión de impresiones
    private static final Logger LOGGER = Logger.getLogger(ChatServerImpl.class.getName());
    
    // Variable de gestión de la hora
    private static final SimpleDateFormat SDF = new SimpleDateFormat("HH:mm:ss");
    // Variable para no repetir el mensaje de esponsorización
    private static final String PUB = "Fernando patrocina el mensaje : ";
    // Variable para no repetir mensaje de error (pedido por Sonar)
    private static final String ERROR = "Error I/O";
    // Variable para no repetir mensaje "El usuario " (pedido por Sonar)
    private static final String USER = " El usuario ";
    // Variable para no repetir segundo mensaje de error (pedido por Sonar)
    private static final String ERROR2 = "No se hace nada, ya está cerrado";
    
    // Variable de identificación del cliente
    private static int clientId = 0;
    
    // Puerto efectivo que se usa
    private final int port;
    
  //Booleano que indica si la sesión está activa
    private volatile boolean alive = true;
    
    // identificación del socket del servidor que se usa
    private ServerSocket serverSocket;

    // Lista de clientes conectados: id → thread client
    private final Map<Integer, ServerThreadForClient> clientes = new ConcurrentHashMap<>();
    
    /**
	 * Constructor
	 * 
	 * Constructor con el puerto 1500 por defecto
	 */
    public ChatServerImpl() {
        this(DEFAULT_PORT);
    }
    
    /**
	 * Constructor con el puerto como argumento introducido por parámetro.
	 * 
	 * @param port puerto que se usa en el chat.
	 */
    public ChatServerImpl(int port) {
        this.port = port;
    }
    
    /*
     * Lanza el proceso
     */
    @Override
    public void startup() {
        try {
        	//Creación del socket para el server
            serverSocket = new ServerSocket(port);
            
            //Comunicación de apertura del socket
            LOGGER.log(Level.INFO,
            	    "{0} Servidor iniciado a las: [{1}]",
            	    new Object[]{ PUB, SDF.format(new Date()) });
            LOGGER.log(Level.INFO,
            	    "Escuchando al puerto: {0}",
            	    new Object[]{port});
            //Bucle de gestión de los mensajes de los clientes
            while (alive) {
            	// Se lanza el método para procesar la conexión
            	procesarConexionCliente();
            }
          // Si no puede abrir el socket se informa del error
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, ERROR, e);
        }
    }
    
    /**
     * Procesa la conexión con el cliente.
     * @throws IOException
     */
    private void procesarConexionCliente() throws IOException{
    	try {
    		// El socket abierto para el servidor se usa para el cliente
    		Socket socket = serverSocket.accept();
    		
    		// Inicio del hilo del cliente
    		ServerThreadForClient hiloCliente = new ServerThreadForClient(socket);
    		hiloCliente.start();
    		
    	}catch (IOException e) {
            // Si ya estaba funcionando se muestra el error
        	if (alive) {
                LOGGER.log(Level.SEVERE, ERROR, e);
            }
        }
    }
    
    /*
     * Para el sistema
     */
    @Override
    public void shutdown() {
        alive = false;

        LOGGER.info(PUB + "Se va a apagar el servidor.");

        // Cierre de lso clientes
        desconectarTodosLosClientes();
        
        // Cerrar el socket del servidor
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, ERROR, e);
        }

    }
    
    /**
     * Desconecta todos los clientes
     */
    public void desconectarTodosLosClientes() {
        for (ServerThreadForClient cliente : clientes.values()) {
            try {
           
            	cliente.disconnect();

            } catch (Exception e) {
                LOGGER.warning("Error desconectando cliente: " + e.getMessage());
            }
        }
        // Se vacía la lista de clientes
        clientes.clear();
    }

    
    /*
	 *  Envia los mensajes recibidos a todos los clientes presentes en el chat
	 *  
	 *  @param mensaje
	 */
    @Override
    public void broadcast(ChatMessage mensaje) {
        // Se obtiene la id contenida en el mensaje
    	int senderId = mensaje.getId();
    	// Se extrae el mensaje enviado
        String contenido = mensaje.getMessage();
        // Se extrae el tipo de mensaje
        MessageType tipo = mensaje.getType();
        
        String senderUsername = null;

        // Se busca el username del en clientes
        for (ServerThreadForClient cliente : clientes.values()) {
        	if (cliente.id == senderId) {
        		senderUsername = cliente.username;
        		break;
        	}
        }

        // Si no se encuentra no se podrá banear
        if (senderUsername == null) {
        	senderUsername = ""; // con esto se ignora el baneo enviado
        }
        
        // Modificación del mensaje recibido para añadir el patrocinio
        // y la indicación del usuario que envía el mensaje
        String contenidoFinal = String.format(
                "%s El usuario: %s%s ha escrito: %s",
                PUB, USER, senderUsername, contenido
        );
     
        // Los mensajes se envian a todos los clientes no baneados
        for (ServerThreadForClient cliente : clientes.values()) {
            if (!cliente.hasBanned(senderUsername)) {
                cliente.enviarMensaje(new ChatMessage(senderId, tipo, contenidoFinal));
            }
        }
    }

    @Override
    public void remove(int id) {
    	// Se cierra el hilo del cliente
        ServerThreadForClient cliente = clientes.remove(id);
        
        // Si existe es cliente se notifica el cierre
        if (cliente != null) {
            LOGGER.log(Level.INFO,
            	    "{0} Usuario eliminado: {1}",
            	    new Object[]{ PUB, id });
        }
    }
    
    /**
	 * Método principal de la clase
	 * @param args argumento del main
	 */
    public static void main(String[] args) {
        // Se instancia el servidor
    	ChatServerImpl server = new ChatServerImpl(DEFAULT_PORT);
        // Se lanza el servidor llamando al método startup
        server.startup();
    }
    
    // *************************************************************************************************
    // Classe interna que gestiona los hilos
    // *************************************************************************************************
    
    /**
     * Clase para la gestión de hilos
     */
    class ServerThreadForClient extends Thread {
    	//Identificación del usuario
        private int id;
        //Nombre del usuario
        private String username;
        
        // Socket que usa el servidor
        private final Socket socket;
        //Flujo de entrada
        private ObjectInputStream in;
        //Flujo de salida
        private ObjectOutputStream out;
        
        // Variable de gestión de actividad del chat
        private volatile boolean active = true;

        // Liste personnelle des bannis : username → true
        private final Map<String, Boolean> baneados = new ConcurrentHashMap<>();
        
        /*
		 * Constructor
		 * @param socket
		 */
        public ServerThreadForClient(Socket socket) {
            this.socket = socket;
        }
        
        /*
		 * run tramita los mensajes que vayan llegando
		 * espera los mensajes de los clientes y realiza las operaciones correspondientes
		 */
        @Override
        public void run() {
            try {
            	// Método para inicializar la conexión
            	inicializarConexion();
            	
            	// Método para procesar los mensajes
            	gestionarMensajes();
            
            // Si no se puede se informa
            } catch (IOException | ClassNotFoundException e) {
                LOGGER.log(Level.SEVERE, ERROR, e);
            // Al finalizar sin resultado se cierran los recursos    
            } finally { 
            	cerrarRecursos();
            	remove(id);
            }
        }
        
        /**
         * Flush para forzar los envíos
         */
        public void flush() {
            try {
                out.flush();
            } catch (IOException e) {
                LOGGER.warning("No se pudo hacer flush del cliente " + username);
            }
        }
        
        /**
		 * Inicializa la conexión en el método run
		 * @throws IOException
		 * @throws ClassNotFoundException
		 */
        private void inicializarConexion() throws IOException, ClassNotFoundException {
            // Inicialización de los hilos de entrada y salida
        	out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());
            
            // Lectura del primer mensaje del cliente
            ChatMessage primerMensaje = (ChatMessage) in.readObject();
            username = primerMensaje.getMessage();

            // Comprobar si el nombre de usuario ya existe. Si existe se informa y se para
            if (clientes.values().stream().anyMatch(c -> c.username.equals(username))) {
                out.writeObject(new ChatMessage(0, MessageType.LOGOUT,
                        PUB + "Ese usuario ya está registrado"));
                active = false;
                return;
            }
            
            // Se crea la identificación para el cliente nuevo
            id = getNextId();
            clientes.put(id, this);
            
            // Se informa de la creación del cliente y de cuántos están conectados
            LOGGER.log(Level.INFO,
            	    "{0} el usuario {1}, {2} con id {3} se ha conectado a las [{4}].",
            	    new Object[]{ PUB, USER,username,id,SDF.format(new Date()) });
            LOGGER.log(Level.INFO,
            	    "{0} Clientes conectados {1}.",
            	    new Object[]{ PUB, clientes.size()});
            
            // Envío de la ID al cliente
            out.writeObject(new ChatMessage(id, MessageType.MESSAGE, String.valueOf(id)));

            // Se da la bienvenida al nuevo usuario
            out.writeObject(new ChatMessage(id, MessageType.MESSAGE,
                    PUB + "Hola " + username + " Bienvendio al chat. Tu id es: " + id + ". Puedes empezar a chatear"));
        }
        
        public void disconnect() {
            active = false;

            try {
            	
                if (in != null) in.close();
                if (out != null) out.close();
                if (socket != null && !socket.isClosed()) {
                	socket.close();
                }
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, ERROR, e);
            }
        }

        
        /**
         * Realiza la gestión de los mensajes entrantes en función de su tipo
         * @throws IOException
         * @throws ClassNotFoundException
         */
        private void gestionarMensajes() throws IOException, ClassNotFoundException {
            // Se leen los mensajes mientras se encuentre activo el chat
        	while (active) {
        		try {
        			// Se lee el mensaje entrante
        			ChatMessage msg = (ChatMessage) in.readObject();
                
        			// En función del tipo se procede de diferente manera
        			// llamndo al método de gestión correspondiente
        			switch (msg.getType()) {
                    	case LOGOUT -> procesarLogout();
                    	case SHUTDOWN -> procesarShutdown();
                    	default -> procesarPublicacion(msg);
        			}
        		}catch (IOException e) { // Si falla se lanza excepción y se sale
                    active = false;
                    break;
                }
            }
        		
        }
        
        /**
         * Procesa el logout del cliente
         */
        private void procesarLogout() {
        	//Información de desconexión
            LOGGER.log(Level.INFO, "{0} El usuario {1}, {2} se ha desconectado.",
                    new Object[]{ PUB, USER, username });
            
            active = false;
            
            //Cierre del socket
            try { 
            	socket.close(); 
            } catch (IOException ignored) {
            	// Si falla se indica que ya está cerrado
            	LOGGER.info(ERROR2);
            }
        }
        
        /**
         * Procesa el cierre del sistema
         */
        private void procesarShutdown() {
        	// Se informa de quién ha cerrado el servidor
            LOGGER.log(Level.INFO,
            	    "{0} El usuario {1}, {2} ha cerrado el servidor.",
            	    new Object[]{ PUB, USER,username});
            
            // Se pasa a falso el chat del usuario
            active = false;
            
            // Enviar SHUTDOWN a todos los clientes
            broadcast(new ChatMessage(0, MessageType.SHUTDOWN, ""));
            
            // Se deja tiempo a los clientes para leer el mensaje y poder
            // desconectarse
            try {
                Thread.sleep(200); 
            } catch (InterruptedException ignored) {
            	LOGGER.info(ERROR2);
            }
            
            // Y se para el servidor
            shutdown();
        }
        
        
        
        /**
         * Gestión del resto de mensajes
         * 
         * @param msg mensaje que se va a tratar
         */
        private void procesarPublicacion(ChatMessage msg) {
            // Extracción del contenido del mensaje
        	String content = msg.getMessage();

            // Control del caso de baneo y desbaneo
            if (content.startsWith("ban ") || content.startsWith("unban ")) {
            	//Se lanza el método de gestión propio
                gestionarBaneos(content);
                return;
            }
            
            // Se notifica la publicación en el servidor y a los usuarios
            LOGGER.log(Level.INFO,
            	    "{0} El usuario {1}, {2} ha publicado un mensaje.",
            	    new Object[]{ PUB, USER,username});
            
            broadcast(msg);
        }
        
        /**
         * Hace la gestión de los mensajes de baneo
         * 
         * @param contenido contenido del mensaje
         */
        private void gestionarBaneos(String contenido) {
            //En caso de baneo se divide el mensaje en dos partes
        	String[] parts = contenido.trim().split("\\s+", 2);
            
        	// Si solamente tiene una parte, se ignora el proceso de baneo/desbaneo
        	if (parts.length < 2) {
        		return;
        	}
        	
        	// Si no se continua
            String comando = parts[0]; // Extracción de la primera parte del mensaje
            String usuario = parts[1]; // Extracción de la segunda parte del mensaje
            
            // Cuando se banea se añade a la lista
            if ("ban".equals(comando)) {
                baneados.put(usuario, true);
                LOGGER.log(Level.INFO,
                        "{0} El usuario {1}, {2} ha baneado a {3}.",
                        new Object[]{ PUB, USER, username, usuario });

            // Cuando se desbanea se quita de la lista
            } else if ("unban".equals(comando)) {
                baneados.remove(usuario);
                LOGGER.log(Level.INFO,
                        "{0} El usuario {1}, {2} ha desbaneado a {3}.",
                        new Object[]{ PUB, USER, username, usuario });
            }
        }
        
        /**
         * Booleano para saber si se ha baneado
         * @param otherUser el usuario baneado
         * @return Verdadero o falso en función de si se ha baneado o no
         */
        public boolean hasBanned(String otherUser) {
            return baneados.containsKey(otherUser);
        }
        
        /**
         * Envia el mensaje
         * @param mensaje mensaje que se va a enviar
         */
        public void enviarMensaje(ChatMessage mensaje) {
            try {
                out.writeObject(mensaje);
                out.flush(); //Para forzar envío
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, ERROR, e);
            }
        }
        
         /**
         * Cierra los recursos al salir
         */
        private void cerrarRecursos() {
            try {
                if (in != null) in.close();
                if (out != null) out.close();
                if (socket != null && !socket.isClosed()) socket.close();
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, ERROR, e);
            }
        }
        
        /**
         * Devuelve el valor de la siguiente identidad para nuevo cliente
         * @return identidad siguiente
         */
        private synchronized int getNextId() {
            return ++clientId;
        }
    }
}