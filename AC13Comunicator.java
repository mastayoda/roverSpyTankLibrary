package com.uceta.AC13Comunicatior;

import java.io.BufferedWriter;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.concurrent.Semaphore;

import org.apache.http.HttpResponse;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.DefaultHttpClient;


import android.util.Log;


public class AC13Comunicator {
	
	//region Declarations

	//Codes
	/*
	 * Right Foward = 5
	 * Left	Foward	= 7
	 * Foward = 5 and 7
	 * Right Backward = 6
	 * Left Backward = 8
	 * Backward = 6 and 8
	 * 
	 * High Resolution = http://192.168.1.100/set_params.cgi?resolution=32
	 * Low Resolution = High Resolution = http://192.168.1.100/set_params.cgi?resolution=8
	 */
	
	//Robot Parameters
	private class Parameters
	{
		public String id;
		public String sys_ver;
		public String app_ver;
		public String alias;
		public String adhoc_ssid;
		public String username;
		public String userpwd;
		public String resolution;
		public String ip;
		public String mask;
		public String gateway;
		public String port;
		public String wifi_ssid;
		public String wifi_encrypt;
		public String wifi_defkey;
		public String  wifi_key1;
		public String  wifi_key2;
		public String  wifi_key3;
		public String  wifi_key4;
		public String  wifi_authtype;
		public String  wifi_keyformat;
		public String  wifi_key1_bits;
		public String  wifi_key2_bits;
		public String  wifi_key3_bits;
		public String  wifi_key4_bits;
		public String  wifi_wpa_psk;
		
		
	}
	
	private Parameters PARAMETERS;
	
	//Thread Handling objects
	private Thread T;
	private Semaphore semaphore;
	
	// TCP/IP sockets
	private Socket cSock;
	private Socket vSock;

	// Flags to store state of the connection
	boolean streaming = false;
	boolean isConnected = false;
	
	// Infrared on/off
	boolean infrared;

	//Command Buffer
	byte[] cmdBuffer;

	// The maximal image buffer will be sufficient for hi-res images, notice
	// that JPEG images do not have a default image size. The TCP buffer is
	// large enough for the packages send by the Rover, but the latter tends
	// to chop images across multiple TCP chunks so it is not large enough
	// for one image.
	int maxTCPBuffer = 2048;
	int maxImageBuffer = 231072;
	byte[] imageBuffer = new byte[maxImageBuffer];
	int imagePtr = 0;
	int tcpPtr = 0;
	private int imageStartPosition;
	private int imageLength;


	// Debugging variables
	public int socketReadCount = 0;
	
	// endRegion Declarations

	//region Main Methods

	public AC13Comunicator() {
		
		PARAMETERS = new Parameters();
	}
	
	public AC13Comunicator(Semaphore semaphore ){
		
		this();
		this.semaphore = semaphore;
	}
	
	public boolean isConnected(){
		return this.isConnected;
	}
	
	public String getInfraredState()
	{
		return (infrared)?"On":"Off";	
	}
	
	public boolean Connect() {

		try {
			
			//Initializing command socket
			SocketAddress sockaddr = new InetSocketAddress("192.168.1.100", 80);
			cSock = new Socket();
			cSock.connect(sockaddr,10000);
	
			
				
			//Setting the connection
			WriteStart();
			ReceiveAnswer(0);
			
			cSock.close();
			
			//Reinitializing the command socket
			cSock = new Socket();
			cSock.connect(sockaddr,10000);
			
			byte[] buffer = new byte[2048];
			
			for (int i = 1; i < 4; i++) {
				
				WriteCmd(i, null);
				buffer = ReceiveAnswer(i);
			}
			
			byte[] imgid = new byte[4];
			
			for (int i = 0; i < 4; i++)
				imgid[i] = buffer[i + 25];

			vSock = new Socket();
			vSock.connect(sockaddr,10000);
			WriteCmd(4, imgid);
			
			startStreaming();
			
			isConnected = true;
			
			//Getting Parameters
			new Thread(new GetParametersThread()).start();
			
		} catch (Exception e) {
			 return false;	
		}
		
		return true;
	}

