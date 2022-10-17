package org.asf.rats;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.InetAddress;

/**
 * 
 * ConnectiveHTTP Server Factory, creates instances of the HTTP server.<br />
 * <b>Supports</b> module implementations for building the server. (enabled by
 * default)
 * 
 * @author Stefan0436 - AerialWorks Software Foundation
 *
 */
public class ConnectiveServerFactory {

	private static ConnectiveServerFactory defaultFactory;

	/**
	 * Retrieves the default (shared) factory.<br />
	 * <b>NOTE:</b> the default factory is always locked.
	 * 
	 * @return Default ConnectiveServerFactory instance.
	 */
	public static ConnectiveServerFactory getDefault() {
		if (defaultFactory == null) {
			defaultFactory = new ConnectiveServerFactory();
			defaultFactory.locked = true;
		}

		return defaultFactory;
	}

	private int port = -1;
	private InetAddress ip = null;

	private boolean locked = false;
	private int options = 0;
	private Class<? extends ConnectiveHTTPServer> implementation = null;

	private boolean hasOption(int opt) {
		return (opt & options) == opt;
	}

	/**
	 * Disables usage of module implementations.
	 */
	public static final int OPTION_DISABLE_MODULE_IMPLEMENTATIONS = 0x1 << 1;

	/**
	 * Automatically starts the server on build.
	 */
	public static final int OPTION_AUTOSTART = 0x1 << 2;

	/**
	 * Assigns a port to the server, needed to use setPort.
	 */
	public static final int OPTION_ASSIGN_PORT = 0x1 << 3;

	/**
	 * Assigns an ip to the server, needed to use setIp.
	 */
	public static final int OPTION_ASSIGN_IP = 0x1 << 4;

	/**
	 * Sets building options
	 * 
	 * @param option Options to set.
	 * @throws IllegalStateException If the factory has been locked
	 */
	public ConnectiveServerFactory setOption(int option) {
		if (locked) {
			throw new IllegalStateException("The factory has been locked!");
		}
		options = options | option;
		return this;
	}

	/**
	 * Assigns the implementation class used to construct the service, requires a
	 * parameterless constructor.
	 * 
	 * @param implementation Implementation class
	 */
	public ConnectiveServerFactory setImplementation(Class<? extends ConnectiveHTTPServer> implementation) {
		this.implementation = implementation;
		return this;
	}

	/**
	 * Assigns the default port, OPTION_ASSIGN_PORT needs to be present for this.
	 * 
	 * @param port Default server port
	 */
	public ConnectiveServerFactory setPort(int port) {
		this.port = port;
		return this;
	}

	/**
	 * Assigns the default ip address, OPTION_ASSIGN_IP needs to be present for
	 * this.
	 * 
	 * @param ip Default server ip
	 */
	public ConnectiveServerFactory setIp(InetAddress ip) {
		this.ip = ip;
		return this;
	}

	/**
	 * Constructs the HTTP server. (locks the factory when building completes)
	 * 
	 * @return New ConnectiveHTTPServer instance.
	 * @throws InvocationTargetException If the server cannot be instanciated.
	 */
	public ConnectiveHTTPServer build() throws InvocationTargetException {
		if (implementation == null) {
			if (!hasOption(OPTION_DISABLE_MODULE_IMPLEMENTATIONS) && ConnectiveHTTPServer.implementation != null) {
				implementation = ConnectiveHTTPServer.implementation.getClass();
			} else {
				implementation = ConnectiveHTTPServer.class;
			}
		}

		ConnectiveHTTPServer server = null;

		try {
			Constructor<? extends ConnectiveHTTPServer> ctor = implementation.getDeclaredConstructor();
			ctor.setAccessible(true);
			server = ctor.newInstance();
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | NoSuchMethodException
				| SecurityException e) {
			throw new InvocationTargetException(e);
		}

		if (hasOption(OPTION_ASSIGN_PORT) && port != -1) {
			server.setPort(port);
		}

		if (hasOption(OPTION_ASSIGN_IP) && ip != null) {
			server.setIp(ip);
		}

		if (hasOption(OPTION_AUTOSTART)) {
			try {
				server.start();
			} catch (IOException e) {
				throw new RuntimeException("Server start failed!", e);
			}
		}

		locked = true;

		return server;
	}
}
