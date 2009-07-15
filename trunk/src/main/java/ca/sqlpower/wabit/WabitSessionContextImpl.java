/*
 * Copyright (c) 2008, SQL Power Group Inc.
 *
 * This file is part of Wabit.
 *
 * Wabit is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * Wabit is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>. 
 */

package ca.sqlpower.wabit;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;

import org.apache.log4j.Logger;

import ca.sqlpower.architect.ArchitectUtils;
import ca.sqlpower.sql.DataSourceCollection;
import ca.sqlpower.sql.PlDotIni;
import ca.sqlpower.sql.SPDataSource;
import ca.sqlpower.sqlobject.SQLObjectException;
import ca.sqlpower.sqlobject.SQLObjectRuntimeException;
import ca.sqlpower.util.DefaultUserPrompter;
import ca.sqlpower.util.UserPrompter;
import ca.sqlpower.util.UserPrompter.UserPromptOptions;
import ca.sqlpower.util.UserPrompter.UserPromptResponse;
import ca.sqlpower.wabit.enterprise.client.WabitServerInfo;

/**
 * This is the canonical headless implementation of WabitSessionContext
 * interface. Other session context implementations that cover more specialized
 * use cases can either extend this one or delegate certain operations to it.
 */
public class WabitSessionContextImpl implements WabitSessionContext {
	
	private static final Logger logger = Logger.getLogger(WabitSessionContextImpl.class);

	/**
	 * This is a preference that stores the location of the pl.ini.
	 */
	private static final String PREFS_PL_INI_PATH = "PL_INI_PATH";
	
    protected final JmDNS jmdns;
    private final List<WabitServerInfo> manuallyConfiguredServers = new ArrayList<WabitServerInfo>();
    
	private DataSourceCollection<SPDataSource> dataSources;
	protected final List<WabitSession> childSessions = new ArrayList<WabitSession>();
	
	/**
	 * If this flag is true, this session context will halt the VM when its
	 * last session closes.
	 */
	protected boolean terminateWhenLastSessionCloses;
	
	/**
	 *  Stores true when the OS is MAC
	 */
    private static final boolean MAC_OS_X = (System.getProperty("os.name").toLowerCase().startsWith("mac os x"));

    /**
     * This is the path to the user's pl.ini file.
     */
    private String plDotIniPath;
    
    /**
     * This prefs node stores context specific prefs. At current this is the pl.ini location.
     */
    protected final Preferences prefs = Preferences.userNodeForPackage(WabitSessionContextImpl.class);
	
	/**
	 * Creates a new Wabit session context.
	 * 
	 * @param terminateWhenLastSessionCloses
	 *            If this flag is true, this session context will halt the VM
	 *            when its last session closes.
	 * @param useJmDNS
	 *            If this flag is true, then this session will create a JmDNS
	 *            instance for searching for Wabit servers.
	 * @throws IOException
	 *             If the startup configuration files can't be read
	 * @throws SQLObjectException
	 *             If the pl.ini is invalid.
	 */
	public WabitSessionContextImpl(boolean terminateWhenLastSessionCloses, boolean useJmDNS) throws IOException, SQLObjectException {
		this.terminateWhenLastSessionCloses = terminateWhenLastSessionCloses;
		if (useJmDNS) {
//			jmdns = JmDNS.create();
			//TODO reenable this.. taking it out for the release due to Bug 1905 in the bug database
			//It causes crashes on startup if no network interface can be found and likely will have
			//some terrible things happening when we try to close the connection. Therefore it is being 
			//taken out for releasing Wabit in 0.9.7.
			jmdns = null;
		} else {
			jmdns = null;
		}
		
        setPlDotIniPath(prefs.get(PREFS_PL_INI_PATH, null));
        logger.debug("pl.ini is at " + getPlDotIniPath());
        
        setPlDotIniPath(ArchitectUtils.checkForValidPlDotIni(getPlDotIniPath(), "Wabit"));
        
        try {
            manuallyConfiguredServers.addAll(readServersFromPrefs());
        } catch (BackingStoreException ex) {
            logger.error("Preferences unavailable! Not reading server infos from prefs.", ex);
        }
	}

	/**
	 * Adds the given Wabit session to the list of child sessions for this
	 * context. This is normally done by the sessions themselves, so you
	 * shouldn't need to call this method from your own code.
	 */
	public void registerChildSession(WabitSession child) {
		childSessions.add(child);
	}
	
	/**
     * Tries to read the plDotIni if it hasn't been done already.  If it can't be read,
     * returns null and leaves the plDotIni property as null as well. See {@link #plDotIni}.
     */
    public DataSourceCollection<SPDataSource> getDataSources() {
        String path = getPlDotIniPath();
        if (path == null) return null;
        
        if (dataSources == null) {
        	dataSources = new PlDotIni();
        	String iniToLoad = "ca/sqlpower/sql/default_database_types.ini";
            try {
                logger.debug("Reading PL.INI defaults");
                dataSources.read(getClass().getClassLoader().getResourceAsStream(iniToLoad));
                iniToLoad = "/ca/sqlpower/demodata/example_database.ini";
                dataSources.read(WabitSessionContextImpl.class.getResourceAsStream(iniToLoad));
            } catch (IOException e) {
                throw new SQLObjectRuntimeException(new SQLObjectException("Failed to read system resource " + iniToLoad,e));
            }
            try {
                if (dataSources != null) {
                    logger.debug("Reading new PL.INI instance");
                    dataSources.read(new File(path));
                }
            } catch (IOException e) {
                throw new SQLObjectRuntimeException(new SQLObjectException("Failed to read pl.ini at \""+getPlDotIniPath()+"\"", e));
            }
        }
        return dataSources;
    }

