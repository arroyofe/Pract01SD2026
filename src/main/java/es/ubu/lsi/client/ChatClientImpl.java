/**
 * @author Fernando Arroyo
 *
 */
package es.ubu.lsi.client;

import java.io.*;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.*;

import es.ubu.lsi.common.ChatMessage;
import es.ubu.lsi.common.ChatMessage.MessageType;

/**
 * Implementación del cliente de chat.
 * 
 * Esta clase gestiona:
 *  - La conexión con el servidor.
 *  - El envío de mensajes.
 *  - La recepción de mensajes mediante un hilo interno.
 *  - La lectura de la entrada del usuario.
 *  - El cierre ordenado del cliente.
 */
public class ChatClientImpl implements ChatClient {

    /**Constante DEFAULT_PORT. 
     * Valor por defecto del puerto utilizado
     */
        private static final int DEFAULT_PORT = 1500;
    
    /** Constante DEFAULT_HOST.
     * Valor por defecto del host.
     */
    private static final String DEFAULT_HOST = "localhost";

    /**Constante LOGGER. 
     * Logger para seguimiento de las trazas.
     */
    private static final Logger LOGGER = Logger.getLogger(ChatClientImpl.class.getName());

    // Configuración del logger para dar un formato personalizado al mismo.
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
        LOGGER.setUseParentHandlers(false); //Evita los logs en doble desactivando los parientes
        LOGGER.addHandler(handler);
    }

    /** Variable para almacenar el servidor que se usa. */
    private final String server;
    
    /** Variable que almacena el nombre del usuario. */
    private final String username;
    
    /** Variable que almacena el puerto empleado. */
    private final int port;

    /** Variable carry on que indica el estado del cliente. */
    private volatile boolean carryOn = true;

    /** Variable id con el identificador asignado por el servidor. */
    private int id;

    /** Variable in con el stream entrante de comunicación. */
    private ObjectInputStream in;
    
    /** Variable out con el stream entrante de comunicación. */
    private ObjectOutputStream out;

    /** Variable escucha cliente, listener interno. */
    private ChatClientListener escuchaCliente;

    /** Variable socket, representa el socket del cliente. */
    private Socket socket;

    /** Variable hora con el formato del tiempo para usar en los logs y mensajes. */
    private final SimpleDateFormat hora = new SimpleDateFormat("HH:mm:ss");

    /** Variable hilo entrada que sirve para la lectura de entradas del teclado. */
    protected Thread hiloEntrada;
    
    // Constantes de mensajes evita repetir contenido
    /** Constant PUB, con el patrocinio de los mensajes. */
    private static final String PUB = "Fernando patrocina el mensaje : ";
    
    /** Constante ERROR con el mensaje de error de entrada y salida. */
    private static final String ERROR = "Erreur IO";

    /**
     * Constructor principal. Crea el cliente con todos los datos por parámetro.
     * 
     * @param server   servidor usado
	 * @param username usuario que se conecta
	 * @param port     puerto de enlace utilizado
     */
    public ChatClientImpl(String server, String username, int port) {
        this.server = server;
        this.username = username;
        this.port = port;

        try {
            // Conexión al servidor creando un socket
            socket = new Socket(server, port);

            // Flujo de salida (se crea en primer lugar para evitar deadlocks)
            out = new ObjectOutputStream(socket.getOutputStream());

        } catch (IOException e) {
        	// Si falla, se informa del fallo y se cierra el proceso
            LOGGER.log(Level.SEVERE, ERROR, e);
            carryOn = false;
        }
    }
    
    /**
	 * Constructor. Crea el cliente con servidor y cliente. El puerto se
	 * escoge con el valor por defecto.
	 * 
	 * @param server   servidor del sistema.
	 * @param username usuario cliente.
	 */
    public ChatClientImpl(String server, String username) {
        this(server, username, DEFAULT_PORT);
    }

    /**
	 * Constructor Crea el cliente con el cliente solamente.
	 * 
	 * El puerto y el servidor se escogen con el valor por defecto.
	 * 
	 * @param username usuario del cliente.
	 */
    public ChatClientImpl(String username) {
        this(DEFAULT_HOST, username, DEFAULT_PORT);
    }

    /**
     * Arranca el cliente.
     * 
     * @return true si el arranque es correcto y false en caso contrario.
     */
    @Override
    public boolean start() {
    	// Control para saber si el chat está activo, si no lo está, se sale
        if (!carryOn) {
        	return false;
        }

        try {
            /* Conexión inicial con el servidor. Si no hay primera conexión del
             *  cliente algo ha fallado y se procede a la desconexión*/
            if (!iniciarConexion()) {
                disconnect();
                return false;
            }

            // Se arranca el oyente
            arrancarOyente();

            // Se arranca la lectura de teclado
            hiloEntrada = new Thread(this::leerEntradaUsuario);
            hiloEntrada.setDaemon(true);
            hiloEntrada.start();

            // Bucle principal, se sale de él en cuanto pasa a inactivo.
            while (carryOn) {
                try { Thread.sleep(50); 
                } catch (InterruptedException e) {
                	Thread.currentThread().interrupt();
                    break;
                }
            }

        } finally {
            disconnect();
        }

        return true;
    }

    /**
     * Realiza la primera comunicación con el servidor.
     * 
     * @return True si la conexión funciona y False en caso contrario.
     */
    private boolean iniciarConexion() {
        try {
        	//Primera conexión: se habilita el flujo de entrada
            in = new ObjectInputStream(socket.getInputStream());

            // Se envía nombre de usuario para pedir registro en el chat
            sendMessage(new ChatMessage(0, MessageType.MESSAGE, username));

            // Se recibe el ID asignado por el servidor
            ChatMessage response = (ChatMessage) in.readObject();
            id = Integer.parseInt(response.getMessage());

            // Se recibe el mensaje de bienvenida al chat
            ChatMessage bienvenida = (ChatMessage) in.readObject();
            System.out.println(bienvenida.getMessage());

            LOGGER.log(Level.INFO,
                    "{0} Son las: [{1}]. El id creado por el servidor para el usuario {2} es: {3}",
                    new Object[]{PUB, hora.format(new Date()), username, id});

            return true;

        } catch (IOException | ClassNotFoundException e) {
            LOGGER.log(Level.SEVERE, ERROR, e);
            carryOn = false;
            return false;
        }
    }

    /**
     * Arranca el hilo que escucha mensajes del servidor.
     */
    private void arrancarOyente() {
        escuchaCliente = new ChatClientListener(in);
        new Thread(escuchaCliente).start();
    }

    /**
     * Lee la entrada del usuario desde consola.
     */
    private void leerEntradaUsuario() {
    	//Inicialización del buffer de entrada
        BufferedReader entrada = new BufferedReader(new InputStreamReader(System.in));

        while (carryOn) { //Se procede en bucle mientras esté activo.
            try {
            	//Si la entrada se encuentra disponible se trata
                if (entrada.ready()) {
                	//Se toma el valor que se ha introducido el cliente
                    String texto = entrada.readLine();
                    
                    // Se procede según el caso
                    switch (texto.toUpperCase()) {
                    	// Si es logout se para el chat del cliente
                    	case "LOGOUT" -> {
                            sendMessage(new ChatMessage(id, MessageType.LOGOUT, ""));
                            carryOn = false;
                        }
                    	
                    	// Si es shtudown se para el servidor y los otros clientes (si hay)
                        case "SHUTDOWN" -> {
                            sendMessage(new ChatMessage(id, MessageType.SHUTDOWN, ""));
                            carryOn = false;
                        }
                        
                        // En caso sontrario se continua normalmente
                        default -> sendMessage(new ChatMessage(id, MessageType.MESSAGE, texto));
                    }
                // Si no está disponible se da un plazo de espera (necesario  para el cierre)
                } else {
                    Thread.sleep(50);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                e.printStackTrace();
                break;
            }

        }
    }

    /**
     * Envía un mensaje al servidor.
     * 
     * @param message mensaje enviado.
     */
    @Override
    public void sendMessage(ChatMessage message) {
        try {
        	// Toma el mensaje y lo envía por el stream de salida.
            out.writeObject(message);
        } catch (IOException e) {
        	// Si falla se informa y se desconecta
            LOGGER.log(Level.SEVERE, ERROR, e);
            disconnect();
        }
    }

    /**
     * Cierra el cliente de forma ordenada.
     */
    @Override
    public void disconnect() {
    	// Se pasa el carriOn a false para indicar el final.
    	carryOn = false;
        
        // Se para el oyente si existe todavía
        if (escuchaCliente != null) {
            escuchaCliente.pararChat();
        }
        
        // Se cierran los streams y el socket
        try {
            if (out != null) out.close();
            if (in != null) in.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, ERROR, e);
        }
    }

    /**
     * Devuelve el id.
     *
     * @return the id
     */
    public int getId() {
        return id;
    }

    // -------------------------------------------------------------------------
    // -------------------------- CLASE INTERNA --------------------------------
    // -------------------------------------------------------------------------
    
    /**
     * Clase interna que gestiona la escucha de los clientes y detiene el 
     * chat cuando es necesario.
     *
     * @see ChatClientEvent
     */
    class ChatClientListener implements Runnable {

        /** Variable in de entrada de datos. */
        private final ObjectInputStream in;

        /**
         * Instancia un oyente del chat clienter.
         *
         * @param in el stream entrante de comunicación
         */
        public ChatClientListener(ObjectInputStream in) {
            this.in = in;
        }

        /**
         * Detiene el chat.
         */
        public void pararChat() {
            carryOn = false;
            try { 
            	in.close(); 
            } catch (IOException e) {
            	LOGGER.info(ERROR);
            }
        }

        /**
         * Arranca y mantiene el chat mientras esté activo.
         */
        @Override
        public void run() {
            while (carryOn) {
                try {
                	// Se recibe el mensaje entrante.
                    ChatMessage mensaje = (ChatMessage) in.readObject();
                    
                    // En función del tipo de mensaje se toma una acción u otra.
                    switch (mensaje.getType()) {
                    	
                    	// Mensaje normal a ser tratado normalmente, se muestra en consola.
                        case MESSAGE -> System.out.println(mensaje.getMessage());
                        
                        // Mensaje de logout, se informa y se pasa carryOn a false para parar el bucle.
                        case LOGOUT -> {
                            System.out.println("Has sido desconectado del servidor.");
                            carryOn = false;
                        }
                        
                        /* Mensaje de shutdown, se informa, se pasa carryOn a false y si hay 
                         * un hilo abierto se cierra
                         */
                        case SHUTDOWN -> {
                            System.out.println("El servidor se ha cerrado.");
                            carryOn = false;

                            if (hiloEntrada != null && hiloEntrada.isAlive()) {
                                hiloEntrada.interrupt();
                            }
                        }
                    }

                } catch (IOException e) {
                    break;
                } catch (ClassNotFoundException e) {
                    carryOn = false;
                }
            }
        }
    }

    /**
     * Método main.
     *
     * @param args argumentos eventuales que se usen.
     */
    public static void main(String[] args) {
    	// Variables para gestión de los argumentos
        String server = DEFAULT_HOST; // Uso del servidor por defecto
        String username = null;
        int port = DEFAULT_PORT; // Uso del puerto por defecto
        
        // En función de los parámetros recibidos se actualiza el valor de las variables
        switch (args.length) {
        	// Cuando hay solamente un argumento, éste corresponde al usuario
            case 1 -> username = args[0];
            
            // Cuando hay dos, el servidor en la primera posición, el usuario en la segunda
            case 2 -> { server = args[0]; username = args[1]; }
            
            // Cuando hay tres, el servidor en la primera, usuario en la segunda y puerto en tercera posición
            case 3 -> { server = args[0]; username = args[1]; port = Integer.parseInt(args[2]); }
            
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
}
