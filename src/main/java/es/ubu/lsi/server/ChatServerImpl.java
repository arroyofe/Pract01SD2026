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
 * Implementación del servidor de chat.
 * 
 * Esta clase gestiona:
 *  - La creación del socket servidor.
 *  - La aceptación de conexiones entrantes.
 *  - El registro de clientes conectados.
 *  - El envío de mensajes a todos los clientes (broadcast).
 *  - El apagado ordenado del sistema.
 * 
 * Contiene una clase interna (ServerThreadForClient) que gestiona cada cliente.
 */
public class ChatServerImpl implements ChatServer {

    /** Puerto por defecto */
    private static final int DEFAULT_PORT = 1500;

    /** Logger para gestión de las trazas del servidor. */
    private static final Logger LOGGER = Logger.getLogger(ChatServerImpl.class.getName());

    /** Variable para la gestión del formato de hora para los mensajes */
    private static final SimpleDateFormat SDF = new SimpleDateFormat("HH:mm:ss");

    // Constantes para evitar duplicación de cadenas
    
    /** Variable con el mensaje de patrocinio. */
    private static final String PUB = "Fernando patrocina el mensaje : ";
    
    /** Variable con el mensaje fijo sobre el usuario. */
    private static final String USER = " El usuario ";
    
    /** Variable con el mensaje de error de entrada y salida. */
    private static final String ERROR = "Error I/O";
    
    /** Variable con el segundo mensaje de error. */
    private static final String ERROR2 = "No se hace nada, ya está cerrado";

    /**Variable Contador global de clientes (se incrementa en cada conexión). */
    protected static int clientId = 0;

    /** Puerto efectivo del servidor. */
    private final int port;

    /** Indica si el servidor está activo. */
    private volatile boolean alive = true;

    /** Socket del servidor */
    private ServerSocket serverSocket;

    /** Lista de clientes conectados: id → hilo del cliente. */
    protected final Map<Integer, ServerThreadForClient> clientes = new ConcurrentHashMap<>();

    /**
     * Constructor por defecto (puerto 1500).
     */
    public ChatServerImpl() {
        this(DEFAULT_PORT);
    }

    /**
     * Constructor con puerto especificado.
     * 
     * @param port puerto de conexión.
     */
    public ChatServerImpl(int port) {
        this.port = port;
    }

