// Sender part

import java.io.*;
import java.net.*;
import java.util.*;
import java.math.*;
import java.text.*;

public class Sender {
	private String receiver_IP = null;
	//private InetAddress receiver_IP = null;
	private String filePath = "snd_file.txt";
	private String logFilePath = "log_file_send.txt";
	private int rcvPort = 20000;
	private int sndPort = 21112;
	private int ackPort = 20001;
	private int pkt_size = 256;
	private int win_size = 1;
	private long startTime = 0, endTime = 0;
	DatagramPacket atgmPkg = null;		// use to send packet
	DatagramPacket atgmPkg_error = null;
	DatagramPacket atgmPkg_send = null;
	DatagramSocket atgmSnd = null;		// UDP socket
	Socket sndrSkt = null;
	DataOutputStream dos = null;
	DataInputStream dis = null;
	BufferedInputStream bis = null;
	StringBuffer endReport_resndPkt = new StringBuffer();
	StringBuffer endReport_wrgAck = new StringBuffer();
	private File file;
	boolean SEE_STATUS = false;						// monitor the transmittion process

	private boolean CHECKSUM_INTERUPT = false;		// this parameter is used to emulate bit error in packet
	private boolean WIN_SIZE_ON = true;
	private int CHECKSUM_INTERUPT_RATE = 101;		// every one of CHECKSUM_INTERUPT_RATE packets can get wrong
	private int WAIT_TIME_OUT = 3000;				// set wait time, in ms

	private void setWin_size(int win_size) {		
		if (WIN_SIZE_ON){
			this.win_size = win_size;
		} else {
			this.pkt_size *= win_size;
		}
	}

	public Sender(String filePath, String receiver_IP, String rcvPortStr,
		String ackPortStr, String logFilePath) {
		this.filePath = filePath;
		this.receiver_IP = receiver_IP;
		this.rcvPort = Integer.parseInt(rcvPortStr);
		//this.sndPort = this.rcvPort + 1112;
		this.ackPort = Integer.parseInt(ackPortStr);
		this.logFilePath = logFilePath;
	}

	private void sendFile() {
		//System.out.println("Still in test");
		try {
			sndrSkt = new Socket(receiver_IP, ackPort);
			//sndrSkt = new Socket("127.0.0.1", ackPort);		// this is for test
			dos = new DataOutputStream(sndrSkt.getOutputStream());
			dis = new DataInputStream(sndrSkt.getInputStream());
			atgmSnd = new DatagramSocket(sndPort);

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

			if (is_over) {
				System.out.println(">>> File transfer end");
			}

			//boolean is_over = this.disConnect();

			atgmSnd.close();
			sndrSkt.close();
			dos.close();
			dis.close();

		} catch (UnknownHostException uknowHostE) {
			System.out.println(">>> Oops, <remote_IP> is not correct");
		} catch (IllegalArgumentException ilgAgmtE) {
			System.out.println(">>> Oops, <ack_port_num> is invalid");
		} catch (IOException e) {
			System.out.println(">>> Oops, something gets wrong, IP address/ACK port is not right");
			//e.printStackTrace();
		} catch (Exception e) {
			System.out.println(">>> Oops, send file failed, in sendFile()");
			//e.printStackTrace();
		} finally {
			try {
				sndrSkt.close();
				dos.close();
				dis.close();
			} catch (Exception e) {
				System.exit(0);
			}	
		}
	}

	private boolean handShake() {
		// Use UDP to connect with receiver
		boolean check = false;
		try {
			//atgmSnd = new DatagramSocket(sndPort);
			int handShakeCount = 0;			// count the hand shaking times, if > 3, give up connection
			System.out.println("> Trying to connect receiver...");
			String startMsg = ("connection_request:" + pkt_size + ":" + win_size);
			byte[] buf = startMsg.getBytes();
			//System.out.println("bufLength: " + buf.length);
			DatagramPacket startpkg = new DatagramPacket(buf, buf.length,
				 new InetSocketAddress(receiver_IP, rcvPort));
			sndrSkt.setSoTimeout(WAIT_TIME_OUT);
			while (handShakeCount < 3) {
				try {
					System.out.println("> Sending start packet");
					atgmSnd.send(startpkg);
					String ackMsg = dis.readUTF();	
					if (ackMsg.equals("connection_allowed"))
						check = true;				
					break;
				} catch (SocketTimeoutException e) {
					handShakeCount++;
					continue;
				}								
			}
			if (handShakeCount >= 3) {
				check = false;
				System.out.println("> Connection failed, no response from receiver, please check your port number");
			}
			//atgmSnd.close();
		} catch (Exception e) {
			System.out.println("> Connection failed");
		}
		
		return check;
		
		// Use TCP to connect with receiver
		/*		
		System.out.println("> Trying to connect receiver...");
		this.sendTCPmsg("connection_request:" + String.valueOf(pkt_size));
		String rcvMsg = this.recvTCPmsg();
		if (rcvMsg.equals("connection_allowed")) {
			System.out.println("> Connection success!");
			return true;
		} else {
			System.out.println("> Connection failed!");
			return false;
		}
		*/
	}

