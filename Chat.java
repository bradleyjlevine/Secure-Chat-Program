/************************************************
 * Project 2: Whispers in the Dark     			*
 * Authors: Bradley Levine, Jamal Scott			*	
 * Co-author: Dr.Wittman		                *
 * Files: Chat.java, AES.java, ValueStorage.java*  									   			*
 * 									   			*
 ***********************************************/

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigInteger;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Scanner;
import java.util.Random;

public class Chat {
	private Socket socket;
	private boolean client;
	private ObjectOutputStream netOut;
	private ObjectInputStream netIn;
	private Sender sender;
	private Receiver receiver;
	private BigInteger S;
	
	//Diffie-Hellman public parameters	
	public static final BigInteger P = new BigInteger("150396459018121493735075635131373646237977288026821404984994763465102686660455819886399917636523660049699350363718764404398447335124832094110532711100861016024507364395416614225232899925070791132646368926029404477787316146244920422524801906553483223845626883475962886535263377830946785219701760352800897738687");
	public static final BigInteger G = new BigInteger("105003596169089394773278740673883282922302458450353634151991199816363405534040161825176553806702944696699090103171939463118920452576175890312021100994471453870037718208222180811650804379510819329594775775023182511986555583053247825364627124790486621568154018452705388790732042842238310957220975500918398046266");
	public static final int LENGTH = 1023;
	BigInteger a, A, B;
	
	public static void main(String[] args) throws UnknownHostException, IOException 
	{	
		Scanner in = new Scanner(System.in);		
		System.out.println("Welcome to Secure Chat\n");

		boolean valid = false;
	
		try
		{
			do
			{
				System.out.print("Client or server? Enter c or s: ");
				String choice = in.nextLine();
				char letter = choice.toLowerCase().charAt(0);			
				int port; 
				
				if( letter == 's' )
				{
					System.out.print("Enter port number: ");			
					port = in.nextInt();
					new Chat( port );
					valid = true;
				}
				else if( letter == 'c' )
				{
					System.out.print("Enter IP address: ");
					String IP = in.next();
					System.out.print("Enter port number: ");
					port = in.nextInt();
					new Chat( IP, port );
					valid = true;
				}
				else
					System.out.println("Invalid choice.");
			} while( !valid );
		}
		catch( InterruptedException e )
		{}
	}
	
	// Server
	public Chat( int port ) throws IOException, InterruptedException
	{
		client = false;		
		ServerSocket serverSocket = new ServerSocket( port );	
		socket = serverSocket.accept();			
		runChat();	
		serverSocket.close();
	}
	
	// Client
	public Chat( String address, int port ) throws UnknownHostException, IOException, InterruptedException
	{
		client = true;		
		socket = new Socket( address, port );			
		runChat();					
	}
	
	public void runChat() throws InterruptedException, IOException
	{
		netOut = new ObjectOutputStream(socket.getOutputStream() );
		netIn = new ObjectInputStream(socket.getInputStream()) ;

		System.out.println("Running chat...\n");
		sender = new Sender();
		receiver = new Receiver();
		sender.start();
		receiver.start();			
		sender.join();
		receiver.join();
	}
	
	private class Sender extends Thread
	{
		public void run()
		{	
			try
			{
				//this generates a new random integer and checks if the number is equal to P, 0, or 1
				a = new BigInteger(LENGTH, new Random());
				while(a.compareTo(P) == 1 || a.compareTo(BigInteger.ZERO) == 0 || a.compareTo(P) == 0)
					a = new BigInteger(LENGTH, new Random());
				
				//this creates A to send
				A = G.modPow(a, P);

				//sending A to server
				netOut.writeObject(A);
				netOut.flush();
				
				//gets the name of the user and sends it
				Scanner in = new Scanner( System.in );
				System.out.print("Enter your name: ");
				String name = in.nextLine();	
				netOut.writeObject(name);
				netOut.flush();
				
				String buffer = "";
				AES aes = new AES();
				while( !socket.isClosed() )
				{
					buffer = in.nextLine(); 
					
					if( buffer.trim().toLowerCase().equals("quit") )
					{
						netOut.close();
						socket.close();
					}
					else
					{
						short[] text = aes.encrypt(S,buffer);				
						netOut.writeObject(text);
						netOut.flush();
					}
				}		
				
				in.close();
			}
			catch( IOException e ) {}			
		}
	}	

	private class Receiver extends Thread
	{
		@SuppressWarnings("static-access")
		public void run()
		{		
			try
			{
				BigInteger temp = null;
				String temp2 = null;
				String name;
				
				
				//this gets A from the client
				while((temp = (BigInteger)netIn.readObject()) == null);
				
				B = temp;
				
				//this calculates the shared secret key
				S = B.modPow(a, P);
				
				while((temp2 = (String)netIn.readObject()) == null);
				
				name = temp2;
				AES aes = new AES();
				while(!socket.isClosed())					
				{
					short[] bytes = (short[])(netIn.readObject());
					String line = aes.decrypt(S,bytes);
					System.out.println(name + ": " +  line);
				}
			} 
			catch (IOException e)
			{
				System.out.println("Connection closed.");
				//this replaced the System.exit(0) and interrupts the thread that is running when the connection is closed by the client
				this.interrupted();
			}
			catch (ClassNotFoundException e)
			{				
				e.printStackTrace();
			}			
		}
	}
}
