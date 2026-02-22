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
	
	/*
	 * startup
	 * 
	 * Arranca el servidor
	 */
	public void startup();
	
	/*
	 * shutdown
	 * 
	 * Para el servidor
	 */
	public void shutdown();
	
	/*
	 * broadcast
	 * 
	 * Envía el mensaje del servidor a los clientes
	 * 
	 * @param mensaje que se quiere enviar
	 */
	public void broadcast(ChatMessage mensaje);
	
	/*
	 * remove
	 * 
	 * Elimina el cliente cuya identidad se pasa por parámetro
	 * 
	 * @param id del cliente que se quiere eliminar
	 */
	public void remove(int id);
}
