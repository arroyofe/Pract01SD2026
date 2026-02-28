/**
 * 
 */
package es.ubu.lsi.server;

import es.ubu.lsi.common.ChatMessage;

/**
 * Interface ChatServer
 * 
 * Define los métodos del servidor
 * 
 * @author Fernando Arroyo
 */
public interface ChatServer {
	
	/**
	 * Arranca el servidor
	 */
	public void startup();
	
	/**
	 * Para el servidor
	 */
	public void shutdown();
	
	/**
	 * Envía el mensaje del servidor a los clientes
	 * 
	 * @param mensaje que se quiere enviar
	 */
	public void broadcast(ChatMessage mensaje);
	
	/**
	 * Elimina el cliente cuya identidad se pasa por parámetro
	 * 
	 * @param id del cliente que se quiere eliminar
	 */
	public void remove(int id);
}
