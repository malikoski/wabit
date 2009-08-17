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

package ca.sqlpower.wabit.swingui;

import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceAdapter;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JTree;
import javax.swing.tree.TreePath;

import org.apache.log4j.Logger;

import ca.sqlpower.sql.DataSourceCollection;
import ca.sqlpower.sql.JDBCDataSource;
import ca.sqlpower.sql.Olap4jDataSource;
import ca.sqlpower.sql.SPDataSource;
import ca.sqlpower.swingui.MultiDragTreeUI;
import ca.sqlpower.swingui.SPSwingWorker;
import ca.sqlpower.swingui.SwingUIUserPrompterFactory;
import ca.sqlpower.swingui.db.DatabaseConnectionManager;
import ca.sqlpower.swingui.db.DefaultDataSourceDialogFactory;
import ca.sqlpower.swingui.db.DefaultDataSourceTypeDialogFactory;
import ca.sqlpower.swingui.event.SessionLifecycleListener;
import ca.sqlpower.util.UserPrompter;
import ca.sqlpower.util.UserPrompterFactory;
import ca.sqlpower.util.UserPrompter.UserPromptOptions;
import ca.sqlpower.util.UserPrompter.UserPromptResponse;
import ca.sqlpower.util.UserPrompterFactory.UserPromptType;
import ca.sqlpower.wabit.WabitChildEvent;
import ca.sqlpower.wabit.WabitChildListener;
import ca.sqlpower.wabit.WabitObject;
import ca.sqlpower.wabit.WabitSession;
import ca.sqlpower.wabit.WabitUtils;
import ca.sqlpower.wabit.WabitWorkspace;
import ca.sqlpower.wabit.dao.WorkspaceXMLDAO;
import ca.sqlpower.wabit.swingui.tree.WabitObjectTransferable;
import ca.sqlpower.wabit.swingui.tree.WorkspaceTreeCellEditor;
import ca.sqlpower.wabit.swingui.tree.WorkspaceTreeCellRenderer;
import ca.sqlpower.wabit.swingui.tree.WorkspaceTreeModel;
import ca.sqlpower.wabit.swingui.tree.WorkspaceTreeModel.FolderNode;


/**
 * The Main Window for the Wabit Application.
 */
public class WabitSwingSessionImpl implements WabitSwingSession {
    
    private static final Icon DB_ICON = new ImageIcon(WabitSwingSessionImpl.class.getClassLoader().getResource("icons/dataSources-db.png"));
	
	private static final Logger logger = Logger.getLogger(WabitSwingSessionImpl.class);
	
	private final WabitSwingSessionContext sessionContext;
	
	private final JTree workspaceTree;
	
    /**
     * The cell renderer for this session's workspace tree.
     */
    private final WorkspaceTreeCellRenderer renderer = new WorkspaceTreeCellRenderer();

	private final List<SessionLifecycleListener<WabitSession>> lifecycleListeners =
		new ArrayList<SessionLifecycleListener<WabitSession>>();

	/**
	 * This DB connection manager will allow editing the db connections in the
	 * pl.ini file. This DB connection manager can be used anywhere needed in 
	 * wabit. 
	 */
	private final DatabaseConnectionManager dbConnectionManager;
	
	/**
	 * A {@link UserPrompterFactory} that will create a dialog for users to choose an existing
	 * DB or create a new one if they load a workspace with a DB not in their pl.ini.
	 */
	private final UserPrompterFactory upfMissingLoadedDB;
	
	/**
	 * All of the session specific operations should be delegated to this session.
	 * This class is mainly used to tie Swing objects to a core session.
	 */
	private final WabitSession delegateSession;
	
	/**
	 * Gets set to true whenever a change (child added/removed or property
	 * changed) is observed in any of the objects under (and including) the
	 * workspace.
	 */
	private boolean unsavedChangesExist = false;
	
    /**
     * Multipurpose event handler that watches every WabitObject in this
     * session's workspace for property changes, and handles them appropriately.
     */
	private class WorkspaceWatcher implements WabitChildListener, PropertyChangeListener {

        public WorkspaceWatcher(WabitWorkspace workspace) {
            WabitUtils.listenToHierarchy(workspace, this, this);
        }
	    
        public void wabitChildAdded(WabitChildEvent e) {
            WabitUtils.listenToHierarchy(e.getChild(), this, this);
            unsavedChangesExist = true;
        }

        public void wabitChildRemoved(WabitChildEvent e) {
            WabitUtils.unlistenToHierarchy(e.getChild(), this, this);
            unsavedChangesExist = true;
        }

        public void propertyChange(PropertyChangeEvent evt) {
            if ("running".equals(evt.getPropertyName())) {
                WabitObject src = (WabitObject) evt.getSource();
                Boolean isRunning = (Boolean) evt.getNewValue();
                if (isRunning) {
                    logger.info(evt.getSource() + " is running now");
                    renderer.updateTimer(src, 0); // TODO animate!
                    workspaceTree.repaint(workspaceTree.getPathBounds(workspaceTreeModel.createTreePathForObject(src)));
                } else {
                    logger.info(evt.getSource() + " has stopped running");
                    renderer.removeTimer(src);
                    workspaceTree.repaint(workspaceTree.getPathBounds(workspaceTreeModel.createTreePathForObject(src)));
                }
            } else {
                unsavedChangesExist = true;
            }
        }
	    
	}

