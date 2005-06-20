/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */

package org.tmatesoft.svn.cli;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.StringTokenizer;

import org.tmatesoft.svn.core.ISVNWorkspace;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNStatus;
import org.tmatesoft.svn.core.io.ISVNCredentialsProvider;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.io.SVNRepositoryLocation;
import org.tmatesoft.svn.core.wc.SVNOptions;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.util.DebugLog;
import org.tmatesoft.svn.util.PathUtil;
import org.tmatesoft.svn.util.SVNUtil;

/**
 * @author TMate Software Ltd.
 */
public abstract class SVNCommand {

    private SVNCommandLine myCommandLine;
    private String myUserName;
    private String myPassword;

    private static Map ourCommands;
    private boolean myIsStoreCreds;

    protected SVNCommandLine getCommandLine() {
        return myCommandLine;
    }

    public void setCommandLine(SVNCommandLine commandLine) {
        myCommandLine = commandLine;
        myUserName = (String) commandLine.getArgumentValue(SVNArgument.USERNAME);
        myPassword = (String) commandLine.getArgumentValue(SVNArgument.PASSWORD);
        myIsStoreCreds = !commandLine.hasArgument(SVNArgument.NO_AUTH_CACHE);
    }

    public abstract void run(PrintStream out, PrintStream err) throws SVNException;

    protected ISVNWorkspace createWorkspace(String absolutePath) throws SVNException {
        return createWorkspace(absolutePath, true);
    }
    
    protected SVNOptions getOptions() {
        String dir = (String) getCommandLine().getArgumentValue(SVNArgument.CONFIG_DIR);
        if (dir == null) {
            return new SVNOptions();
        }
        return new SVNOptions(new File(dir));
    }

    protected ISVNWorkspace createWorkspace(String absolutePath, boolean root) throws SVNException {
        ISVNWorkspace ws = SVNUtil.createWorkspace(absolutePath, root);
        //ws.setCredentials(myUserName, myPassword);
        ws.setCredentials(new SVNCommandLineCredentialsProvider(myUserName, myPassword, myIsStoreCreds));
        return ws;
    }

    protected final SVNRepository createRepository(String url) throws SVNException {
        SVNRepository repository = SVNRepositoryFactory.create(SVNRepositoryLocation.parseURL(url));
        repository.setCredentialsProvider(new SVNCommandLineCredentialsProvider(myUserName, myPassword, myIsStoreCreds));
        //repository.setCredentialsProvider(new SVNSimpleCredentialsProvider(myUserName, myPassword));
        return repository;
    }
    
    protected ISVNCredentialsProvider getCredentialsProvider() {
        return new SVNCommandLineCredentialsProvider(myUserName, myPassword, myIsStoreCreds);
    }

    public static SVNCommand getCommand(String name) {
        if (name == null) {
            return null;
        }
        String className = null;
        for (Iterator keys = ourCommands.keySet().iterator(); keys.hasNext();) {
            String[] names = (String[]) keys.next();
            for (int i = 0; i < names.length; i++) {
                if (name.equals(names[i])) {
                    className = (String) ourCommands.get(names);
                    break;
                }
            }
            if (className != null) {
                break;
            }
        }
        if (className == null) {
            return null;
        }
        try {
            Class clazz = Class.forName(className);
            if (clazz != null) {
                return (SVNCommand) clazz.newInstance();
            }
        } catch (Throwable th) {}
        return null;
    }

    protected String convertPath(String homePath, ISVNWorkspace ws, String path) throws IOException {
        if ("".equals(homePath)) {
            homePath = ".";
        }
        String absolutePath = SVNUtil.getAbsolutePath(ws, path);
        String absoluteHomePath = new File(homePath).getCanonicalPath();
        if (".".equals(homePath) || new File(homePath).isAbsolute()) {
            homePath = "";
        }
        String relativePath = absolutePath.substring(absoluteHomePath.length());
        String result = PathUtil.append(homePath, relativePath);
        result = result.replace(File.separatorChar, '/');
        result = PathUtil.removeLeadingSlash(result);
        result = PathUtil.removeTrailingSlash(result);
        result = result.replace('/', File.separatorChar);

        if ("".equals(result)) {
            return ".";
        }
        return result;
    }