	/**
	 * Removes the given Wabit session from the list of child sessions for this
	 * context. This is normally done by the sessions themselves, so you
	 * shouldn't need to call this method from your own code.
	 */
	public void deregisterChildSession(WabitSession child) {
		childSessions.remove(child);
		
		logger.debug("Deregistered a child session " + childSessions.size() + " sessions still remain.");
		if (childSessions.isEmpty() && getDataSources() != null && getPlDotIniPath() != null) {
			logger.debug("Saving pl.ini");
	        prefs.put(PREFS_PL_INI_PATH, getPlDotIniPath());
			try {
				getDataSources().write(new File(getPlDotIniPath()));
	        } catch (IOException e) {
	            logger.error("Couldn't save PL.INI file!", e); //$NON-NLS-1$
	        }
		}
		
		if (terminateWhenLastSessionCloses && childSessions.isEmpty()) {
			System.exit(0);
		}
	}
	/**
	 * returns true if the OS is Mac
	 * @return
	 */
	public boolean isMacOSX() {
		return MAC_OS_X ; 
	}

	/**
	 * This does not create a session as there is no current core session implementation.
	 */
	public WabitSession createSession() {
		return new WabitSessionImpl(this);
	}

	protected void setPlDotIniPath(String plDotIniPath) {
		this.plDotIniPath = plDotIniPath;
	}

	private String getPlDotIniPath() {
		return plDotIniPath;
	}
	
	public int getSessionCount() {
		return childSessions.size();
	}

	public void close() {
		if (jmdns != null) {
			jmdns.close();
		}	
		for (int i = childSessions.size() - 1; i >= 0; i--) {
			if (!childSessions.get(i).close()) {
				return;
			}
		}
		System.exit(0);
	}
	
	/**
	 * Returns the JmDNS instance in this session context. Note that if this
	 * session context was initialzed not to use JmDNS, it will return null.
	 */
	public JmDNS getJmDNS() {
	    return jmdns;
	}
	
	public List<WabitServerInfo> getEnterpriseServers(boolean includeDiscovered) {
	    List<WabitServerInfo> servers = new ArrayList<WabitServerInfo>(manuallyConfiguredServers);
	    if (includeDiscovered && jmdns != null) {
	        for (ServiceInfo si : jmdns.list(WABIT_ENTERPRISE_SERVER_MDNS_TYPE)) {
	            servers.add(new WabitServerInfo(si));
	        }
	    }
	    return servers;
	}

	public void addServer(WabitServerInfo serverInfo) {
        manuallyConfiguredServers.add(serverInfo);
        Preferences servers = getServersPrefNode();
        Preferences thisServer = servers.node(serverInfo.getName());
        thisServer.put("name", serverInfo.getName());
        thisServer.put("serverAddress", serverInfo.getServerAddress());
        thisServer.putInt("port", serverInfo.getPort());
        thisServer.put("path", serverInfo.getPath());
    }

    public void removeServer(WabitServerInfo serverInfo) {
        manuallyConfiguredServers.remove(serverInfo);
        Preferences servers = getServersPrefNode();
        try {
            servers.node(serverInfo.getName()).removeNode();
        } catch (BackingStoreException ex) {
            throw new RuntimeException("Failed to remove server from list", ex);
        }
    }

    private List<WabitServerInfo> readServersFromPrefs() throws BackingStoreException {
        Preferences serversNode = getServersPrefNode();
        List<WabitServerInfo> serverList = new ArrayList<WabitServerInfo>();
        for (String nodeName : serversNode.childrenNames()) {
            Preferences serverNode = serversNode.node(nodeName);
            
            serverList.add(new WabitServerInfo(
                    serverNode.get("name", null),
                    serverNode.get("serverAddress", null),
                    serverNode.getInt("port", 0),
                    serverNode.get("path", null)
                    ));
        }
        return serverList;
    }
    
    /**
     * Returns the prefs node under which the manually-configured server infos
     * are stored.
     */
    private Preferences getServersPrefNode() {
        return prefs.node("servers");
    }

    /**
     * Returns the preferences node used by this session context. This should
     * not normally be used by client code; it is primarily intended for use by
     * alternative session and session context implementations.
     */
	public Preferences getPrefs() {
        return prefs;
    }

	public String getName() {
		return "Local";
	}

    /**
     * Returns a user prompter that always gives the default response, since
     * this is a headless session context and there is no user to ask.
     */
    public UserPrompter createUserPrompter(String question,
            UserPromptType responseType, UserPromptOptions optionType,
            UserPromptResponse defaultResponseType, Object defaultResponse,
            String... buttonNames) {
        return new DefaultUserPrompter(optionType, defaultResponseType, defaultResponse);
    }
}