	private byte[] addHeader(byte[] buf, int count, int ackNum, int len) {
		byte[] bufWithHeader = new byte[buf.length + 20];
		try {
			byte[] header = new byte[20];
			/////// Now fill header ///////
			
			byte[] checkSumByteArr = checkSumBuf(buf, len);

			System.arraycopy(toByteArray(sndPort, 2), 0, header, 0, 2);		// First 2 Bytes, sender port
			System.arraycopy(toByteArray(rcvPort, 2), 0, header, 2, 2);		// Second 2 Bytes, receiver port
			System.arraycopy(toByteArray(count, 4), 0, header, 4, 4);		// Third 4 Bytes, sequence number
			System.arraycopy(toByteArray(ackNum, 4), 0, header, 8, 4);		// Forth 4 Bytes, ACK number
			System.arraycopy(toByteArray(len, 2), 0, header, 12, 2);	// Fifth 2 Bytes, window size
			System.arraycopy(checkSumByteArr, 0, header, 14, 2);			// Sixth 2 Bytes, checksum
			System.arraycopy(toByteArray(0, 4), 0, header, 16, 4);
			

			System.arraycopy(header, 0, bufWithHeader, 0, 20);
			System.arraycopy(buf, 0, bufWithHeader, 20, buf.length);
		} catch (Exception e) {
			System.out.println("Error in addHeader");
		}
		return bufWithHeader;
	}

	private byte[] toByteArray(int num, int arrayLen) {			// change a integer to a byte array
		byte[] byteArray = new byte[arrayLen];
		for (int i = 0; (i < 4) && (i < arrayLen); i++) {
			byteArray[i] = (byte) (num >> 8 * i & 0xFF);
		}
		return byteArray;
	}

