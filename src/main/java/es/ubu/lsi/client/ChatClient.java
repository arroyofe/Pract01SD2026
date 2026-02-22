/**
 * 
 */
package es.ubu.lsi.client;

import es.ubu.lsi.common.ChatMessage;

/**
 * Interface ChatClient
 * 
 * Define los métodos a utilizar por el cliente.
 * 
 * @author Fernando Arroyo
 */
public interface ChatClient {
	
	/*
	 * Arranca el cliente
	 * 
	 * @return true si se ha arrancado y false si no se ha arrancado
	 */
	public boolean start();
	
	
	/*
	 * Envia un mensaje del cliente recibido por parámetro
	 * 
	 * @param message
	 */
	public void sendMessage(ChatMessage message);
	
	/*
	 * Desconecta al cliente
	 */
	public void disconnect();

}