	public boolean Disconnect(){
		
		try {
			
		    streaming = false;
		    
		    if(infrared)
		    	SwitchInfrared();
		    
			cSock.close();
			vSock.close();
			
		} catch (Exception e) {
			return false;
		}
		
		return true;	
	}
	
	
	public void SwitchInfrared() {
		
		if (infrared)
			WriteCmd(11,null);
		else
			WriteCmd(10,null);

		infrared = !infrared;
	}

	private byte[] ReceiveAnswer(int i) {
		
		byte[] buffer = new byte[2048];
		try {
			
		 cSock.getInputStream().read(buffer, 0, 2048);
			
		} catch (Exception eg) {
			eg.printStackTrace();
		}
		return buffer;
	}
	
	private void WriteStart() {
		try {

			PrintWriter out = new PrintWriter(new BufferedWriter(
					new OutputStreamWriter(cSock.getOutputStream())), true);
			out.println("GET /check_user.cgi?user=AC13&pwd=AC13 HTTP/1.1\r\nHost: 192.168.1.100:80\r\nUser-Agent: WifiCar/1.0 CFNetwork/485.12.7 Darwin/10.4.0\r\nAccept: */*\r\nAccept-Language: en-us\r\nAccept-Encoding: gzip, deflate\r\nConnection: keep-alive\r\n\r\n!");

		} catch (Exception e) {
		}
	}
	
	// endRegion Main Methods
	
	//region Image Methods
	
	private boolean startStreaming(){
		try
		{	
			//Start Streaming
			streaming = true;
			T = new Thread(new VideoStreamingThread());
			T.start();	
		
		}
		catch (Exception e) {
			 return false;
			
		}
		return true;
	}
	
	public void SwitchTo640X480Resolution(){
		
		new Thread(new ResolutionCommandThread(32)).start();	
	}
	
	public void SwitchTo320X240Resolution(){	
		
		new Thread(new ResolutionCommandThread(8)).start();
	}
		
	public boolean isStreaming(){
		
		return streaming;	
	}
	
	public byte[] GetImageBuffer(){
		
		byte[] cleanImage = new byte[GetImageLength()];
		System.arraycopy(imageBuffer,  GetImageStartPosition(), cleanImage, 0, GetImageLength());
		//return 	Arrays.copyOfRange(imageBuffer, GetImageStartPosition(), GetImageStartPosition() + GetImageLength());
		return cleanImage;
	}
	
	public byte[] GetRawImageBuffer(){	
		
		return imageBuffer;	
	}
	
	public int  GetImageLength(){	
		
		return this.imageLength;
	}
	
	public int GetImageStartPosition(){
		
		return this.imageStartPosition;
	}
	
	private void SetImageStartPosition(int start){	
		this.imageStartPosition = start;	
	}
	
	private void SetImageLength(int len){	
		this.imageLength = len;	
	}
		
	private boolean ImgStart(byte[] start) {
		return (start[0] == 'M' && start[1] == 'O' && start[2] == '_' && start[3] == 'V');
	}
	
	private void ReceiveImage() {	
		try {
			
			int len = 0;
			int newPtr = tcpPtr;
			int imageLength = 0;
			boolean fnew = false;
			
			while (!fnew && newPtr < maxImageBuffer - maxTCPBuffer) {
				
				len = vSock.getInputStream().read(imageBuffer, newPtr, maxTCPBuffer);
				// todo: check if this happens too often and exit
				if (len <= 0) continue;

				byte[] f4 = new byte[4];
				
				for (int i = 0; i < 4; i++)
					f4[i] = imageBuffer[newPtr + i];
				
				if (ImgStart(f4) && (imageLength > 0))
					fnew = true;
				
				if (!fnew) //OLD IMAGE, SO WHAT THE SOCKET GOT WAS A CHUNK OF AN IMAGE
				{
					newPtr += len;
					imageLength = newPtr - imagePtr;
				} 
				else //NEW IMAGE
				{
						
					SetImageStartPosition(imagePtr + 36);//PORB 36 ES LA CANTIDAD MAXIMA DE BYTES DE IMAGEN QUE LEES, CADA VEZ
														//O TAMBN PUEDE SER QUE AL SUMARLE 36 ELIMINAS EL ENCABEZADO DE LA IMG
					SetImageLength(imageLength - 36);
				
					if (newPtr > maxImageBuffer / 2) 
					{
						// copy first chunk of new arrived image to start of
						// array
						for (int i = 0; i < len; i++)
							imageBuffer[i] = imageBuffer[newPtr + i];
						imagePtr = 0;
						tcpPtr = len;	
						
					} else 
					{
						imagePtr = newPtr;
						tcpPtr = newPtr + len;
					}
					
				}//END ELSE
				
			}//END WHILE
					
			// reset if ptr runs out of boundaries
			if (newPtr >= maxImageBuffer - maxTCPBuffer) {
				imagePtr = 0;
				tcpPtr = 0;
			}
			
		} catch (Exception eg) {
		 // Log.v("Comunicator ERROR", eg.toString());
		}	
	}
	