	private byte[] checkSumBuf(byte[] buf, int len) {			// calculate the checksum of the input byte arrey
		int checkSum, startPt, addToSum;						// and store it in a byte[2] array
		if (len%2 == 1 && buf.length>0) {
			checkSum = buf[0];
			startPt = 1;
		} else {
			checkSum = 0;
			startPt = 0;
		}
		for (int i=startPt; i<len-1; i+=2) {
			addToSum = (buf[i] << 8) + buf[i+1];
			checkSum += addToSum;
			while ((checkSum>>16) != 0) {
				checkSum = (checkSum & 65535) + 1;
			}
			//System.out.println(Integer.toBinaryString(checkSum));
		}
		//System.out.println("checksum is: " + checkSum);
		return toByteArray(checkSum, 2);
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
		try {
			//atgmSnd = new DatagramSocket(sndPort);
			bis = new BufferedInputStream(new FileInputStream(new File(filePath)));
			List<byte[]> bufArrList = new ArrayList<byte[]>();
			List<byte[]> bufArrList_withHeader = new ArrayList<byte[]>();
			List<byte[]> bufArrList_withHeader_error = new ArrayList<byte[]>();
			List<DatagramPacket> atgmPkg_Arr = new ArrayList<DatagramPacket>();
			List<DatagramPacket> atgmPkg_errorArr = new ArrayList<DatagramPacket>();
			Set<Integer> ackMsgHashSet = new HashSet<Integer>();
			int[] lenArr = new int[win_size];
			for (int i=0; i<win_size; i++) {
				bufArrList.add(new byte[pkt_size]);
				bufArrList_withHeader.add(new byte[pkt_size + 20]);
				bufArrList_withHeader_error.add(new byte[pkt_size + 20]);
				atgmPkg_Arr.add(null);
				atgmPkg_errorArr.add(null);
			}
			int valid_bufRead = 0;
			boolean transInPorcess = true;

			byte[] buf = new byte[pkt_size];
			byte[] buf_withHeader_inArr = new byte[pkt_size + 20];
			byte[] bufWithHeader = new byte[pkt_size + 20];
			byte[] bufWithHeader_error = new byte[pkt_size + 20];
			//byte[] ackbuf = new byte[1024];
			int len, count_i, count = 0, totalSentPkt = 0, totalBytes = 0, ackNum = -1, checkSumIntrpt = 0, totalSgBytes = 0;
			String recordStr = "";
			if (WIN_SIZE_ON) {
				Loop1:while (transInPorcess) {
					valid_bufRead = win_size;
					totalSgBytes = 0;
					for (int i=0; i<win_size; i++) {

						lenArr[i] = bis.read(buf);
						bufArrList.set(i, Arrays.copyOf(buf, buf.length));				
						//lenArr[i] = bis.read(bufArrList.get(i));
						if (lenArr[i] == -1) {
							transInPorcess = false;
							valid_bufRead = i;
							break Loop1;
						}
						totalSgBytes += lenArr[i] + 20;
						buf_withHeader_inArr = addHeader(bufArrList.get(i), count+i, ackNum+i, lenArr[i]);
						bufArrList_withHeader.set(i, Arrays.copyOf(buf_withHeader_inArr, buf_withHeader_inArr.length));

						bufWithHeader_error = Arrays.copyOf(bufArrList_withHeader.get(i), bufArrList_withHeader.get(i).length);
						bufWithHeader_error[20] = (byte)0;
						bufArrList_withHeader_error.set(i, Arrays.copyOf(bufWithHeader_error, bufWithHeader_error.length));

						atgmPkg_Arr.set(i, new DatagramPacket(bufArrList_withHeader.get(i), 
							lenArr[i] + 20, new InetSocketAddress(receiver_IP, rcvPort)));
						atgmPkg_errorArr.set(i, new DatagramPacket(bufArrList_withHeader_error.get(i),
							lenArr[i] + 20, new InetSocketAddress(receiver_IP, rcvPort)));
					}
					/*		// use for debug
					for (int i=0; i<win_size; i++) {
						for (int j=0; j<bufArrList_withHeader.get(i).length; j++) {
							System.out.print(bufArrList_withHeader.get(i)[j] + " ");
						}
						System.out.println("__" + bufArrList_withHeader.get(i).length);
						for (int j=0; j<buf.length; j++) {
							System.out.print(buf[j] + " ");
						}
						System.out.println("__" + buf.length);
					}*/		// use for debug end

					sndrSkt.setSoTimeout(WAIT_TIME_OUT);
					SndWhile: while (true) {
						ackMsgHashSet.clear();
						totalBytes += totalSgBytes;

						// insert bit error

						try {
							for (int i=0; i<valid_bufRead; i++) {

								// insert checksum interrupt
								checkSumIntrpt++;
								if (CHECKSUM_INTERUPT && checkSumIntrpt%CHECKSUM_INTERUPT_RATE==0) {
									atgmPkg_send = atgmPkg_errorArr.get(i);
								} else {
									atgmPkg_send = atgmPkg_Arr.get(i);
								}
								// end insert

								count_i = count + i;
								if (SEE_STATUS)
									System.out.println(">> Sending num " + count_i + " segment");
								atgmSnd.send(atgmPkg_send);
								totalSentPkt++;
							}
							for (int i=0; i<valid_bufRead; i++) {
								String ackMsg = dis.readUTF();
								ackNum = Integer.parseInt(ackMsg.split(":")[1]);
								ackMsgHashSet.add(ackNum);
							}
							for (int i=0; i<valid_bufRead; i++) {
								count_i = count + i;
								if (ackMsgHashSet.contains(count_i)) {
									continue;
								} else {
									if (SEE_STATUS)
										System.out.println(">> Wrong ACK number, does not contain ACK number: " + count_i);
									recordStr = (ackPort + " " + rcvPort + " SeqsNum:" + count +
										" ACK:" + count_i + " lostACK");
									continue SndWhile;
								}
							}
							for (int i=0; i<valid_bufRead; i++) {
								count_i = count + i;
								if (SEE_STATUS)
									System.out.println(">> Receive ACK " + count_i);
								recordStr = (ackPort + " " + rcvPort + " SeqsNum:" + count_i +
									" ACK:" + count_i + " getACK");
								recordToFile(recordStr);		
							}
							break SndWhile;

						} catch (SocketTimeoutException e) {
							endReport_resndPkt.append(count + "~" + valid_bufRead + " ");
							recordStr = (ackPort + " " + rcvPort + " SeqsNum:" + count +
								" ACK:null" + " timeOut");
							recordToFile(recordStr);
							continue SndWhile;
						}
					}
					count = count + valid_bufRead;
				}
			} else {
				while ((len = bis.read(buf)) != -1) {
					//totalBytes += 20 + len;

					bufWithHeader = addHeader(buf, count, ackNum, len);
					bufWithHeader_error = Arrays.copyOf(bufWithHeader, bufWithHeader.length);
					bufWithHeader_error[20] = (byte)0;

					atgmPkg = new DatagramPacket(bufWithHeader, len + 20, new InetSocketAddress(receiver_IP, rcvPort));
					atgmPkg_error = new DatagramPacket(bufWithHeader_error, len + 20, new InetSocketAddress(receiver_IP, rcvPort));
					sndrSkt.setSoTimeout(WAIT_TIME_OUT);
					while (true) {
						totalBytes += 20 + len;
						// insert bit error
						checkSumIntrpt++;
						if (CHECKSUM_INTERUPT && checkSumIntrpt%CHECKSUM_INTERUPT_RATE==0) {
							atgmPkg_send = atgmPkg_error;
						} else {
							atgmPkg_send = atgmPkg;
						}
						// insert bit error end

						try {
							if (SEE_STATUS)
								System.out.println(">> Sending num " + count + " segment");
							atgmSnd.send(atgmPkg_send);
							totalSentPkt++;
							String ackMsg = dis.readUTF();
							ackNum = Integer.parseInt(ackMsg.split(":")[1]);

							if (ackNum != count) {		// receive wrong ACK, keep sending, until receive right one
								if (SEE_STATUS)
									System.out.println(">> Wrong ACK number: " + ackNum + " segment number: " + count);
								endReport_wrgAck.append(ackNum + " ");
								continue;
							}

							if (SEE_STATUS)
								System.out.println(">> Receive ACK " + ackNum);
							recordStr = (ackPort + " " + rcvPort + " SeqsNum:" + count +
								" ACK:" + ackNum + " getACK");
							recordToFile(recordStr);
							break;
						} catch (SocketTimeoutException e) {
							//for (int j=20; j<bufWithHeader.length; j++) {
							//	System.out.print(bufWithHeader[j] + " ");
							//}
							endReport_resndPkt.append(count + " ");
							recordStr = (ackPort + " " + rcvPort + " SeqsNum:" + count +
								" ACK:null" + " timeOut");
							recordToFile(recordStr);
							continue;
						}				
					}
					count++;
					/*			// use for debug
					for (int i=0; i<bufWithHeader.length; i++) {
						System.out.print(bufWithHeader[i] + " ");
					}
					break;*/	// use for debug end
				}
			}
			
			
			byte[] endbuf = "transmission_over".getBytes();
			DatagramPacket endpkg = new DatagramPacket(endbuf, endbuf.length,
			 new InetSocketAddress(receiver_IP, rcvPort));
			sndrSkt.setSoTimeout(WAIT_TIME_OUT);
			while (true) {
				try {
					System.out.println(">> Sending end packet");
					atgmSnd.send(endpkg);
					totalSentPkt++;					
					String ackMsg = dis.readUTF();
					break;
				} catch (SocketTimeoutException e) {
					continue;
				}				
			}
			count++;
			System.out.println(">> Totally sent " + totalSentPkt + " segments, " + 
				count + " of them arrived");
			float rate = (float)(totalSentPkt-count)/(float)totalSentPkt * 100f;
			System.out.println(">> Segment retransmit: " + rate + "%");
			System.out.println(">> Totally sent " + totalBytes + " Bytes");

			check = true;
			bis.close();
			//atgmSnd.close();
		} catch (IOException e) {
			System.out.println(">> Error in opening file:" + filePath);
			//e.printStackTrace();
		} catch (Exception e) {
			System.out.println("Error fileTrans");
		} finally {
			try {
				bis.close();
				//atgmSnd.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return check;
	}

	public boolean reportTrans() {				// use to report the transmission details
		System.out.println(">>> Report:");
		System.out.println(">>> Totally use time: " + (endTime - startTime) + " ms");
		System.out.println(">>> Resent segments number: " + endReport_resndPkt.toString());
		System.out.println(">>> Get wrong ACK number: " + endReport_wrgAck.toString());
		return true;
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

	public static void main(String[] args) {
		Sender fileSndr = null;
		if (args.length > 6 || args.length < 5) {
			System.out.println(">>> Invalid input!");
			System.out.println(">>> Sender <filename> <remote_IP> <remote_port>" + 
				" <ack_port_num> <log_filename> <optional:window_size>");
			System.exit(0);
		}
		try {
			fileSndr = new Sender(args[0], args[1], args[2], args[3], args[4]);
			if (args.length == 6) {
				fileSndr.setWin_size(Integer.parseInt(args[5]));
			}
		} catch (Exception e) {
			System.out.println(">>> Invalid input!");
			System.out.println(">>> Sender <filename> <remote_IP> <remote_port>" + 
				" <ack_port_num> <log_filename> <optional:window_size>");
			System.exit(0);
		}
		try {
			fileSndr.sendFile();
		} catch (Exception e) {
			System.out.println(">>> send file failed, please try again!");
			System.exit(0);
		}
	}
}
