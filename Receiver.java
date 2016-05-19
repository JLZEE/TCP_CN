// Computer network homework 5 programming
// receiver part

import java.io.*;
import java.net.*;
import java.util.*;
import java.math.*;
import java.text.*;

public class Receiver {
	private String sender_IP;
	private String filePath = "rcv_file.txt";
	private String logFilePath = "log_file_recv.txt";
	private int rcvPort = 20000;
	private int sndPort = 21112;
	private int ackPort = 20001;
	private int pkt_size = 1;
	private int win_size = 1;
	private long startTime = 0, endTime = 0;
	private DatagramPacket atgmPkg = null;		// use to receive packet
	//private DatagramPacket atgmAck;		// use to send ACKs
	private DatagramSocket atgmRcv = null;
	ServerSocket srvtSkt = null;
	Socket senderSocket = null;
	DataOutputStream dos = null;
	DataInputStream dis = null;
	BufferedOutputStream bos = null;
	StringBuffer endReport_dupPkt = new StringBuffer();
	StringBuffer endReport_wrgCksm = new StringBuffer();
	StringBuffer endReport_dlyPkt = new StringBuffer();
	StringBuffer endReport_otfOrder = new StringBuffer();
	private File file;

	private boolean PACKET_LOST = false;			// emulate packet lost situation
	private int PACKET_LOST_RATE = 201;			// one of every PACKET_LOST_RATE packets would lost
	boolean SEE_STATUS = false;

	public Receiver(String filePath, String rcvPortStr, String sender_IP, 
		String ackPortStr, String logFilePath) {
		this.sender_IP = sender_IP;
		this.filePath = filePath;
		this.logFilePath = logFilePath;
		this.rcvPort = Integer.parseInt(rcvPortStr);
		//this.sndPort = this.rcvPort + 1112;
		this.ackPort = Integer.parseInt(ackPortStr);
	}

	private void receiveFile() {				// this is the main method
		//System.out.println("still in test");
		try {
			srvtSkt = new ServerSocket(ackPort);
			senderSocket = srvtSkt.accept();
			dos = new DataOutputStream(senderSocket.getOutputStream());
			dis = new DataInputStream(senderSocket.getInputStream());
			atgmRcv = new DatagramSocket(rcvPort);

			boolean is_connect = this.handShake();
			boolean is_transmit = false;
			boolean is_over = false;
			
			if (is_connect) {
				startTime = System.currentTimeMillis();
				is_transmit = this.fileTrans();
				endTime = System.currentTimeMillis();
			}

			if (is_transmit) {
				is_over = this.reportTrans();
			}

			//boolean is_over = this.disConnect();

			atgmRcv.close();
			srvtSkt.close();
			senderSocket.close();
			dos.close();
			dis.close();
		} catch (Exception e) {
			System.out.println(">>> Error num2");
			//e.printStackTrace();
		} finally {
			try {
				srvtSkt.close();
				senderSocket.close();
				dos.close();
				dis.close();
			} catch (Exception e) {
				System.out.println(">>> Error num3");
				//e.printStackTrace();
			}
		}		
	}