	private void ReceiveImageUsingSemaphore() {
		
		try {
			
			int len = 0;
			int newPtr = tcpPtr;
			int imageLength = 0;
			boolean fnew = false;
			
			while (!fnew && newPtr < maxImageBuffer - maxTCPBuffer) {
				
				len = vSock.getInputStream().read(imageBuffer, newPtr, maxTCPBuffer);
				// todo: check if this happens too often and exit
				if (len <= 0) continue;	//WILLIAM: IF NOTHING GOTTEN IN BUFFER CONTINUE

				byte[] f4 = new byte[4];
				
				for (int i = 0; i < 4; i++)	//READ FROM THE NEW BUFFER THE 4 BYTES THAT REPRESENT THE IMAGE 'MO_V'
					f4[i] = imageBuffer[newPtr + i];
				
				if (ImgStart(f4) && (imageLength > 0))	//CHECK IF THE IMAGE IS THERE
					fnew = true;
				
				if (!fnew) 
				{
					newPtr += len;
					imageLength = newPtr - imagePtr;
				} 
				else 
				{
					
					SetImageStartPosition(imagePtr + 36);
					SetImageLength(imageLength - 36);
					
					//Releasing the client threat (notifying that there is a new image ready)
				    semaphore.release();
				
					if (newPtr > maxImageBuffer / 2) 
					{
						// copy first chunk of new arrived image to start of
						// array
						for (int i = 0; i < len; i++)
							imageBuffer[i] = imageBuffer[newPtr + i];
						imagePtr = 0;
						tcpPtr = len;	
						
					} else 
					{
						imagePtr = newPtr;
						tcpPtr = newPtr + len;
					}
					
				}//END ELSE
				
			}//END WHILE
					
			// reset if ptr runs out of boundaries
			if (newPtr >= maxImageBuffer - maxTCPBuffer) {
				imagePtr = 0;
				tcpPtr = 0;
			}
			
		} catch (Exception eg) {
		 // Log.v("Comunicator ERROR", eg.toString());
		}
		
	}
	
	private class VideoStreamingThread implements Runnable {
		public void run() {
			try {
				
				if(semaphore == null)
					while(streaming)
						ReceiveImage();
				else
					while(streaming)
						ReceiveImageUsingSemaphore();
					
				
			} catch (Exception e) {
			
				Log.v("Video Thread ERROR", e.toString());
			}
		}
	}
	
	private class ResolutionCommandThread implements Runnable {
		
		int command;
		
		public ResolutionCommandThread(int command) {
			this.command = command;
		}
		
		public void run() {
			try {

				
				
		         HttpClient mClient= new DefaultHttpClient();
		         HttpGet get = new HttpGet("http://192.168.1.100/set_params.cgi?resolution=" + command);
		         //HttpGet get = new HttpGet("http://192.168.1.100/set_params.cgi?get_params.cgi?resolution");
		         //HttpGet get = new HttpGet("http://192.168.1.100/check_user.cgi?user=AC13&pwd=AC13");
		         get.addHeader(BasicScheme.authenticate(new UsernamePasswordCredentials("AC13", "AC13"),"UTF-8", false));
		         
		         mClient.execute(get);
		         HttpResponse response = mClient.execute(get);
		        
		      
		         BufferedReader rd = new BufferedReader(new InputStreamReader(
							response.getEntity().getContent()));
					String line = "";
					while ((line = rd.readLine()) != null)  
							Log.v("RESOLUTION COMMAND", line + " " + command);
					
			
		         
			} catch (Exception e) {
			
				Log.v("Resolution Command Error", e.toString());
			}
		}
	}
	
