package org.tmatesoft.svn.core.wc;

import org.tmatesoft.svn.core.io.SVNNodeKind;

import java.io.File;

/**
 * Created by IntelliJ IDEA.
 * User: alex
 * Date: 10.06.2005
 * Time: 21:41:31
 * To change this template use File | Settings | File Templates.
 */
public class SVNCommitItem {

    private SVNRevision myRevision;
    private File myFile;
    private String myURL;
    private String myCopyFromURL;
    private SVNNodeKind myKind;

    private boolean myIsAdded;
    private boolean myIsDeleted;
    private boolean myIsPropertiesModified;
    private boolean myIsContentsModified;
    private boolean myIsCopied;

    public SVNCommitItem(File file, String URL, String copyFromURL, SVNNodeKind kind, SVNRevision revision,
                         boolean isAdded, boolean isDeleted, boolean isPropertiesModified, boolean isContentsModified, boolean isCopied) {
        myRevision = revision;
        myFile = file;
        myURL = URL;
        myCopyFromURL = copyFromURL;
        myKind = kind;
        myIsAdded = isAdded;
        myIsDeleted = isDeleted;
        myIsPropertiesModified = isPropertiesModified;
        myIsContentsModified = isContentsModified;
        myIsCopied = isCopied;
    }

    public SVNRevision getRevision() {
        return myRevision;
    }

    public File getFile() {
        return myFile;
    }

    public String getURL() {
        return myURL;
    }

    public String getCopyFromURL() {
        return myCopyFromURL;
    }

    public SVNNodeKind getKind() {
        return myKind;
    }

    public boolean isIsAdded() {
        return myIsAdded;
    }

    public boolean isIsDeleted() {
        return myIsDeleted;
    }

    public boolean isIsPropertiesModified() {
        return myIsPropertiesModified;
    }

    public boolean isIsContentsModified() {
        return myIsContentsModified;
    }

    public boolean isIsCopied() {
        return myIsCopied;
    }
}
