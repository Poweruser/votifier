/*
 * Copyright (C) 2012 Vex Software LLC
 * This file is part of Votifier.
 * 
 * Votifier is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Votifier is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Votifier.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.vexsoftware.votifier.net;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.logging.*;
import javax.crypto.BadPaddingException;
import org.bukkit.Bukkit;

import com.vexsoftware.votifier.Votifier;
import com.vexsoftware.votifier.crypto.RSA;
import com.vexsoftware.votifier.model.*;

/**
 * The vote receiving server.
 * 
 * @author Blake Beaupain
 * @author Kramer Campbell
 */
public class VoteReceiver extends Thread {

	/** The logger instance. */
	private static final Logger LOG = Logger.getLogger("Votifier");

	private final Votifier plugin;

	/** The host to listen on. */
	private final String host;

	/** The port to listen on. */
	private final int port;

	/** The server socket. */
	private ServerSocket server;

	/** The running flag. */
	private boolean running = true;

	/**
	 * Instantiates a new vote receiver.
	 * 
	 * @param host
	 *            The host to listen on
	 * @param port
	 *            The port to listen on
	 */
	public VoteReceiver(final Votifier plugin, String host, int port)
			throws Exception {
		this.plugin = plugin;
		this.host = host;
		this.port = port;

		initialize();
	}

	private void initialize() throws Exception {
		try {
			server = new ServerSocket();
			server.bind(new InetSocketAddress(host, port));
			server.setSoTimeout(10000);
			if(this.plugin.isDebug()) {
			    LOG.info("ServerSocket is set up. Listening on Address: " + server.getInetAddress().toString());
			}
		} catch (Exception ex) {
			LOG.log(Level.SEVERE,
					"Error initializing vote receiver. Please verify that the configured");
			LOG.log(Level.SEVERE,
					"IP address and port are not already in use. This is a common problem");
			LOG.log(Level.SEVERE,
					"with hosting services and, if so, you should check with your hosting provider.",
					ex);
			throw new Exception(ex);
		}
	}

	/**
	 * Shuts the vote receiver down cleanly.
	 */
	public void shutdown() {
		running = false;
		if (server == null)
			return;
		try {
			server.close();
		} catch (Exception ex) {
			LOG.log(Level.WARNING, "Unable to shut down vote receiver cleanly.", ex);
		}
	}

	@Override
	public void run() {
	    boolean firstRun = true;
	    if(this.plugin.isDebug() && !running) {
	        LOG.log(Level.WARNING, "VoteReceiver thread was shutdown before it was even started.");
	    }
	    // Main loop.
	    while (running) {
	        if(firstRun) {
	            firstRun = false;
	            if(this.plugin.isDebug()) {
	                LOG.log(Level.INFO, "VoteReceiver thread started. Votifier is now ready to receive incoming connections.");
	            }
	        }
	        try {
	            Socket socket = server.accept();
	            if(this.plugin.isDebug()) {
	                LOG.log(Level.INFO, "Accepting new incoming connection from: " + socket.getInetAddress().toString());
	            }
	            try {
	                socket.setSoTimeout(30000);
	                new VoteReceiverClientThread(this.plugin, socket).start();
	            } catch (IOException exception) {
	                LOG.log(Level.WARNING, "Error while setting up a new incoming client connection", exception);
	                LOG.log(Level.WARNING, exception.toString());
	                try {
	                    socket.close();
	                } catch (IOException e) {}
	            }
	        } catch (SocketTimeoutException e) {
	            // nothing to do, accept() just timed out, as there was no incoming connection
	        } catch (IOException e) {
	            if(running) {
	                LOG.log(Level.WARNING, "Error while waiting for a new incoming client connection", e);
	                LOG.log(Level.WARNING, e.toString());
	            }
	        }
	    }
	    if(this.plugin.isDebug()) {
	        LOG.log(Level.INFO, "VoteReceiver thread has stopped.");
	    }
	}
}
