/*
 * Copyright (c) 2009, SQL Power Group Inc.
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

package ca.sqlpower.wabit.enterprise.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.log4j.Logger;

import ca.sqlpower.sql.DataSourceCollection;
import ca.sqlpower.sql.PlDotIni;
import ca.sqlpower.sql.SPDataSource;
import ca.sqlpower.sqlobject.SQLObjectException;
import ca.sqlpower.wabit.WabitWorkspace;
import ca.sqlpower.wabit.WabitSession;
import ca.sqlpower.wabit.WabitSessionContextImpl;
import ca.sqlpower.wabit.dao.WorkspaceXMLDAO;

/**
 * A special kind of session context that binds itself to a remote Wabit
 * Enterprise Server. Provides database connection information and file storage
 * capability based on the remote server.
 */
public class WabitServerSessionContext extends WabitSessionContextImpl {

    private static final Logger logger = Logger
            .getLogger(WabitServerSessionContext.class);
    
    private final HttpClient httpClient;
    private final WabitServerInfo serviceInfo;
    public static final HashMap<WabitServerInfo, WabitServerSessionContext> instances = new HashMap<WabitServerInfo, WabitServerSessionContext>();

    private WabitServerSessionContext(WabitServerInfo serviceInfo, boolean terminateWhenLastSessionCloses)
            throws IOException, SQLObjectException {
        super(terminateWhenLastSessionCloses, true);

        HttpParams params = new BasicHttpParams();
        HttpConnectionParams.setConnectionTimeout(params, 2000);
        httpClient = new DefaultHttpClient(params);

        this.serviceInfo = serviceInfo;
        if (serviceInfo == null) {
            logger.error("Null pointer Exception");
            throw new NullPointerException("serviceInfo is for the WabitServer is null");
        }
    }

    @Override
    public void close() {
        httpClient.getConnectionManager().shutdown();
        super.close();
    }
    
    /**
     * Retrieves the data source list from the server.
     * <p>
     * Future plans: In the future, the server will probably be a proxy for all
     * database operations, and we won't actually send the connection
     * information to the client. This has the advantage that it can work over
     * an HTTP firewall or proxy, where the present method would fail.
     */
    @Override
    public DataSourceCollection<SPDataSource> getDataSources() {
        ResponseHandler<DataSourceCollection<SPDataSource>> plIniHandler = new ResponseHandler<DataSourceCollection<SPDataSource>>() {
            public DataSourceCollection<SPDataSource> handleResponse(HttpResponse response)
                    throws ClientProtocolException, IOException {
                if (response.getStatusLine().getStatusCode() != 200) {
                    throw new IOException(
                            "Server error while reading data sources: " + response.getStatusLine());
                }
                PlDotIni plIni;
                try {
					plIni = new PlDotIni(getServerURI("/"));
	                plIni.read(response.getEntity().getContent());
                } catch (URISyntaxException e) {
                    throw new RuntimeException(e);
                }
                return plIni;
            }
        };
        try {
            return executeServerRequest("data-sources/", plIniHandler);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
    
    /**
     * List all the workspaces on this context's server.
     * 
     * @param serviceInfo
     * @return
     * @throws IOException
     * @throws URISyntaxException
     */
    public List<String> getWorkspaceNames() throws IOException, URISyntaxException {
        String responseBody = executeServerRequest("workspace", new BasicResponseHandler());
        logger.debug("Workspace list:\n" + responseBody);
        List<String> workspaces = Arrays.asList(responseBody.split("\r?\n"));
        return workspaces;
    }

    private <T> T executeServerRequest(String contextRelativePath, ResponseHandler<T> responseHandler)
    throws IOException, URISyntaxException {
        HttpUriRequest request = new HttpGet(getServerURI(contextRelativePath));
        return httpClient.execute(request, responseHandler);
    }
    
    private URI getServerURI(String contextRelativePath) throws URISyntaxException {
        logger.debug("Getting server URI for: " + serviceInfo);
        String contextPath = serviceInfo.getPath();
        return new URI("http", null, serviceInfo.getServerAddress(), serviceInfo.getPort(),
                contextPath + contextRelativePath, null, null);
    }

    public static WabitServerSessionContext getInstance(WabitServerInfo serviceInfo) throws IOException, SQLObjectException {
        WabitServerSessionContext context =  instances.get(serviceInfo);
        if (context == null) {
            context = new WabitServerSessionContext(serviceInfo, false);
            instances.put(serviceInfo, context);
        }
        return context;
    }

    /**
     * Saves the given workspace on this session context's server. The name to
     * save as is determined by the workspace's name.
     * 
     * @param workspace
     *            The workspace to save. Its name determines the name of the
     *            resource saved to the server. If there is already a workspace on
     *            the server with the same name, it will be replaced.
     * @throws IOException
     *             If the upload fails
     * @throws URISyntaxException
     *             If the workspace name can't be properly encoded in a URI
     */
    public void saveWorkspace(WabitWorkspace workspace) throws IOException, URISyntaxException {
        
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        WorkspaceXMLDAO dao = new WorkspaceXMLDAO(out, workspace);
        dao.save();
        out.close(); // has no effect, but feels sensible :)
        
        HttpPost request = new HttpPost(getServerURI("workspace/" + workspace.getName()));
        logger.debug("Posting workspace to " + request);
        request.setEntity(new ByteArrayEntity(out.toByteArray()));
        httpClient.execute(request);
        logger.debug("Post complete!");
    }
    
	/**
	 * Removes the given Wabit session from the list of child sessions for this
	 * context. This is normally done by the sessions themselves, so you
	 * shouldn't need to call this method from your own code.
	 */
	public void deregisterChildSession(WabitSession child) {
		childSessions.remove(child);
		
		logger.debug("Deregistered a child session " + childSessions.size() + " sessions still remain.");
		
		if (terminateWhenLastSessionCloses && childSessions.isEmpty()) {
			System.exit(0);
		}
	}
    
    @Override
    public String getName() {
    	return serviceInfo.getName();
    }
    
}