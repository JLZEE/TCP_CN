# TCP_CN
computer network homework 5
TCP transmission based on UDP

## Basic use
1. run Receiver.class first by typing:

$ java Receiver <filename> <listening_port> <sender_IP> <sender_port> <log_filename>

for example:

$ java Receiver rcv_file.txt 20000 127.0.0.1 20001 log_file_recv.txt

You can also transfer file rcv_file.png.

2. run Sender.class by typing:

$ java Sender <filename> <remote_IP> <remote_port> <ack_port_num> <log_filename> <optional:window_size>

for example:

$ java Sender snd_file.txt 127.0.0.1 20000 20001 log_file_send.txt 2

3. if you’re going to use newudpl emulator, remind that sender’s UDP port set as 21112.

4. please run Receiver before Sender, if something gets wrong, remember to reboot Receiver before new transmission.

## Extra features
*****
1. Handshake before file transfer. Real TCP always have 3 handshaking to insure reliable transmission. Thus handshaking can improve the reliability of my program. Sender will try at most 3 times to handshake with receiver, if is fails, sender will not try to send file. 

*****
2. More detailed report after transmission. At the end of transmission, there’ll be a more detailed report including total transmission time, duplicate sent segments’ number, wrong check sum segments’ number, out of order segments’ number…. Sender’s report is slightly different from receiver’s. You’ll see the detailed report after transmission.

*****
3. Two kinds of transmission mode. Instead of changing window size, another transmission mode is changing packet size(keep window size as 1). For example, if you use this mode and set window size to 5, the packet size will be 5 * 256 (256 is default packet size). Every time sender will send only 1 packet, but the packet size is larger.

To use is mode, change the code in Sender.java at line 32:
private boolean WIN_SIZE_ON = true;
to
private boolean WIN_SIZE_ON = false;
and compile again.

Then run the code.

*****
4. Embedded emulator in program. newudpl is very useful to emulate condition like packet lost, bit error and out of order packets. However, you need to install it on linux first. To make it easier, I add an emulator function in the program. Embedded emulator can make bit error and packet lost.

To use bit error emulator, change code in Sender.java at line 31
private boolean CHECKSUM_INTERUPT = false;
to
private boolean CHECKSUM_INTERUPT = true;

you can also decide the rate of bit error by changing code in Sender.java at line 33
private int CHECKSUM_INTERUPT_RATE = 101;
to
private int CHECKSUM_INTERUPT_RATE = “any number you want”;
and compile again.

To use packet lost emulator, change code in Receiver.java at line 34
private boolean PACKET_LOST = false;
to
private boolean PACKET_LOST = true;

you can also decide the rate of packet lost by changing code in Receiver.java at line 35
private int PACKET_LOST_RATE = 201;
to
private int PACKET_LOST_RATE = “any number you want”;
and compile again.

Then run the code.

*****
5. Transmission monitor. log_file can record every transmission message, however, sometime we’d like to see them real time. If I want to know what is the packet being sent now, which packet is lost, I can use this function. You can use this function to see realtime transmission process and debug the program more easily.

To use this function, change code in Sender.java at line 29
boolean SEE_STATUS = false;
to 
boolean SEE_STATUS = true;

and in Receiver.java at line 36
boolean SEE_STATUS = false;
to
boolean SEE_STATUS = true;
and compile again.

Then run the code.


Have fun