	public void sendTCPmsg(String message) {	// use TCP to send message
		try {
			dos.writeUTF(message);
			dos.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public String recvTCPmsg() {				// use TCP to recevie message
		String message = null;
		try {
			message = dis.readUTF();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return message;
	}

	private boolean handShake() {
		// UDP connection
		boolean check = false;
		try {
			//atgmRcv = new DatagramSocket(rcvPort);
			byte[] buf = new byte[50];
			atgmPkg = new DatagramPacket(buf, buf.length);
			while (true) {
				atgmRcv.receive(atgmPkg);
				String rcvMsg = new String(atgmPkg.getData(), 0, atgmPkg.getLength());
				if (rcvMsg.startsWith("connection_request")) {
					this.sendTCPmsg("connection_allowed");
					pkt_size = Integer.parseInt(rcvMsg.split(":")[1]);				// with 20 Bytes header
					win_size = Integer.parseInt(rcvMsg.split(":")[2]);
					System.out.println("> Connect with sender success. Window * Packet_size= " + (win_size * pkt_size));
					check = true;
					break;
				}
			}
			//atgmRcv.close();		
		} catch (Exception e) {
			System.out.println("> Connection failed!");
		} 

		return check;
		
		// TCP connection
		/*
		String rcvMsg = this.recvTCPmsg();
		if (rcvMsg.startsWith("connection_request")) {
			this.sendTCPmsg("connection_allowed");
			pkt_size = Integer.parseInt(rcvMsg.split(":")[1]);
			System.out.println("> Connect with sender success. Packet size: " + pkt_size);			
			return true;
		} else {
			return false;
		}
		*/
	}

	private int toInt(byte[] byteArray) {
		int intOut = 0;
		byte bLoop;

		for (int i = 0; i < byteArray.length; i++) {
			bLoop = byteArray[i];
			intOut += (bLoop & 0xFF) << (8 * i);
		}
		return intOut;
	}

	private int checkSumData(byte[] buf) {
		int checkSum=0, startPt, addToSum;						// and store it in a byte[2] array
		if (buf.length%2 == 1 && buf.length>0) {
			checkSum = buf[0];
			startPt = 1;
		} else {
			checkSum = 0;
			startPt = 0;
		}
		//System.out.println("Check start: " + Integer.toBinaryString(checkSum));
		for (int i=startPt; i<buf.length-1; i+=2) {
			addToSum = (buf[i] << 8) + buf[i+1];
			checkSum += addToSum;
			while ((checkSum>>16) != 0) {
				checkSum = (checkSum & 65535) + 1;
			}
			//System.out.println("Check process: " + Integer.toBinaryString(checkSum));
		}	
		return checkSum;
	}

	private void recordToFile(String msg) {
		long timeNow = System.currentTimeMillis();
        SimpleDateFormat fm = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
        StringBuffer date = new StringBuffer(fm.format(timeNow));
        date.append("-->");
        date.append(msg);
        date.append("\n");
        try {
			FileWriter fileWriter = new FileWriter(logFilePath, true);
			fileWriter.write(date.toString());
			fileWriter.flush();
			fileWriter.close();
		} catch (IOException e) {             
			e.printStackTrace();
			System.out.println("Record trans information error, log_file path wrong");
		}
	}

	private boolean fileTrans() {
		boolean check = false;
		int interruptPkt = 0;
		int pktCount = 0;
		try {
			//atgmRcv = new DatagramSocket(rcvPort);
			bos = new BufferedOutputStream(new FileOutputStream(new File(filePath)));
			byte[] buf = new byte[pkt_size + 20];			// extra 20 bytes are for headers
			byte[] dataHeader = new byte[20];
			byte[] headerCount = new byte[4];
			byte[] checkSumByte = new byte[2];
			byte[] dataByteArr;
			byte[] dataLen = new byte[2];
			int countNum = 0, checkSumNum = 0;
			String recordStr = "";
			atgmPkg = new DatagramPacket(buf, buf.length);
			while (true) {				
				atgmRcv.receive(atgmPkg);
				
				// the code below is used to interrupt UDP connection
				interruptPkt++;
				if (PACKET_LOST && interruptPkt%PACKET_LOST_RATE == 0)
					continue;
				// interrupt end
				
				if (new String(atgmPkg.getData(), 0, atgmPkg.getLength()).equals("transmission_over")) {
					System.out.println(">> Receive file finish");
					sendTCPmsg("Get end packet, FIN");
					check = true;
					break;
				}
				
				
				// get the header of packet				
				System.arraycopy(atgmPkg.getData(), 0, dataHeader, 0, 20);
				////// check the header, 
				////// 1. if dup packet, send dup ACK, but do not write data to file
				////// 2. if not dup packet, check checksum, if not correct continue
				////// 3. if correct, send ACK, wirte data to file
				System.arraycopy(dataHeader, 4, headerCount, 0, 4);
				System.arraycopy(dataHeader, 14, checkSumByte, 0, 2);
				System.arraycopy(dataHeader, 12, dataLen, 0, 2);
				dataByteArr = Arrays.copyOfRange(atgmPkg.getData(), 20, atgmPkg.getLength());

				countNum = toInt(headerCount);
				if (countNum < pktCount && countNum >= (pktCount - win_size)) {	 // condition 1.
					if (SEE_STATUS)
						System.out.println(">> head count: " + countNum + " ACK num: " + pktCount + " Send dupACKs");
					sendTCPmsg("Get packet, ACK:" + countNum);
					recordStr = (ackPort + " " + rcvPort + " SeqsNum:" + countNum +
						" ACK:" + pktCount + " dupPackets");
					recordToFile(recordStr);
					endReport_dupPkt.append(countNum + " ");
					continue;
				}
				if (countNum < (pktCount - win_size)) {							// receive delay packet
					if (SEE_STATUS)
						System.out.println(">> head count: " + countNum + " ACK num: " + pktCount + " delayPacket");
					recordStr = (ackPort + " " + rcvPort + " SeqsNum:" + countNum +
						" ACK:" + pktCount + " delayPacket");
					recordToFile(recordStr);
					endReport_dlyPkt.append(countNum + " ");
					continue;
				}
				if (countNum > pktCount) {
					if (SEE_STATUS)
						System.out.println(">> head count: " + countNum + " ACK num: " + pktCount + " outOfOrderPacket");
					recordStr = (ackPort + " " + rcvPort + " SeqsNum:" + countNum +
						" ACK:" + pktCount + " outOfOrderPacket");
					recordToFile(recordStr);
					endReport_otfOrder.append(countNum + " ");
					continue;
				}

				checkSumNum = toInt(checkSumByte);
				if (checkSumNum != checkSumData(dataByteArr)) {				// condition 2.
					if (SEE_STATUS)
						System.out.println(">> head count: " + countNum + " ACK num: " + pktCount + " Wrong checkSum, ignore packet");
					/*		// use for check sum monitor
					System.out.println(Integer.toBinaryString(countNum) + " " + Integer.toBinaryString(checkSumData(dataByteArr)));
					System.out.println("Packet Size: " + atgmPkg.getLength() + " header data len: " + toInt(dataLen) 
						+ " read dataLen: " + dataByteArr.length);
					System.out.println("Header checksum is: " + checkSumNum + " data real checksum is: " + checkSumData(dataByteArr));
					for (int j=0; j<dataByteArr.length; j++) {
						System.out.print(dataByteArr[j] + " ");
					}
					System.out.println(" ");
					for (int j=0; j<checkSumByte.length; j++) {
						System.out.print(checkSumByte[j] + " ");
					}*/
					recordStr = (ackPort + " " + rcvPort + " SeqsNum:" + countNum +
						" ACK:" + pktCount + " wrongCheckSum");
					recordToFile(recordStr);
					endReport_wrgCksm.append(countNum + " ");
					continue;
				}															// condition 3.
				if (SEE_STATUS)
					System.out.println(">> head count: " + countNum + " ACK num: " + pktCount);
				sendTCPmsg("Get packet, ACK:" + pktCount);				
				recordStr = (ackPort + " " + rcvPort + " SeqsNum:" + countNum +
					" ACK:" + pktCount + " rightPacket");
				recordToFile(recordStr);

				bos.write(atgmPkg.getData(), 20, atgmPkg.getLength()-20);
				bos.flush();
				pktCount++;
			}
			bos.close();
			//atgmRcv.close();
		} catch (IOException e) {
			//e.printStackTrace();
			System.out.println("Error fileread/write");
		} catch (Exception e) {
			System.out.println("Error fileTrans");
		} finally {
			try {
				bos.close();
				//atgmRcv.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return check;
	}

	public boolean reportTrans() {
		System.out.println(">>> Report:");
		System.out.println(">>> Totally use time: " + (endTime - startTime) + " ms");
		System.out.println(">>> Duplicate sent segments number: " + endReport_dupPkt.toString());
		System.out.println(">>> Wrong checksum segments number: " + endReport_wrgCksm.toString());
		System.out.println(">>> Delay sent segments numeber: " + endReport_dlyPkt.toString());
		System.out.println(">>> Out of order segments number: " + endReport_otfOrder.toString());
		return true;
	}

	public static void main(String args[]) {
		
		Receiver fileRcvr = null;
		if (args.length != 5) {
			System.out.println(">>> Invalid input!");
			System.out.println(">>> Receiver <filename> <listening_port> <sender_IP> <sender_port> <log_filename>");
			System.exit(0);
		}	// check input parameters
		try {
			fileRcvr = new Receiver(args[0], args[1], args[2], args[3], args[4]);
		} catch (Exception e) {
			System.out.println(">>> Invalid input!");
			System.out.println(">>> Receiver <filename> <listening_port> <sender_IP> <sender_port> <log_filename>");
			System.exit(0);			
		}	// check input again

		try {
			System.out.println(">>> Start receving file ...");
			fileRcvr.receiveFile();
			System.out.println(">>> Receive file end");			
		} catch (Exception e) {
			System.out.println(">>> Receive file failed, please try again!");
			//e.printStackTrace();
		}
	}
}
