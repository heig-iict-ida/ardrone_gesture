/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package madsdf.ardrone;

import static com.google.common.base.Preconditions.*;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Queues;
import com.google.common.eventbus.EventBus;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedQueue;
import madsdf.ardrone.controller.templates.TimeseriesChartPanel;

/**
 * Handles communication with a drone
 */
public class DroneClient {
    // Port to send command to drone
    static final int AT_PORT = 5556;
    // ARDrone constant
    static final int COM_MAX_LENGTH = 1024;
    // Default time between two commands
    static final int CMD_INTERVAL = 30;
    // End line char
    static final String LF = "\r";
    // ARDrone listening port
    static final int NAVDATA_PORT = 5554;
    static final int VIDEO_PORT = 5555;
    // NavDataReader offset
    static final int NAVDATA_STATE = 4;
    static final int NAVDATA_BATTERY = 24;
    static final int NAVDATA_ALTITUDE = 40;
    // AT*REF values
    static final int AT_REF_TAKEOFF = 1 << 9;
    static final int AT_REF_LANDING = 0;
    static final int AT_REF_EMERGENCY = 1 << 8;
    static final int AT_REF_RESET = 0;
    
    // Events type broadcasted by the droneclient
    public static class PCMDEvent {
        public final int flag;
        public final float roll;
        public final float pitch;
        public final float gas;
        public final float yaw;
        public PCMDEvent(int flag, float roll, float pitch, float gas, float yaw) {
            this.flag = flag; this.roll = roll;
            this.pitch = pitch; this.gas = gas;
            this.yaw = yaw;
        }
    }
    
    
    
    private final Timer timer = new Timer();
    ConcurrentLinkedQueue<String> comList = Queues.newConcurrentLinkedQueue();
    // Socket to send command to the drone
    private DatagramSocket atSocket;
    private InetAddress droneAddress;
    
    private int videoChannel = 0;
    
    // Command sequence number
    // Send AT command with sequence number 1 will reset the counter
    private int seq = 1;
    
    // Program state
    private boolean exit = false;
    private boolean navDataBootStrap = false;
    private boolean emergency = false;
    
    // Drone state
    private FlyingState droneState = FlyingState.LANDED;
    
    private VideoReader videoReader;
    private NavDataReader navDataReader;
    
    private final EventBus ebus;
    
    public DroneClient(EventBus ebus) {
        this.ebus = ebus;
    }
    
    
    private void sendCommand() {
        // Send more than one command in one packet, but less than the max length of a packet
        String cmd = "";
        while (comList.size() > 0 && cmd.length() + comList.peek().length() < COM_MAX_LENGTH) {

            // Take the first command of the list
            cmd += comList.poll();
        }

        try {
            // Verify there is a command to send
            if (cmd.length() > 0) {

                // Create the packet
                byte[] buf = cmd.getBytes();
                DatagramPacket sendPacket = new DatagramPacket(buf, buf.length, droneAddress, AT_PORT);

                // And send it
                atSocket.send(sendPacket);
            }
        } catch (IOException ex) {
            System.err.println("ARDrone.run: " + ex);
        }
    }
    
    public void connect() throws IOException {
        droneAddress = InetAddress.getByName(DroneConfig.get().getString("ip"));
        
        // Open send socket
        atSocket = new DatagramSocket(AT_PORT);
                
        timer.scheduleAtFixedRate(new TimerTask() {
            public void run() {
                sendCommand();
            }
        }, CMD_INTERVAL, CMD_INTERVAL);
        
        sendConfig();
    }
    
    public void disconnect() {
        atSocket.close();
        videoReader.disconnect();
        navDataReader.disconnect();
    }
    
    /**
     * Send an AT command to the drone, always use this method to send a
     * command.
     *
     * @param cmd the AT command to send without the final "LF"
     */
    public void sendATCmd(String cmd) {
        // Add the command to the end of the list of commands to send
        comList.offer(cmd + LF);
    }
    
    /**
     * @return the command sequence number and increase it
     */
    synchronized int incrSeq() {
        return seq++;
    }
    
    public InetAddress getDroneAddress() {
        return droneAddress;
    }
    
    /**
     * @return the application exit state
     */
    public boolean isExit() {
        return exit;
    }

    /**
     * @return the drone flying state
     */
    public FlyingState getFlyingState() {
        return droneState;
    }

    /**
     * @param flyingState set the new flying state
     */
    void setFlyingState(FlyingState flyingState) {
        this.droneState = flyingState;
    }

    /**
     * @param emergency the emergency to set
     * @return the emergency field value
     */
    public boolean setEmergency(boolean emergency) {
        return this.emergency = emergency;
    }
    
    
    /**
     * @param navDataBootStrap the navDataBootStrap to set
     * @return the navDatabootStrap value
     */
    public boolean setNavDataBootStrap(boolean navDataBootStrap) {
        return this.navDataBootStrap = navDataBootStrap;
    }
    
    
    // WARNING: Don't call this method when drone is flying, will cause crash
    public void resetEmergency() {
        checkState(emergency,
                   "Only call resetEmergency when drone is in emergency");
        
        // Reset emergency state
        sendATCmd("AT*REF=" + incrSeq() + "," + AT_REF_RESET);
        sendATCmd("AT*REF=" + incrSeq() + "," + AT_REF_EMERGENCY);

        // Reset the command watchdog just in case
        sendATCmd("AT*COMWDG=" + incrSeq());
    }
    
    
    /**
     * Change the video chanel
     *
     * @param chanel the new chanel to switch to
     */
    private void changeVideoChannel(int chanel) {
        System.out.println("Video channel : " + chanel);
        sendATCmd("AT*CONFIG=" + incrSeq() + ",\"video:video_channel\",\"" + chanel + "\"");
    }
    
