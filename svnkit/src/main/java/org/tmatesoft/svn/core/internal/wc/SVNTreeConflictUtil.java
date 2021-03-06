/*
 * ====================================================================
 * Copyright (c) 2004-2012 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import org.tmatesoft.svn.core.internal.util.SVNSkel;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.SVNConflictAction;
import org.tmatesoft.svn.core.wc.SVNConflictReason;
import org.tmatesoft.svn.core.wc.SVNOperation;
import org.tmatesoft.svn.core.wc.SVNTreeConflictDescription;
import org.tmatesoft.svn.util.SVNLogType;

/**
 * @author  TMate Software Ltd.
 * @version 1.3
 */
public class SVNTreeConflictUtil {

    public static Map readTreeConflicts(File dirPath, String conflictData) throws SVNException {
        if (conflictData == null) {
            return new SVNHashMap();
        }

        byte[] data;
        try {
            data = conflictData.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            data = conflictData.getBytes();
        }
        return readTreeConflicts(dirPath, data);
    }

    public static Map readTreeConflicts(File dirPath, byte[] conflictData) throws SVNException {
        Map conflicts = new SVNHashMap();
        if (conflictData == null) {
            return conflicts;
        }
        SVNSkel skel = SVNSkel.parse(conflictData);
        if (skel == null || skel.isAtom()) {
            SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT, "Error parsing tree conflict skel");
            SVNErrorManager.error(error, SVNLogType.WC);
        }
        for (Iterator iterator = skel.getList().iterator(); iterator.hasNext();) {
            SVNSkel conflictSkel = (SVNSkel) iterator.next();
            SVNTreeConflictDescription conflict = readSingleTreeConflict(conflictSkel, dirPath);
            if (conflict != null) {
                conflicts.put(conflict.getPath(), conflict);
            }
        }
        return conflicts;
    }

    public static SVNTreeConflictDescription readSingleTreeConflict(SVNSkel skel, File dirPath) throws SVNException {
        if (!isValidConflict(skel)) {
            SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT, "Invalid conflict info in tree conflict description");
            SVNErrorManager.error(error, SVNLogType.WC);
        }
        if (skel.getChild(1).getData().length == 0) {
            SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT, "Empty \'victim\' field in tree conflict description");
            SVNErrorManager.error(error, SVNLogType.WC);
        }
        String victimBasename = skel.getChild(1).getValue();

        SVNNodeKind kind = getNodeKind(skel.getChild(2).getValue());
        if (kind != SVNNodeKind.FILE && kind != SVNNodeKind.DIR) {
            SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT, "Invalid \'node_kind\' field in tree conflict description");
            SVNErrorManager.error(error, SVNLogType.WC);
        }

        SVNOperation operation = getOperation(skel.getChild(3).getValue());
        SVNConflictAction action = getAction(skel.getChild(4).getValue());
        SVNConflictReason reason = getConflictReason(skel.getChild(5).getValue());
        SVNConflictVersion srcLeftVersion = readConflictVersion(skel.getChild(6));
        SVNConflictVersion srcRightVersion = readConflictVersion(skel.getChild(7));

        return new SVNTreeConflictDescription(new File(dirPath, victimBasename), kind, action, reason, operation, srcLeftVersion, srcRightVersion);
    }

    private static SVNConflictVersion readConflictVersion(SVNSkel skel) throws SVNException {
        if (!isValidVersionInfo(skel)) {
            SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT, "Invalid version info in tree conflict description");
            SVNErrorManager.error(error, SVNLogType.WC);
        }
        String repoURLString = skel.getChild(1).getValue();
        SVNURL repoURL = repoURLString.length() == 0 ? null : SVNURL.parseURIEncoded(repoURLString);
        if (repoURL == null) {
            return null;
        }
        long pegRevision = Long.parseLong(skel.getChild(2).getValue());
        String path = skel.getChild(3).getValue();
        path = path.length() == 0 ? null : path;
        SVNNodeKind kind = getNodeKind(skel.getChild(4).getValue());
        return new SVNConflictVersion(repoURL, path, pegRevision, kind);
    }

    private static boolean isValidVersionInfo(SVNSkel skel) throws SVNException {
        if (skel.getListSize() != 5 || !skel.getChild(0).contentEquals("version")) {
            return false;
        }
        return skel.containsAtomsOnly();
    }

    private static boolean isValidConflict(SVNSkel skel) throws SVNException {
        if (skel == null) {
            return false;
        }
        if (skel.getListSize() != 8 || !skel.getChild(0).contentEquals("conflict")) {
            return false;
        }
        for (int i = 1; i < 6; i++) {
            SVNSkel element = skel.getChild(i);
            if (!element.isAtom()) {
                return false;
            }
        }
        return isValidVersionInfo(skel.getChild(6)) && isValidVersionInfo(skel.getChild(7));
    }

    public static String getTreeConflictData(Map conflicts) throws SVNException {
        if (conflicts == null) {
            return null;
        }
        byte[] rawData = getTreeConflictRawData(conflicts);
        String conflictData;
        try {
            conflictData = new String(rawData, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            conflictData = new String(rawData);
        }
        return conflictData;
    }

    public static byte[] getTreeConflictRawData(Map conflicts) throws SVNException {
        if (conflicts == null) {
            return null;
        }
        SVNConflictVersion nullVersion = new SVNConflictVersion(null, null, SVNRepository.INVALID_REVISION, SVNNodeKind.UNKNOWN);
        SVNSkel skel = SVNSkel.createEmptyList();
        for (Iterator iterator = conflicts.values().iterator(); iterator.hasNext();) {
            SVNTreeConflictDescription conflict = (SVNTreeConflictDescription) iterator.next();
            SVNSkel conflictSkel = getConflictSkel(nullVersion, conflict);
            if (!isValidConflict(conflictSkel)) {
                SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT,
                        "Failed to create valid conflict description skel: ''{0}''", skel.toString());
                SVNErrorManager.error(error, SVNLogType.WC);
            }
            skel.prepend(conflictSkel);
        }
        return skel.unparse();
    }

    public static String getSingleTreeConflictData(SVNTreeConflictDescription conflict) throws SVNException {
        if (conflict == null) {
            return null;
        }
        byte[] rawData = getSingleTreeConflictRawData(conflict);
        String conflictData;
        try {
            conflictData = new String(rawData, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            conflictData = new String(rawData);
        }
        return conflictData;
    }

    public static byte[] getSingleTreeConflictRawData(SVNTreeConflictDescription conflict) throws SVNException {
        SVNConflictVersion nullVersion = new SVNConflictVersion(null, null, SVNRepository.INVALID_REVISION, SVNNodeKind.UNKNOWN);
        SVNSkel conflictSkel = getConflictSkel(nullVersion, conflict);
        if (!isValidConflict(conflictSkel)) {
            SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT,
                    "Failed to create valid conflict description skel: ''{0}''", conflictSkel.toString());
            SVNErrorManager.error(error, SVNLogType.WC);
        }
        return conflictSkel.unparse();
    }


    public static SVNSkel getConflictSkel(SVNConflictVersion nullVersion, SVNTreeConflictDescription conflict) throws SVNException {
        SVNSkel conflictSkel = SVNSkel.createEmptyList();

        SVNConflictVersion sourceRightVersion = conflict.getSourceRightVersion();
        sourceRightVersion = sourceRightVersion == null ? nullVersion : sourceRightVersion;
        prependVersionInfo(conflictSkel, sourceRightVersion);

        SVNConflictVersion sourceLeftVersion = conflict.getSourceLeftVersion();
        sourceLeftVersion = sourceLeftVersion == null ? nullVersion : sourceLeftVersion;
        prependVersionInfo(conflictSkel, sourceLeftVersion);

        conflictSkel.prepend(SVNSkel.createAtom(conflict.getConflictReason().toString()));
        conflictSkel.prepend(SVNSkel.createAtom(conflict.getConflictAction().toString()));
        conflictSkel.prepend(SVNSkel.createAtom(conflict.getOperation().toString()));

        if (conflict.getNodeKind() != SVNNodeKind.DIR && conflict.getNodeKind() != SVNNodeKind.FILE) {
            SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT,
                    "Invalid \'node_kind\' field in tree conflict description");
            SVNErrorManager.error(error, SVNLogType.WC);
        }
        conflictSkel.prepend(SVNSkel.createAtom(getNodeKindString(conflict.getNodeKind())));

        String path = conflict.getPath().getName();
        if (path.length() == 0) {
            SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT,
                    "Empty path basename in tree conflict description");
            SVNErrorManager.error(error, SVNLogType.WC);
        }
        conflictSkel.prepend(SVNSkel.createAtom(path));
        conflictSkel.prepend(SVNSkel.createAtom("conflict"));

        return conflictSkel;
    }

    public static String getHumanReadableConflictDescription(SVNTreeConflictDescription treeConflict) {
        SVNNodeKind incomingKind = SVNNodeKind.UNKNOWN;
        if (treeConflict.getConflictAction() == SVNConflictAction.EDIT || treeConflict.getConflictAction() == SVNConflictAction.DELETE) {
            if (treeConflict.getSourceLeftVersion() != null) {
                incomingKind = treeConflict.getSourceLeftVersion().getKind();
            }
        } else if (treeConflict.getConflictAction() == SVNConflictAction.ADD || treeConflict.getConflictAction() == SVNConflictAction.REPLACE) {
            if (treeConflict.getSourceRightVersion() != null) {
                incomingKind = treeConflict.getSourceRightVersion().getKind();
            }
        }
        final String reasonStr = getReasonString(treeConflict);
        final String actionStr = getActionString(incomingKind, treeConflict);
        final String operationStr = treeConflict.getOperation().getName();
        String kindWithSpaceStr = getNodeKindString(treeConflict.getNodeKind());
        if (kindWithSpaceStr.length() > 0) {
            kindWithSpaceStr = kindWithSpaceStr + " ";
        }
        final String description = String.format("local %s%s, incoming %s upon %s", kindWithSpaceStr, reasonStr, actionStr, operationStr);
        return description;
    }

    public static String getHumanReadableConflictVersion(SVNConflictVersion version) {
        if (version == null) {
            return "(none)";
        }
    	String url = version.getRepositoryRoot() != null ? version.getRepositoryRoot().toString() : null;
    	if (url != null && version.getPath() != null) {
    		url = url + "/" + version.getPath();
    	} else if (url != null) {
    		url = url + "/...";
    	} else if (version.getPath() != null) {
    		url = version.getPath();
    	} else {
    		url = "...";
    	}
        return "(" + getNodeKindString(version.getKind()) + ") " + url + "@" + version.getPegRevision();
    }

    private static String getReasonString(SVNTreeConflictDescription treeConflict) {
        final SVNConflictReason reason = treeConflict.getConflictReason();
        if (reason == SVNConflictReason.EDITED) {
            return "edit";
        } else if (reason == SVNConflictReason.OBSTRUCTED) {
            return "obstruction";
        } else if (reason == SVNConflictReason.DELETED) {
            return "delete";
        } else if (reason == SVNConflictReason.MISSING) {
            if (treeConflict.getOperation() == SVNOperation.MERGE) {
                return "missing or deleted or moved away";
            } else {
                return "missing";
            }
        } else if (reason == SVNConflictReason.UNVERSIONED) {
            return "unversioned";
        } else if (reason == SVNConflictReason.ADDED) {
            return "add";
        } else if (reason == SVNConflictReason.REPLACED) {
            return "replace";
        } else if (reason == SVNConflictReason.MOVED_AWAY) {
            return "moved away";
        } else if (reason == SVNConflictReason.MOVED_HERE) {
            return "moved here";
        }
        return null;
    }

    private static String getActionString(SVNNodeKind incomingKind, SVNTreeConflictDescription treeConflict) {
        if (incomingKind == SVNNodeKind.FILE) {
            SVNConflictAction action = treeConflict.getConflictAction();
            if (action == SVNConflictAction.ADD) {
                return "file add";
            } else if (action == SVNConflictAction.EDIT) {
                return "file edit";
            } else if (action == SVNConflictAction.DELETE) {
                return "file delete or move";
            } else if (action == SVNConflictAction.REPLACE) {
                return "replace with file";
            }
        } else if (incomingKind == SVNNodeKind.DIR) {
            SVNConflictAction action = treeConflict.getConflictAction();
            if (action == SVNConflictAction.ADD) {
                return "dir add";
            } else if (action == SVNConflictAction.EDIT) {
                return "dir edit";
            } else if (action == SVNConflictAction.DELETE) {
                return "dir delete or move";
            } else if (action == SVNConflictAction.REPLACE) {
                return "replace with dir";
            }
        } else if (incomingKind == SVNNodeKind.NONE || incomingKind == SVNNodeKind.UNKNOWN) {
            SVNConflictAction action = treeConflict.getConflictAction();
            if (action == SVNConflictAction.ADD) {
                return "add";
            } else if (action == SVNConflictAction.EDIT) {
                return "edit";
            } else if (action == SVNConflictAction.DELETE) {
                return "delete or move";
            } else if (action == SVNConflictAction.REPLACE) {
                return "replace";
            }
        }

        return null;
    }

    private static SVNSkel prependVersionInfo(SVNSkel parent, SVNConflictVersion versionInfo) throws SVNException {
        parent = parent == null ? SVNSkel.createEmptyList() : parent;
        SVNSkel skel = SVNSkel.createEmptyList();
        skel.prepend(SVNSkel.createAtom(getNodeKindString(versionInfo.getKind())));
        String path = versionInfo.getPath() == null ? "" : versionInfo.getPath();
        skel.prepend(SVNSkel.createAtom(path));
        skel.prepend(SVNSkel.createAtom(String.valueOf(versionInfo.getPegRevision())));
        String repoURLString = versionInfo.getRepositoryRoot() == null ? "" : versionInfo.getRepositoryRoot().toString();
        skel.prepend(SVNSkel.createAtom(repoURLString));
        skel.prepend(SVNSkel.createAtom("version"));
        if (!isValidVersionInfo(skel)) {
            SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT, "Failed to create valid conflict version skel: ''{0}''", skel.toString());
            SVNErrorManager.error(error, SVNLogType.WC);
        }
        parent.prepend(skel);
        return parent;
    }

    private static SVNNodeKind getNodeKind(String name) throws SVNException {
        if ("".equals(name)) {
            return SVNNodeKind.UNKNOWN;
        }
        SVNNodeKind kind = SVNNodeKind.parseKind(name);
        if (kind == SVNNodeKind.UNKNOWN) {
            mappingError("node kind");
        }
        return kind;
    }

    private static String getNodeKindString(SVNNodeKind kind) {
        if (kind ==SVNNodeKind.UNKNOWN) {
            return "";
        }
        return kind.toString();
    }

    private static SVNOperation getOperation(String name) throws SVNException {
        SVNOperation operation = SVNOperation.fromString(name);
        if (operation == null) {
            mappingError("operation");
        }
        return operation;
    }

    private static SVNConflictAction getAction(String name) throws SVNException {
        SVNConflictAction action = SVNConflictAction.fromString(name);
        if (action == null) {
            mappingError("conflict action");
        }
        return action;
    }

    private static SVNConflictReason getConflictReason(String name) throws SVNException {
        SVNConflictReason reason;
        /*
        if (SVNConflictReason.UNVERSIONED.getName().equals(name)) {
            reason = null;
        } else {
            reason = SVNConflictReason.fromString(name);
        }
        */
        reason = SVNConflictReason.fromString(name);
        if (reason == null) {
            mappingError("conflict reason");
        }
        return reason;
    }

    private static void mappingError(String type) throws SVNException {
        SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT, "Unknown {0} value in tree conflict description", type);
        SVNErrorManager.error(error, SVNLogType.WC);
    }
}