    protected final static long parseRevision(SVNCommandLine commandLine, ISVNWorkspace workspace, String path) throws SVNException {
        if (commandLine.hasArgument(SVNArgument.REVISION)) {
            final String revStr = (String) commandLine.getArgumentValue(SVNArgument.REVISION);
            if (revStr.equalsIgnoreCase("HEAD")) {
                return ISVNWorkspace.HEAD;
            }
	          else if (revStr.equalsIgnoreCase("BASE")) {
		          if (workspace == null ||path == null) {
			          throw new SVNException("Revision BASE not allowed in this context!");
		          }

		          final SVNStatus status = workspace.status(path, false);
		          return status.getWorkingCopyRevision();
	          }
            return Long.parseLong(revStr);
        }
        return -1;
    }
    
    protected final static SVNRevision parseRevision(SVNCommandLine commandLine) throws SVNException {
        if (commandLine.hasArgument(SVNArgument.REVISION)) {
            final String revStr = (String) commandLine.getArgumentValue(SVNArgument.REVISION);
            DebugLog.log("parsing revision: " + revStr);
            return SVNRevision.parse(revStr);
        }
        return SVNRevision.UNDEFINED;        
    }
    
    protected final static long getRevisionNumber(String rev, String path, ISVNWorkspace ws, SVNRepository repository) throws SVNException {
        if (rev == null) {
            return -2;
        }
        rev = rev.trim();
        long result = -1;
        try {
            return Long.parseLong(rev);
        } catch (NumberFormatException nfe) {
        }
        if ("HEAD".equals(rev) && repository != null) {
            return repository.getLatestRevision();
        } else if (("COMMITTED".equals(rev) || 
                   "WORKING".equals(rev) ||
                   "BASE".equals(rev) ||
                   "PREV".equals(rev)) && ws != null && path != null) {
            if ("BASE".equals(rev) || "WORKING".equals(rev)) {
                String revisionStr = ws.getPropertyValue(path, SVNProperty.REVISION);
                if (revisionStr != null) {
                    return SVNProperty.longValue(revisionStr);
                }
            } else {
                String revisionStr = ws.getPropertyValue(path, SVNProperty.COMMITTED_REVISION);
                if (revisionStr != null) {
                    long value = SVNProperty.longValue(revisionStr);
                    if ("PREV".equals(rev)) {                        
                        value--;
                    }
                    return value;
                }
            }
        } else if (rev.startsWith("{") && rev.endsWith("}") &&
                 repository != null) {
            rev = rev.substring(1);
            rev = rev.substring(0, rev.length() - 1);
            Date date = null;
            try {
                date = SimpleDateFormat.getDateTimeInstance().parse(rev);
            } catch (ParseException e) {
                DebugLog.error(e);
            }
            if (date != null) {
                return repository.getDatedRevision(date);
            }
        } 
        return -2;
        
    }

    public final static void println(PrintStream out, String line) {
        out.println(line);
        DebugLog.log(line);
    }

    public final static void print(PrintStream out, String line) {
        out.print(line);
        DebugLog.log(line);
    }

    public final static void println(PrintStream out) {
        out.println();
        DebugLog.log("");
    }
    
    protected static boolean matchTabsInPath(String path, PrintStream out) {
        if (path != null && path.indexOf('\t') >= 0) {
            out.println("svn: Invalid control character '0x09' in path '" + path + "'");
            return true;
        }
        return false;
    }

    public static String getPath(File file) {
        String path = "";
        String rootPath = "";
        path = file.getAbsolutePath();
        rootPath = new File("").getAbsolutePath();
        path = path.replace(File.separatorChar, '/');
        rootPath = rootPath.replace(File.separatorChar, '/');
        if (path.startsWith(rootPath)) {
            path = path.substring(rootPath.length());
        }
        // remove all "./"
        path = condensePath(path);
        path = PathUtil.removeLeadingSlash(path);
        path = PathUtil.removeTrailingSlash(path);
        path = path.replace('/', File.separatorChar);
        if (path.trim().length() == 0) {
        	path = ".";
        }
        return path;
    }

    private static String condensePath(String path) {
        StringBuffer result = new StringBuffer();
        for(StringTokenizer tokens = new StringTokenizer(path, "/", true); tokens.hasMoreTokens();) {
            String token = tokens.nextToken();
            if (".".equals(token)) {
                if (tokens.hasMoreTokens()) {
                    String nextToken = tokens.nextToken();
                    if (!nextToken.equals("/")) {
                        result.append(nextToken);
                    }
                }
                continue;
            }
            result.append(token);
        }
        return result.toString();
    }

