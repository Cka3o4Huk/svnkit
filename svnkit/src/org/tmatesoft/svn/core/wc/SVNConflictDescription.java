/*
 * ====================================================================
 * Copyright (c) 2004-2008 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.wc;

import org.tmatesoft.svn.core.SVNNodeKind;


/**
 * The <b>SVNConflictDescription</b>
 * 
 * @version 1.2.0
 * @author  TMate Software Ltd.
 * @since   1.2.0
 */
public class SVNConflictDescription {
    private SVNMergeFileSet myMergeFiles;
    private SVNNodeKind myNodeKind;
    private String myPropertyName;
    private boolean myIsPropertyConflict;
    private SVNConflictAction myConflictAction;
    private SVNConflictReason myConflictReason;
    
    public SVNConflictDescription(SVNMergeFileSet mergeFiles, SVNNodeKind nodeKind, String propertyName, 
            boolean isPropertyConflict, SVNConflictAction conflictAction, SVNConflictReason conflictReason) {
        myMergeFiles = mergeFiles;
        myNodeKind = nodeKind;
        myPropertyName = propertyName;
        myIsPropertyConflict = isPropertyConflict;
        myConflictAction = conflictAction;
        myConflictReason = conflictReason;
    }

    public SVNMergeFileSet getMergeFiles() {
        return myMergeFiles;
    }
    
    public SVNConflictAction getConflictAction() {
        return myConflictAction;
    }
    
    public SVNConflictReason getConflictReason() {
        return myConflictReason;
    }
    
    public boolean isPropertyConflict() {
        return myIsPropertyConflict;
    }
    
    public SVNNodeKind getNodeKind() {
        return myNodeKind;
    }
    
    public String getPropertyName() {
        return myPropertyName;
    }

}
