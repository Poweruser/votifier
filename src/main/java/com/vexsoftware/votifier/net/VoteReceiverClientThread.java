package com.vexsoftware.votifier.net;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.SocketException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.crypto.BadPaddingException;

import org.bukkit.Bukkit;

import com.vexsoftware.votifier.Votifier;
import com.vexsoftware.votifier.crypto.RSA;
import com.vexsoftware.votifier.model.Vote;
import com.vexsoftware.votifier.model.VoteListener;
import com.vexsoftware.votifier.model.VotifierEvent;

public class VoteReceiverClientThread extends Thread {

    private static final Logger LOG = Logger.getLogger("Votifier");
    private Socket clientSocket;
    private Votifier plugin;
    private BufferedWriter writer;
    private InputStream in;
    
    public VoteReceiverClientThread(Votifier plugin, Socket clientSocket) throws IOException {
        this.plugin = plugin;
        this.clientSocket = clientSocket;
        this.setName("Votifier_ClientThread(" + this.clientSocket.getInetAddress().toString() + ")");
        writer = new BufferedWriter(new OutputStreamWriter(this.clientSocket.getOutputStream()));
        in = new BufferedInputStream(this.clientSocket.getInputStream());
    }

    /**
     * Reads a string from a block of data.
     * 
     * @param data
     *            The data to read from
     * @return The string
     */
    private String readString(byte[] data, int offset) {
        StringBuilder builder = new StringBuilder();
        for (int i = offset; i < data.length; i++) {
            if (data[i] == '\n')
                break; // Delimiter reached.
            builder.append((char) data[i]);
        }
        return builder.toString();
    }

    @Override
    public void run() {
        if(this.clientSocket != null && !this.clientSocket.isClosed() && writer != null && in != null) {
            try {
                // Send them our version.
                writer.write("VOTIFIER " + Votifier.getInstance().getVersion());
                writer.newLine();
                writer.flush();
        
                // Read the 256 byte block.
                byte[] block = new byte[256];
                in.read(block, 0, block.length);
        
                // Decrypt the block.
                block = RSA.decrypt(block, Votifier.getInstance().getKeyPair()
                        .getPrivate());
                int position = 0;
        
                // Perform the opcode check.
                String opcode = readString(block, position);
                position += opcode.length() + 1;
                if (!opcode.equals("VOTE")) {
                    // Something went wrong in RSA.
                    throw new Exception("Unable to decode RSA");
                }
        
                // Parse the block.
                String serviceName = readString(block, position);
                position += serviceName.length() + 1;
                String username = readString(block, position);
                position += username.length() + 1;
                String address = readString(block, position);
                position += address.length() + 1;
                String timeStamp = readString(block, position);
                position += timeStamp.length() + 1;
        
                // Create the vote.
                final Vote vote = new Vote();
                vote.setServiceName(serviceName);
                vote.setUsername(username);
                vote.setAddress(address);
                vote.setTimeStamp(timeStamp);
        
                if (this.plugin.isDebug())
                    LOG.info("Received vote record -> " + vote);
        
                // Dispatch the vote to all listeners.
                for (VoteListener listener : Votifier.getInstance().getListeners()) {
                    try {
                        listener.voteMade(vote);
                    } catch (Exception ex) {
                        String vlName = listener.getClass().getSimpleName();
                        LOG.log(Level.WARNING,
                                "Exception caught while sending the vote notification to the '" + vlName + "' listener", ex);
                    }
                }
        
                // Call event in a synchronized fashion to ensure that the
                // custom event runs in the
                // the main server thread, not this one.
                plugin.getServer().getScheduler()
                        .scheduleSyncDelayedTask(plugin, new Runnable() {
                            public void run() {
                                Bukkit.getServer().getPluginManager()
                                        .callEvent(new VotifierEvent(vote));
                            }
                        });
        
            } catch (BadPaddingException ex) {
                LOG.log(Level.WARNING, "Unable to decrypt vote record. Make sure that that your public key");
                LOG.log(Level.WARNING, "matches the one you gave the server list.", ex);
            } catch (Exception ex) {
                LOG.log(Level.WARNING, "Exception caught while receiving a vote notification", ex);
            }
        } else {
            LOG.log(Level.WARNING, "VoteReceiverClientThread has not been properly initialized. " + (this.clientSocket != null ? (" For Client: " + this.clientSocket.getInetAddress().toString()) : ""));
        }
        this.cleanup();
    }

    public void cleanup() {
        if(this.writer != null) {
            try {
                this.writer.close();
            } catch (IOException e) {}
        }
        if(this.in != null) {
            try {
                this.in.close();
            } catch (IOException e) {}
        }
        if(this.clientSocket != null) {
            try {
                this.clientSocket.close();
            } catch (IOException e) {}
        }
    }
}