    static {
        Locale.setDefault(Locale.ENGLISH);

        ourCommands = new HashMap();
        ourCommands.put(new String[] { "status", "st", "stat" }, "org.tmatesoft.svn.cli.command.StatusCommand");
        ourCommands.put(new String[] { "import" }, "org.tmatesoft.svn.cli.command.ImportCommand");
        ourCommands.put(new String[] { "checkout", "co" }, "org.tmatesoft.svn.cli.command.CheckoutCommand");
        ourCommands.put(new String[] { "add" }, "org.tmatesoft.svn.cli.command.AddCommand");
        ourCommands.put(new String[] { "commit", "ci" }, "org.tmatesoft.svn.cli.command.CommitCommand");
        ourCommands.put(new String[] { "update", "up" }, "org.tmatesoft.svn.cli.command.UpdateCommand");
        ourCommands.put(new String[] { "delete", "rm", "remove", "del" }, "org.tmatesoft.svn.cli.command.DeleteCommand");
        ourCommands.put(new String[] { "move", "mv", "rename", "ren" }, "org.tmatesoft.svn.cli.command.MoveCommand");
        ourCommands.put(new String[] { "copy", "cp" }, "org.tmatesoft.svn.cli.command.CopyCommand");
        ourCommands.put(new String[] { "revert" }, "org.tmatesoft.svn.cli.command.RevertCommand");
        ourCommands.put(new String[] { "mkdir" }, "org.tmatesoft.svn.cli.command.MkDirCommand");
        ourCommands.put(new String[] { "propset", "pset", "ps" }, "org.tmatesoft.svn.cli.command.PropsetCommand");
        ourCommands.put(new String[] { "propdel", "pdel", "pd" }, "org.tmatesoft.svn.cli.command.PropdelCommand");
        ourCommands.put(new String[] { "propget", "pget", "pg" }, "org.tmatesoft.svn.cli.command.PropgetCommand");
        ourCommands.put(new String[] { "proplist", "plist", "pl" }, "org.tmatesoft.svn.cli.command.ProplistCommand");
        ourCommands.put(new String[] { "info" }, "org.tmatesoft.svn.cli.command.InfoCommand");
        ourCommands.put(new String[] { "resolved" }, "org.tmatesoft.svn.cli.command.ResolvedCommand");
        ourCommands.put(new String[] { "cat" }, "org.tmatesoft.svn.cli.command.CatCommand");
        ourCommands.put(new String[] { "ls" }, "org.tmatesoft.svn.cli.command.LsCommand");
        ourCommands.put(new String[] { "log" }, "org.tmatesoft.svn.cli.command.LogCommand");
        ourCommands.put(new String[] { "switch", "sw" }, "org.tmatesoft.svn.cli.command.SwitchCommand");
        ourCommands.put(new String[] { "diff", "di" }, "org.tmatesoft.svn.cli.command.DiffCommand");
        ourCommands.put(new String[] { "merge" }, "org.tmatesoft.svn.cli.command.MergeCommand");
        ourCommands.put(new String[] { "export" }, "org.tmatesoft.svn.cli.command.ExportCommand");
        ourCommands.put(new String[] { "cleanup" }, "org.tmatesoft.svn.cli.command.CleanupCommand");

        ourCommands.put(new String[] { "lock" }, "org.tmatesoft.svn.cli.command.LockCommand");
        ourCommands.put(new String[] { "unlock" }, "org.tmatesoft.svn.cli.command.UnlockCommand");

        ourCommands.put(new String[] { "annotate", "blame", "praise", "ann" }, "org.tmatesoft.svn.cli.command.AnnotateCommand");
    }

    public static String formatString(String str, int chars, boolean left) {
        if (str.length() > chars) {
            return str.substring(0, chars);
        }
        StringBuffer formatted = new StringBuffer();
        if (left) {
            formatted.append(str);
        }
        for(int i = 0; i < chars - str.length(); i++) {
            formatted.append(' ');
        }
        if (!left) {
            formatted.append(str);
        }
        return formatted.toString();
    }
}