	/**
	 * The model behind the workspace tree on the left side of Wabit.
	 */
	private final WorkspaceTreeModel workspaceTreeModel;
	
    /**
     * This is the most recent URI this session was saved to or the URI
     * this session was loaded from if it has not been saved. This will
     * be null for new sessions that have not been saved.
     */
    private URI currentURI = null;
    
    /**
     * The list of all currently-registered background tasks.
     */
    private final List<SPSwingWorker> activeWorkers =
        Collections.synchronizedList(new ArrayList<SPSwingWorker>());
	
    /**
     * This listener is attached to the active session's workspace and will
     * update the current editor displayed when the object being edited changes
     * in the workspace.
     */
    private final PropertyChangeListener workspaceEditorModelListener = new PropertyChangeListener() {
        public void propertyChange(PropertyChangeEvent evt) {
            if (sessionContext.isLoading()) return;
            if (evt.getPropertyName().equals("editorPanelModel")) {
                sessionContext.setActiveSession(WabitSwingSessionImpl.this);
                if (!sessionContext.setEditorPanel()) {
                    getWorkspace().setEditorPanelModel((WabitObject) evt.getOldValue());
                    return;
                }
                if (evt.getNewValue() != null) {
                    final TreePath createTreePathForObject = 
                        getWorkspaceTreeModel().createTreePathForObject((WabitObject) evt.getNewValue());
                    logger.debug("Tree path being set to " + createTreePathForObject 
                            + " as editor panel being set to " + ((WabitObject) evt.getNewValue()).getName());
                    getTree().setSelectionPath(createTreePathForObject);
                }
            }
        }
    };
    
    /**
     * This listener will listen for the context to finish loading and update
     * the selected element in the tree to be the same as the current editor.
     * This is done to select the correct object in the tree as it cannot be
     * done during loading. Doing this on sessions that are already loaded when
     * other sessions are loading should not hurt anything.
     */
    private final PropertyChangeListener loadingContextListener = new PropertyChangeListener() {
        public void propertyChange(PropertyChangeEvent evt) {
            if (evt.getPropertyName().equals("loading") && !((Boolean) evt.getNewValue())
                    && getWorkspace().getEditorPanelModel() != null) {
                final TreePath createTreePathForObject = 
                    getWorkspaceTreeModel().createTreePathForObject(getWorkspace().getEditorPanelModel());
                getTree().setSelectionPath(createTreePathForObject);
            }
        };
    };