	// endRegion Image Methods
	
	//region Movement Methods
	
	public void stopMovement(){
		new Thread( new StopWheelsRunnable()).start();
	}
	
	public void MoveFoward(){
		
		WriteCmd(5, null);
		WriteCmd(7, null);
	}
	
	public void MoveLeftFoward(){
		
		WriteCmd(7, null);	

	}
	
	public void MoveRightFoward(){
		WriteCmd(5, null);
	}
	
	public void MoveBackward(){
		
		WriteCmd(6, null);	
		WriteCmd(8, null);
	}
	
	public void MoveLeftBackward(){
	
		WriteCmd(8, null);
	}
	
	public void MoveRightBackward(){
		WriteCmd(6, null);	
	}
		
    public void RotateLeft(){
    	MoveLeftFoward();
    	MoveRightBackward();
	}
    
	public void RotateRight(){
		
		MoveRightFoward();
		MoveLeftBackward();

	}
	
	// endRegion Movements Methods
	
	//region Command Write	
	
	private void WriteCmd(int index, byte[] extra_input) {
		int len = 0;
		switch (index) {
		case 1:
			len = 23;
			break;
		case 2:
			len = 49;
			break;
		case 3:
			len = 24;
			break;
		case 4:
			len = 27;
			break;
		case 5:
			len = 25;
			break; // cmd for the wheels
		case 6:
			len = 25;
			break;
		case 7:
			len = 25;
			break;
		case 8:
			len = 25;
			break;
		case 9:
			len = 23;
			break;
		case 10:
			len = 24;
			break;
		case 11:
			len = 24;
			break;
		}
		byte[] buffer = new byte[len];
		for (int i = 4; i < len; i++)
			buffer[i] = '\0';
		buffer[0] = 'M';
		buffer[1] = 'O';
		buffer[2] = '_';
		buffer[3] = 'O';
		if (index == 4) {
			buffer[3] = 'V';
		}

		switch (index) {
		case 1:
			break;
		case 2:
			buffer[4] = 0x02;
			buffer[15] = 0x1a;
			buffer[23] = 'A';
			buffer[24] = 'C';
			buffer[25] = '1';
			buffer[26] = '3';
			buffer[36] = 'A';
			buffer[37] = 'C';
			buffer[38] = '1';
			buffer[39] = '3';
			break;
		case 3:
			buffer[4] = 0x04;
			buffer[15] = 0x01;
			buffer[19] = 0x01;
			buffer[23] = 0x02;
			break;
		case 4:
			buffer[15] = 0x04;
			buffer[19] = 0x04;
			for (int i = 0; i < 4; i++)
				buffer[i + 23] = extra_input[i];
			break;
		case 5: // left Wheel Foward
			buffer[4] = (byte) 0xfa;
			buffer[15] = 0x02;
			buffer[19] = 0x01;
			buffer[23] = 0x04;
			buffer[24] = (byte) 0x0a;
			break;
		case 6: // Left Wheel Backward
			buffer[4] = (byte) 0xfa;
			buffer[15] = 0x02;
			buffer[19] = 0x01;
			buffer[23] = 0x05;
			buffer[24] = (byte) 0x0a;
			break;
		case 7: // Right wheel Foward
			buffer[4] = (byte) 0xfa;
			buffer[15] = 0x02;
			buffer[19] = 0x01;
			buffer[23] = 0x01;
			buffer[24] = (byte) 0x0a;
			break;
		case 8: // Right Wheel Backward
			buffer[4] = (byte) 0xfa;
			buffer[15] = 0x02;
			buffer[19] = 0x01;
			buffer[23] = 0x02;
			buffer[24] = (byte) 0x0a;
			break;
		case 9: // IR off(?)
			buffer[4] = (byte) 0xff;
			break;
		case 10:
			buffer[4] = (byte) 0x0e;
			buffer[15] = 0x01;
			buffer[19] = 0x01;
			buffer[23] = (byte)0x5e;
			break;
		case 11:
			buffer[4] = (byte) 0x0e;
			buffer[15] = 0x01;
			buffer[19] = 0x01;
			buffer[23] = (byte)0x5f;
			break;

		}
		
		if (index != 4) {
			try {
				cSock.getOutputStream().write(buffer, 0, len);
				cmdBuffer = buffer;
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}	
		} 
		else {
			
			try {
				vSock.getOutputStream().write(buffer, 0, len);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
	}
	
	private class StopWheelsRunnable implements Runnable{

		public void run() {
			try {
				
				cmdBuffer[23] = 0x02;
				cmdBuffer[24] = 0;
				cSock.getOutputStream().write(cmdBuffer, 0, cmdBuffer.length);	
				
				cmdBuffer[23] = 0x04;
				cmdBuffer[24] = 0;	
				cSock.getOutputStream().write(cmdBuffer, 0, cmdBuffer.length);	
				
			} catch (Exception e) {
				// TODO: handle exception
			}
			
		}	
	}
	
	//endRegion Command Write
	
	//region Parameters
	
	public String getParameter_Id() {
		return PARAMETERS.id;
	}
	public String getParameterSys_ver() {
		return PARAMETERS.sys_ver;
	}
	public String getParameter_App_ver() {
		return PARAMETERS.app_ver;
	}
	public String getParameter_Alias() {
		return PARAMETERS.alias;
	}
	public String getParameter_Adhoc_ssid() {
		return PARAMETERS.adhoc_ssid;
	}
	public String getParameter_Username() {
		return PARAMETERS.username;
	}
	public String getParameter_Userpwd() {
		return PARAMETERS.userpwd;
	}
	public String getParameter_Resolution() {
		return PARAMETERS.resolution;
	}
	public String getParameter_Ip() {
		return PARAMETERS.ip;
	}
	public String getParameter_Mask() {
		return PARAMETERS.mask;
	}
	public String getParameter_Gateway() {
		return PARAMETERS.gateway;
	}
	public String getParameter_Port() {
		return PARAMETERS.port;
	}
	public String getParameter_Wifi_ssid() {
		return PARAMETERS.wifi_ssid;
	}
	public String getParameter_Wifi_encrypt() {
		return PARAMETERS.wifi_encrypt;
	}
	public String getParameter_Wifi_defkey() {
		return PARAMETERS.wifi_defkey;
	}
	public String geParameter_Wifi_key1() {
		return PARAMETERS.wifi_key1;
	}
	public String getParameter_Wifi_key2() {
		return PARAMETERS.wifi_key2;
	}
	public String getParameter_Wifi_key3() {
		return PARAMETERS.wifi_key3;
	}
	public String getParameter_Wifi_key4() {
		return PARAMETERS.wifi_key4;
	}
	public String getParameter_Wifi_authtype() {
		return PARAMETERS.wifi_authtype;
	}
	public String getParameter_Wifi_keyformat() {
		return PARAMETERS.wifi_keyformat;
	}
	public String getParameter_Wifi_key1_bits() {
		return PARAMETERS.wifi_key1_bits;
	}
	public String getParameter_Wifi_key2_bits() {
		return PARAMETERS.wifi_key2_bits;
	}
	public String getParameter_Wifi_key3_bits() {
		return PARAMETERS.wifi_key3_bits;
	}
	public String getParameter_Wifi_key4_bits() {
		return PARAMETERS.wifi_key4_bits;
	}
	public String getParameter_Wifi_wpa_psk() {
		return PARAMETERS.wifi_wpa_psk;
	}	
	
	public String getParameters_List(){
		String list = 
				
		" id = " + PARAMETERS.id +
		"\n sys_ver = " + PARAMETERS.sys_ver +
		"\n app_ver = " + PARAMETERS.app_ver +
		"\n alias = " + PARAMETERS.alias + 
		"\n adhoc_ssid = " + PARAMETERS.adhoc_ssid + 
		"\n username = " + PARAMETERS.username + 
		"\n userpwd = " + PARAMETERS.userpwd + 
		"\n resolution = " + PARAMETERS.resolution + 
		"\n ip = " + PARAMETERS.ip + 
		"\n mask = " + PARAMETERS.mask + 
		"\n gateway = " + PARAMETERS.gateway + 
		"\n port = " + PARAMETERS.port + 
		"\n wifi_ssid = " + PARAMETERS.wifi_ssid + 
		"\n wifi_encrypt = " + PARAMETERS.wifi_encrypt + 
		"\n wifi_defkey = " + PARAMETERS.wifi_defkey + 
		"\n wifi_key1 = " + PARAMETERS. wifi_key1 + 
		"\n wifi_key2 = " + PARAMETERS. wifi_key2 + 
		"\n wifi_key3 = " + PARAMETERS. wifi_key3 +
		"\n wifi_key4 = " + PARAMETERS. wifi_key4 +
		"\n wifi_authtype = " + PARAMETERS. wifi_authtype +
		"\n wifi_keyformat = " + PARAMETERS. wifi_keyformat +
		"\n wifi_key1_bits = " + PARAMETERS. wifi_key1_bits +
		"\n wifi_key2_bits = " + PARAMETERS. wifi_key2_bits + 
		"\n wifi_key3_bits = " + PARAMETERS. wifi_key3_bits +
		"\n wifi_key3_bits = " + PARAMETERS. wifi_key4_bits +
		"\n wifi_wpa_psk = " + PARAMETERS. wifi_wpa_psk;
		
		return list;
	}
	
	private void fillParameters(ArrayList<String>parameters){	
		
		PARAMETERS.id = parameters.get(0);
		PARAMETERS.sys_ver = parameters.get(1);
		PARAMETERS.app_ver= parameters.get(2);
		PARAMETERS.alias= parameters.get(3);
		PARAMETERS.adhoc_ssid= parameters.get(4);
		PARAMETERS.username= parameters.get(5);
		PARAMETERS.userpwd= parameters.get(6);
		PARAMETERS.resolution= parameters.get(7);
		PARAMETERS.ip= parameters.get(8);
		PARAMETERS.mask= parameters.get(9);
		PARAMETERS.gateway= parameters.get(10);
		PARAMETERS.port= parameters.get(11);
		PARAMETERS.wifi_ssid= parameters.get(12);
		PARAMETERS.wifi_encrypt= parameters.get(13);
		PARAMETERS.wifi_defkey= parameters.get(14);
		PARAMETERS. wifi_key1= parameters.get(15);
		PARAMETERS. wifi_key2= parameters.get(16);
		PARAMETERS. wifi_key3= parameters.get(17);
		PARAMETERS. wifi_key4= parameters.get(18);
		PARAMETERS. wifi_authtype= parameters.get(19);
		PARAMETERS. wifi_keyformat= parameters.get(20);
		PARAMETERS. wifi_key1_bits= parameters.get(21);
		PARAMETERS. wifi_key2_bits= parameters.get(22);
		PARAMETERS. wifi_key3_bits= parameters.get(23);
		PARAMETERS. wifi_key4_bits= parameters.get(24);
		PARAMETERS. wifi_wpa_psk= parameters.get(25);
		
	}
	
	private class GetParametersThread implements Runnable {
		
		public void run() {
			try {	
				
				 ArrayList<String> params = new ArrayList<String>();
				 
		         HttpClient mClient= new DefaultHttpClient();
		         HttpGet get = new HttpGet("http://192.168.1.100/get_params.cgi" );
		         get.addHeader(BasicScheme.authenticate(new UsernamePasswordCredentials("AC13", "AC13"),"UTF-8", false));
		         
		         mClient.execute(get);
		         HttpResponse response = mClient.execute(get);
		        
		      
		         BufferedReader rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
				 String line = "";
				 
				 while ((line = rd.readLine()) != null) 
					 params.add((line.substring(line.indexOf("=")+1,line.indexOf(";"))).replace("'", ""));
					
				fillParameters(params);
				
				//Log.v("Parameters",getParametersList());
				
			} catch (Exception e) {
			
				Log.v("GET PARAMETERS ERROR", e.toString());
			}
		}
	}
	
	//endRegion Parameters
	
}