    /**
     * Método principal de arranque del servidor.
     * Crea el ServerSocket y entra en un bucle aceptando conexiones.
     */
    @Override
    public void startup() {
        try {
            // Creación del socket del servidor
            serverSocket = new ServerSocket(port);
            
            //Log de apertura
            LOGGER.log(Level.INFO,
                    "{0} Servidor iniciado a las: [{1}]",
                    new Object[]{ PUB, SDF.format(new Date()) });

            LOGGER.log(Level.INFO,
                    "Escuchando al puerto: {0}",
                    new Object[]{ port });

            // Bucle principal: aceptar conexiones mientras el servidor esté vivo
            while (alive) {
            	// Se lanza el método para procesar la conexión.
                procesarConexionCliente();
            }
         // Si algo falla se informa del error
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, ERROR, e);
        }
    }

    /**
     * Acepta una conexión entrante y lanza un hilo para gestionarla.
     * 
     * @throws IOException.
     */
    private void procesarConexionCliente() throws IOException {
        try {
            // Espera bloqueante hasta que un cliente se conecta
            Socket socket = serverSocket.accept();

            // Se crea un hilo dedicado para ese cliente
            ServerThreadForClient hiloCliente = new ServerThreadForClient(socket);
            hiloCliente.start();

        } catch (IOException e) {
            // Si el servidor sigue activo, se trata como error
            if (alive) {
                LOGGER.log(Level.SEVERE, ERROR, e);
            }
        }
    }

    /**
     * Apaga el servidor de forma ordenada.
     */
    @Override
    public void shutdown() {
        // Se pasa alive a false para parar el bucle de startup
    	alive = false;

        LOGGER.info(PUB + "Se va a apagar el servidor.");

        // Desconectar todos los clientes
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
     * Desconecta todos los clientes que estuvieran todavía conectados.
     */
    public void desconectarTodosLosClientes() {
    	// Se recorre el diccionario con los clientes
        for (ServerThreadForClient cliente : clientes.values()) {
            try {
            	// Y se desconecta el cliente
                cliente.disconnect();
            } catch (Exception e) {
                LOGGER.warning("Error desconectando cliente: " + e.getMessage());
            }
        }
        
        // Al acabar se vacía la lista de clientes.
        clientes.clear();
    }

    /**
     * Envía un mensaje a todos los clientes no baneados por el emisor.
     * 
     * @param mensaje mensaje que se va a enviar
     */
    @Override
    public void broadcast(ChatMessage mensaje) {
    	// Se obtiene la id contenida en el mensaje
        int senderId = mensaje.getId();
        
        // Se obtiene el contenido del mensaje
        String contenido = mensaje.getMessage();
        
        // Se obtiene el tipo de mensaje
        MessageType tipo = mensaje.getType();

        // Buscar el nombre del usuario que envía el mensaje
        String senderUsername = null;
        for (ServerThreadForClient cliente : clientes.values()) {
            if (cliente.id == senderId) {
                senderUsername = cliente.username;
                break;
            }
        }

        // Si no se encuentra el nombre de usuario, no se podrá banear
        if (senderUsername == null) {
            senderUsername = "";
        }

        // Formato final del mensaje con patrocinio
        String contenidoFinal = String.format(
                "%s %s: %s ha escrito: %s",
                PUB, USER, senderUsername, contenido
        );

        // Se envia el mensaje a todos los clientes que no hayan baneado al emisor
        for (ServerThreadForClient cliente : clientes.values()) {
            if (!cliente.haBaneado(senderUsername)) {
                cliente.enviarMensaje(new ChatMessage(senderId, tipo, contenidoFinal));
            }
        }
    }

    /**
     * Elimina un cliente del registro.
     * 
     * @param id identidad del cliente a eliminar.
     */
    @Override
    public void remove(int id) {
    	// Se cierra el hilo del cliente
        ServerThreadForClient cliente = clientes.remove(id);
        
        // Si existe es cliente se logea el cierre
        if (cliente != null) {
            LOGGER.log(Level.INFO,
                    "{0} Usuario eliminado: {1}",
                    new Object[]{ PUB, id });
        }
    }

    /**
     * Método main para lanzar el servidor.
     * 
     * @param args argumentos enventuales del main.
     */
    public static void main(String[] args) {
    	// Se instancia el servidor.
        ChatServerImpl server = new ChatServerImpl(DEFAULT_PORT);
        
        // Se lanza el proceso de startup
        server.startup();
    }

    // -------------------------------------------------------------------------
    // -------------------------- CLASE INTERNA --------------------------------
    // -------------------------------------------------------------------------

    /**
     * Hilo dedicado a gestionar un cliente concreto.
     * 
     * Cada cliente tiene:
     *  - Su propio socket.
     *  - Sus propios flujos de entrada/salida.
     *  - Su propio estado (activo/inactivo).
     *  - Su propia lista de usuarios baneados.
     */
    class ServerThreadForClient extends Thread {

        /** Identificador único del cliente. */
        private int id;

        /** Nombre de usuario. */
        private String username;

        /** Socket asociado al cliente. */
        private final Socket socket;

        /** Flujos de entrada y salida. */
        private ObjectInputStream in;
        private ObjectOutputStream out;

        /** Estado del hilo. */
        private volatile boolean active = true;

        /** Lista de usuarios baneados por este cliente. */
        private final Map<String, Boolean> baneados = new ConcurrentHashMap<>();
        
        /**
         * Constructor: Hilo del cliente.
         * 
         * @param socket en el que se ejecuta el hilo.
         */
        public ServerThreadForClient(Socket socket) {
            this.socket = socket;
        }

        /**
         * Método principal del hilo.
         * Inicializa la conexión y entra en el bucle de lectura de mensajes.
         */
        @Override
        public void run() {
            try {
            	// Se inicializa la conexión.
                inicializarConexion();
                
                // Una vez inicializada se gestiona los mensajes
                gestionarMensajes();
                
            } catch (IOException | ClassNotFoundException e) {
                LOGGER.log(Level.SEVERE, ERROR, e);
            
            // Al finalizar, se cierran los recursos
            } finally {
                cerrarRecursos();
                remove(id);
            }
        }

        /**
         * Inicializa los flujos, recibe el nombre del usuario y registra al cliente.
         * 
         * @throws IOException
		 * @throws ClassNotFoundException
         */
        private void inicializarConexion() throws IOException, ClassNotFoundException {

            // Se crea el flujo de salida primero (esto evita deadlocks)
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();

            // Se crea el flujo de entrada
            in = new ObjectInputStream(socket.getInputStream());

            // Se lee el primer mensaje que contiene el nombre del usuario
            ChatMessage primerMensaje = (ChatMessage) in.readObject();
            username = primerMensaje.getMessage();

            // Se comprueba primero si el nombre ya existe. Si está se informa y se para.
            if (clientes.values().stream().anyMatch(c -> c.username.equals(username))) {
                out.writeObject(new ChatMessage(0, MessageType.LOGOUT,
                        PUB + "Ese usuario ya está registrado"));
                active = false;
                return;
            }

            // Se asigna un ID único al usuario que se ha registrado
            id = getNextId();
            clientes.put(id, this);

            /* Mensajes de log con la identidad del usuario que se ha conectado y 
             * el número de ellos que están conectados
             * */
            LOGGER.log(Level.INFO,
                    "{0} {1} {2} con id {3} se ha conectado a las [{4}].",
                    new Object[]{ PUB, USER, username, id, SDF.format(new Date()) });

            LOGGER.log(Level.INFO,
                    "{0} Clientes conectados {1}.",
                    new Object[]{ PUB, clientes.size() });

            // Se envia ID al cliente
            out.writeObject(new ChatMessage(id, MessageType.MESSAGE, String.valueOf(id)));

            // Se envía el mensaje de bienvenida
            out.writeObject(new ChatMessage(id, MessageType.MESSAGE,
                    PUB + "Hola " + username + " Bienvenido al chat. Tu id es: " + id));
        }

        /**
         * Bucle principal de lectura de mensajes del cliente. Se tratan según el tipo.
         * 
         * @throws IOException
         * @throws ClassNotFoundException
         */
        private void gestionarMensajes() throws IOException, ClassNotFoundException {
        	// Se leen los mensajes mientras se encuentre activo el hilo.
        	while (active) {
                try {
                	// Lectura del mensaje entrante
                    ChatMessage msg = (ChatMessage) in.readObject();

                    switch (msg.getType()) {
                    
                    	// Si es logout se lanza el método que gestiona el logout
                        case LOGOUT -> procesarLogout();
                        
                        // Si es shutdown se lanza el método que gestiona el shutdown
                        case SHUTDOWN -> procesarShutdown();
                        
                        // Los demás mensajes se tratan con el método estándar.
                        default -> procesarPublicacion(msg);
                    }

                } catch (IOException e) {
                    active = false;
                    break;
                }
            }
        }

        /**
         * Procesa la desconexión voluntaria del cliente.
         */
        private void procesarLogout() {
            LOGGER.log(Level.INFO,
                    "{0} {1} {2} se ha desconectado.",
                    new Object[]{ PUB, USER, username });

            active = false;
        }

        /**
         * Procesa el cierre total del servidor solicitado por un cliente.
         */
        private void procesarShutdown() {
            LOGGER.log(Level.INFO,
                    "{0} {1} {2} ha cerrado el servidor.",
                    new Object[]{ PUB, USER, username });

            active = false;

            // Se avisa a todos los clientes
            broadcast(new ChatMessage(0, MessageType.SHUTDOWN, ""));

            // Se da tiempo a que los clientes reciban el mensaje
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) 
            {
            	Thread.currentThread().interrupt();
            	LOGGER.info(ERROR2);
            }

            // Se apaga el servidor
            ChatServerImpl.this.shutdown();
        }

        /**
         * Procesa un mensaje normal (publicación o comando ban/unban).
         * 
         * @param msg mensaje que se va a tratar.
         */
        private void procesarPublicacion(ChatMessage msg) {
            String content = msg.getMessage();

            // Cuando hay un comando de baneo/desbaneo se gestionan con el método adhoc.
            if (content.startsWith("ban ") || content.startsWith("unban ")) {
                gestionarBaneos(content);
                return;
            }
            
            // Se hace un log del mensaje publicado
            LOGGER.log(Level.INFO,
                    "{0} {1} {2} ha publicado un mensaje.",
                    new Object[]{ PUB, USER, username });
            
            // Se envía el mensaje con el método broadcast
            broadcast(msg);
        }

        /**
         * Gestiona los comandos de baneo y desbaneo.
         * 
         * @param contenido contenido del mensaje de ban/unban
         */
        private void gestionarBaneos(String contenido) {
        	// Cuando se produce un baneo se divide en dos parte el mensaje
            String[] parts = contenido.trim().split("\\s+", 2);
            
            // Si no se puede dividir (empiea por ban/unban sin nada más), se termina.
            if (parts.length < 2) {
            	return;
            }
            
            //Cuando se puede dividir se extraen las dos partes
            String comando = parts[0]; // contiene ban/unban
            String usuario = parts[1]; // contiene nombre de usuario
            
            // Cuando se banea se añade a la lista
            if ("ban".equals(comando)) {
                baneados.put(usuario, true);
                LOGGER.log(Level.INFO,
                        "{0} {1} {2} ha baneado a {3}.",
                        new Object[]{ PUB, USER, username, usuario });
            
            // Cuando se desbanea se saca de la lista
            } else if ("unban".equals(comando)) {
                baneados.remove(usuario);
                LOGGER.log(Level.INFO,
                        "{0}  {1} {2} ha desbaneado a {3}.",
                        new Object[]{ PUB, USER, username, usuario });
            }
        }

        /**
         * Indica si este cliente ha baneado a otro usuario.
         * 
         * @param otroUsuario
         * @return True si es verdad y False si no lo ha baneado
         */
        public boolean haBaneado(String otroUsuario) {
            return baneados.containsKey(otroUsuario);
        }

        /**
         * Envía un mensaje al cliente.
         * 
         * @param mensaje el mensaje que se envía.
         */
        public void enviarMensaje(ChatMessage mensaje) {
            if (!active) return;

            try {
                out.writeObject(mensaje);
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, ERROR, e);
            }
        }

        /**
         * Desconecta al cliente cerrando sus recursos.
         */
        public void disconnect() {
            // Se pone active a false para cerrar el hilo.
        	active = false;
            // Se lanza el proceso de cierre.
            cerrarRecursos();
        }

        /**
         * Cierra los flujos y el socket del cliente.
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
         * Genera un nuevo ID único para un cliente.
         * 
         * @reurn clientId clave generada para el nuevo cliente que se conecta.
         */
        private synchronized int getNextId() {
            return ++clientId;
        }
    }
}