    /**
     * Creates a new session that belongs to the given context and delegates
     * some of its work to the given delegate session.
     * <p>
     * To create an instance of this class, use
     * {@link WabitSwingSessionContextImpl#createSession()}.
     * 
     * @param context The context this session belongs to.
     * @param delegateSession The session to delegate some WabitSession operations to.
     */
	WabitSwingSessionImpl(WabitSwingSessionContext context, WabitSession delegateSession) {
	    this.delegateSession = delegateSession;
		sessionContext = context;
		
		new WorkspaceWatcher(delegateSession.getWorkspace());
		
		workspaceTreeModel = new WorkspaceTreeModel(delegateSession.getWorkspace());
		workspaceTree = new JTree(workspaceTreeModel);
		workspaceTree.setRootVisible(false);
		workspaceTree.setToggleClickCount(0);

        dbConnectionManager = createDbConnectionManager();
        
        upfMissingLoadedDB = new SwingUIUserPrompterFactory(sessionContext.getFrame());

        workspaceTree.setUI(new MultiDragTreeUI());
//		workspaceTree.updateUI(); //this seems to make the tree look nice on linux, don't know why but not for lack of trying
		workspaceTree.setShowsRootHandles(true);
		DragSource ds = new DragSource();
        ds.createDefaultDragGestureRecognizer(workspaceTree, DnDConstants.ACTION_COPY, new DragGestureListener(){

			public void dragGestureRecognized(DragGestureEvent dge) {
	            dge.getSourceAsDragGestureRecognizer().setSourceActions(DnDConstants.ACTION_COPY);
	            JTree t = (JTree) dge.getComponent();
	            List<WabitObject> wabitObjectsToExport = new ArrayList<WabitObject>();
	            for (TreePath path : t.getSelectionPaths()) {
	            	Object lastPathComponent = path.getLastPathComponent();
					if (lastPathComponent instanceof FolderNode) continue;
					wabitObjectsToExport.add((WabitObject) lastPathComponent);
	            	
	            }
	            
				if (wabitObjectsToExport.size() == 0) return;
				
				ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
				WorkspaceXMLDAO dao = new WorkspaceXMLDAO(byteOut, sessionContext);
				dao.save(wabitObjectsToExport);
				try {
					byteOut.flush();
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
				
	            dge.getDragSource().startDrag(dge, null, 
	                    new WabitObjectTransferable(byteOut), 
	                    new DragSourceAdapter() {//just need a default adapter
	                    }
	            );
			}
        	
        });

        workspaceTree.setCellRenderer(renderer);
        workspaceTree.setCellEditor(new WorkspaceTreeCellEditor(workspaceTree, renderer));
        workspaceTree.addMouseListener(new WorkspaceTreeListener(this));
        workspaceTree.setEditable(true);
        
        //Sets the editor panel if it is not set when creating the UI 
        //components. This may need to move elsewhere.
        if (getWorkspace().getEditorPanelModel() == null) {
        	getWorkspace().setEditorPanelModel(getWorkspace());
        }
        getWorkspace().addPropertyChangeListener(workspaceEditorModelListener);
        getContext().addPropertyChangeListener(loadingContextListener);
	}

	/**
	 * Constructor subroutine that builds the DB connection manager for this session.
	 */
    private DatabaseConnectionManager createDbConnectionManager() {
        List<Class<? extends SPDataSource>> newDSTypes = new ArrayList<Class<? extends SPDataSource>>();
        newDSTypes.add(JDBCDataSource.class);
        newDSTypes.add(Olap4jDataSource.class);
        DatabaseConnectionManager dbcm = new DatabaseConnectionManager(getDataSources(), 
                new DefaultDataSourceDialogFactory(), 
                new DefaultDataSourceTypeDialogFactory(getDataSources()),
                new ArrayList<Action>(), new ArrayList<JComponent>(), sessionContext.getFrame(), false, newDSTypes);
        dbcm.setDbIcon(DB_ICON);
        return dbcm;
    }
	
    public DataSourceCollection<SPDataSource> getDataSources() {
        return delegateSession.getDataSources();
    }

    public JTree getTree() {
    	return workspaceTree;
    }

	public void addSessionLifecycleListener(SessionLifecycleListener<WabitSession> l) {
		lifecycleListeners.add(l);
	}

	public void removeSessionLifecycleListener(SessionLifecycleListener<WabitSession> l) {
		lifecycleListeners.remove(l);
	}
	
	public boolean close() {
	    if (!delegateSession.close()) {
	        return false;
	    }
	    getWorkspace().removePropertyChangeListener(workspaceEditorModelListener);
	    getContext().removePropertyChangeListener(loadingContextListener);
	    for (SPSwingWorker worker : activeWorkers) {
	        worker.kill();
	    }
	    return true;
	}

    public WabitWorkspace getWorkspace() {
        return delegateSession.getWorkspace();
    }
    
	public WabitSwingSessionContext getContext() {
		return sessionContext;
	}
	
    /**
     * Returns a {@link DatabaseConnectionManager} that allows modifying
     * the data source collection of the given session.
     */
    public DatabaseConnectionManager getDbConnectionManager() {
        return dbConnectionManager;
    }
	
	public UserPrompter createUserPrompter(String question, UserPromptType responseType, UserPromptOptions optionType, UserPromptResponse defaultResponseType, Object defaultResponse, String ...buttonNames) {
		return upfMissingLoadedDB.createUserPrompter(question, responseType, optionType, defaultResponseType, defaultResponse, buttonNames);
	}

	public void addPropertyChangeListener(PropertyChangeListener l) {
        delegateSession.addPropertyChangeListener(l);
	}

	public void removePropertyChangeListener(PropertyChangeListener l) {
	    delegateSession.removePropertyChangeListener(l);
	}
	
    public WorkspaceTreeModel getWorkspaceTreeModel() {
        return workspaceTreeModel;
    }


	public UserPrompter createDatabaseUserPrompter(String question,
			List<Class<? extends SPDataSource>> dsTypes,
			UserPromptOptions optionType,
			UserPromptResponse defaultResponseType, Object defaultResponse,
			DataSourceCollection<SPDataSource> dsCollection,
			String... buttonNames) {
		return upfMissingLoadedDB.createDatabaseUserPrompter(question, dsTypes, optionType, defaultResponseType,
				defaultResponse, dsCollection, buttonNames);
	}

	public URI getCurrentURI() {
	    return currentURI;
	}
	
	/**
	 * Updates the current URI this session's workspace was last loaded from or saved to.
	 * Also clears the unsaved changes state.
	 */
	public void setCurrentURI(URI uri) {
	    currentURI = uri;
	    unsavedChangesExist = false;
	}
	
	public File getCurrentURIAsFile() {
	    if (getCurrentURI() != null && "file".equals(getCurrentURI().getScheme())) {
	        return new File(getCurrentURI());
	    } else {
	        return null;
	    }
	}

	public boolean hasUnsavedChanges() {
        return unsavedChangesExist;
    }

    /* docs inherited from interface */
    public void registerSwingWorker(SPSwingWorker worker) {
        activeWorkers.add(worker);
    }

    /* docs inherited from interface */
    public void removeSwingWorker(SPSwingWorker worker) {
        activeWorkers.remove(worker);
    }
    
}