    public void nextVideoChannel() {
        videoChannel = (videoChannel + 1) % 4;
        changeVideoChannel(videoChannel);
    }
    
    /**
     * Make the drone take off if the condition are filled
     * Returns a boolean indicating if the command was sent (true) or
     * canceled (false)
     */
    public boolean takeOff() {
        // If the drone is already flying cancel the command        
        if (getFlyingState() == FlyingState.FLYING ||
            getFlyingState() == FlyingState.LANDING ||
            navDataBootStrap ||
            emergency) {
            // If the drone is in emergency state, reset the flag
            if (emergency) {
                sendATCmd("AT*REF=" + incrSeq() + "," + AT_REF_RESET);
                sendATCmd("AT*REF=" + incrSeq() + "," + AT_REF_EMERGENCY);
            }
            return false;
        } // Otherwise send it
        else {
            sendATCmd("AT*REF=" + incrSeq() + "," + AT_REF_TAKEOFF);
            System.out.println("Takeoff");
            return true;
        }
    }

    /**
     * Make the drone land
     */
    public boolean land() {
        // If the drone is already landed cancel the status
        if (getFlyingState() == FlyingState.LANDED) {
            return false;
        }

        // But keep sending the command, we never know...
        sendATCmd("AT*REF=" + incrSeq() + "," + AT_REF_LANDING);
        System.out.println("Landing");
        return true;
    }

   
    /**
     * Configure the drone and start the control sender, the navigation data
     * thread, the video reader thread and the control sender thread.
     */
    private void sendConfig() {
        // Initialisation of the drone
        /*sendATCmd("AT*PMODE=" + getSeq() + ",2");
        sendATCmd("AT*MISC=" + getSeq() + ",2,20,2000,3000");*/

        //resetEmergency();

        // Set the altitude max
        sendATCmd("AT*CONFIG=" + incrSeq() + ",\"control:altitude_max\",\""
                + DroneConfig.get().getString("altitude_max") + "\"");

        // Set the altitude min
        sendATCmd("AT*CONFIG=" + incrSeq() + ",\"control:altitude_min\",\""
                + DroneConfig.get().getString("altitude_min") + "\"");

        // Set the control level (0 = no combined yaw mode)
        sendATCmd("AT*CONFIG=" + incrSeq() + ",\"control:control_level\",\"0\"");

        // Tell the drone we are indoor/outdoor
        sendATCmd("AT*CONFIG=" + incrSeq() + ",\"control:outdoor\",\"FALSE\"");

        // Tell the drone it has the shell or not
        sendATCmd("AT*CONFIG=" + incrSeq() + ",\"control:flight_without_shell\",\"FALSE\"");

        // Set the drone max angle
        sendATCmd("AT*CONFIG=" + incrSeq() + ",\"control:euler_angle_max\",\""
                + DroneConfig.get().getString("euler_max") + "\"");

        // Set the drone max up/down speed
        sendATCmd("AT*CONFIG=" + incrSeq() + ",\"control:control_vz_max\",\""
                + DroneConfig.get().getString("vz_max") + "\"");

        // Set the drone yaw speed
        sendATCmd("AT*CONFIG=" + incrSeq() + ",\"control:control_yaw\",\""
                + DroneConfig.get().getString("yaw_max") + "\"");

        // Set the navdata demo mode
        sendATCmd("AT*CONFIG=" + incrSeq() + ",\"general:navdata_demo\",\"TRUE\"");

        // Set the video stream
        sendATCmd("AT*CONFIG=" + incrSeq() + ",\"general:video_enable\",\"TRUE\"");

        // Set the video channel
        sendATCmd("AT*CONFIG=" + incrSeq() + ",\"video:video_channel\",\"" + videoChannel + "\"");

        // Set the ultra sound frequence
        //sendATCmd("AT*CONFIG=" + getSeq() + ",\"pic:ultrasound_freq\",\"8\"");

        // Tell the drone it is laying horizontally
        sendATCmd("AT*FTRIM=" + incrSeq());

        // Reset emergency state
        //sendATCmd("AT*REF=" + getSeq() + "," + AT_REF_RESET);

        // Send an hovering command
        sendPCMD(0, 0, 0, 0, 0);

        // Reset emergency state
        //sendATCmd("AT*REF=" + getSeq() + "," + AT_REF_RESET);

        // Reset emergency state
        //sendATCmd("AT*REF=" + getSeq() + "," + AT_REF_RESET);

        // Wait a little before starting all thread
        try {
            Thread.sleep(CMD_INTERVAL * 10);
        } catch (InterruptedException ex) {
            System.err.println("ARDrone.startConfig: " + ex);
        }

        // Launch the navadata thread
        navDataReader = new NavDataReader(this, ebus);
        navDataReader.start();

        // Launch the video reader
        videoReader = new VideoReader(this, ebus);
        videoReader.start();
    }
    
    // TODO: Expose an API with two different call : hover() and move()
    // which then call PCMD with correct parameters
    
    /**
     * Send a movement command to the drone, always use this method to send a
     * movement command. This method use the sendATCmd() method.
     *
     * @param flag 0 for hovering, 1 to make the next parameter useful
     * @param roll the roll degree
     * @param pitch the pitch degree
     * @param gas the gas command (up & down)
     * @param yaw the yaw speed (rotation left & right)
     */
    public void sendPCMD(int flag, float roll, float pitch, float gas, float yaw) {
        sendATCmd("AT*PCMD=" + incrSeq() + ","
                + flag + ","
                + Float.floatToIntBits(roll) + ","
                + Float.floatToIntBits(pitch) + ","
                + Float.floatToIntBits(gas) + ","
                + Float.floatToIntBits(yaw));
        ebus.post(new PCMDEvent(flag, roll, pitch, gas, yaw));
    }

}
