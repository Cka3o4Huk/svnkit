/*
 * ====================================================================
 * Copyright (c) 2004-2010 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.db;

import java.io.File;
import java.lang.reflect.Constructor;
import java.util.EnumMap;

import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.sqljet.core.SqlJetTransactionMode;
import org.tmatesoft.sqljet.core.internal.SqlJetPagerJournalMode;
import org.tmatesoft.sqljet.core.internal.SqlJetSafetyLevel;
import org.tmatesoft.sqljet.core.table.ISqlJetBusyHandler;
import org.tmatesoft.sqljet.core.table.SqlJetDb;
import org.tmatesoft.sqljet.core.table.SqlJetTimeoutBusyHandler;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc17.db.SvnNodesPristineTrigger;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbSchema;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbStatements;
import org.tmatesoft.svn.util.SVNLogType;

/**
 * @author TMate Software Ltd.
 */
public class SVNSqlJetDb {

    public static enum Mode {
        /** open the database read-only */
        ReadOnly,
        /** open the database read-write */
        ReadWrite,
        /** open/create the database read-write */
        RWCreate
    };
    
    private static final ISqlJetBusyHandler DEFAULT_BUSY_HANDLER = new SqlJetTimeoutBusyHandler(10000);

    private SqlJetDb db;
    private EnumMap<SVNWCDbStatements, SVNSqlJetStatement> statements;

    private int openCount = 0;
    private SVNSqlJetDb temporaryDb;

    private SVNSqlJetDb(SqlJetDb db) {
        this.db = db;
        statements = new EnumMap<SVNWCDbStatements, SVNSqlJetStatement>(SVNWCDbStatements.class);
    }

    public SqlJetDb getDb() {
        return db;
    }
    
    public int getOpenCount() {
        return openCount;
    }

    public void close() throws SVNException {
        if (temporaryDb != null) {
            try {
                temporaryDb.close();
            } catch (SVNException e) {
                //
            }
            temporaryDb = null;
        }
        if (db != null) {
            try {
                db.close();
                File journalFile = new File(db.getFile().getParentFile(), db.getFile().getName() + "-journal");
                if (journalFile.isFile() && journalFile.length() == 0) {
                    SVNFileUtil.deleteFile(journalFile);
                }
            } catch (SqlJetException e) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.SQLITE_ERROR, e);
                SVNErrorManager.error(err, SVNLogType.WC);
            }
        }
    }

    public static SVNSqlJetDb open(File sdbAbsPath, Mode mode) throws SVNException {
        if (mode != Mode.RWCreate) {
            if (!sdbAbsPath.exists()) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_FOUND, "File not found ''{0}''", sdbAbsPath);
                SVNErrorManager.error(err, SVNLogType.WC);
            }
        }
        try {
            SqlJetDb db = SqlJetDb.open(sdbAbsPath, mode != Mode.ReadOnly);
            db.setBusyHandler(DEFAULT_BUSY_HANDLER);
            db.setSafetyLevel(SqlJetSafetyLevel.OFF);
//            db.setJournalMode(SqlJetPagerJournalMode.TRUNCATE);
            
            SVNSqlJetDb sDb = new SVNSqlJetDb(db);
            return sDb;
        } catch (SqlJetException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.SQLITE_ERROR, e);
            SVNErrorManager.error(err, SVNLogType.WC);
            return null;
        }
    }
    
    public SVNSqlJetDb getTemporaryDb() throws SVNException {
        if (temporaryDb == null) {
            try {
                temporaryDb = new SVNSqlJetDb(getDb().getTemporaryDatabase(false));
            } catch (SqlJetException e) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.SQLITE_ERROR, e);
                SVNErrorManager.error(err, SVNLogType.WC);
                return null;
            }
        }
        return temporaryDb;
    }

    public SVNSqlJetStatement getStatement(SVNWCDbStatements statementIndex) throws SVNException {
        assert (statementIndex != null);
        // SVNDebugLog.getDefaultLog().log(SVNLogType.WC, new StackTraceLog(statementIndex.toString()), Level.INFO);
        SVNSqlJetStatement stmt = statements.get(statementIndex);
        if (stmt == null) {
            stmt = prepareStatement(statementIndex);
            statements.put(statementIndex, stmt);
        }
        if (stmt != null && stmt.isNeedsReset()) {
            stmt.reset();
        }
        
        if (stmt instanceof SVNSqlJetInsertStatement
         || stmt instanceof SVNSqlJetUpdateStatement
         || stmt instanceof SVNSqlJetDeleteStatement) {
            String targetTableName = ((SVNSqlJetTableStatement) stmt).getTableName();
            if (SVNWCDbSchema.NODES.toString().equals(targetTableName)) {
                SvnNodesPristineTrigger trigger = new SvnNodesPristineTrigger();
                ((SVNSqlJetTableStatement) stmt).addTrigger(trigger);
            }
        }        
        return stmt;
    }

    private SVNSqlJetStatement prepareStatement(SVNWCDbStatements statementIndex) throws SVNException {
        final Class<? extends SVNSqlJetStatement> statementClass = statementIndex.getStatementClass();
        SVNErrorManager.assertionFailure(statementClass != null, String.format("Statement '%s' not defined", statementIndex.toString()), SVNLogType.WC);
        if (statementClass == null) {
            return null;
        }
        try {
            final Constructor<? extends SVNSqlJetStatement> constructor = statementClass.getConstructor(SVNSqlJetDb.class);
            final SVNSqlJetStatement stmt = constructor.newInstance(this);
            return stmt;
        } catch (Exception e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, e);
            SVNErrorManager.error(err, SVNLogType.WC);
            return null;
        }
    }

    public void execStatement(SVNWCDbStatements statementIndex) throws SVNException {
        final SVNSqlJetStatement statement = getStatement(statementIndex);
        if (statement != null) {
            statement.exec();
        }
    }

    public static void createSqlJetError(SqlJetException e) throws SVNException {
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.SQLITE_ERROR, e);
        SVNErrorManager.error(err, SVNLogType.WC);
    }

    public void beginTransaction(SqlJetTransactionMode mode) throws SVNException {
        if (mode != null) {
            openCount++;
            if (isNeedStartTransaction(mode)) {
                try {
                    db.beginTransaction(mode);
                } catch (SqlJetException e) {
                    createSqlJetError(e);
                }
            }
        } else {
            SVNErrorManager.assertionFailure(mode != null, "transaction mode is null", SVNLogType.WC);
        }
    }

    private boolean isNeedStartTransaction(SqlJetTransactionMode mode) {
        if (!db.isInTransaction()) {
            return true;
        }
        SqlJetTransactionMode dbMode = db.getTransactionMode();
        return mode != dbMode && (SqlJetTransactionMode.WRITE == mode || SqlJetTransactionMode.EXCLUSIVE == mode) && SqlJetTransactionMode.READ_ONLY == dbMode;
    }

    public void commit() throws SVNException {
        if (openCount > 0) {
            openCount--;
            if (openCount == 0) {
                try {
                    db.commit();
                } catch (SqlJetException e) {
                    createSqlJetError(e);
                }
            }
        } else {
            SVNErrorManager.assertionFailure(openCount > 0, "no opened transactions", SVNLogType.WC);
        }
    }

    public void verifyNoWork() {
    }

    public void runTransaction(final SVNSqlJetTransaction transaction) throws SVNException {
        runTransaction(transaction, SqlJetTransactionMode.WRITE);
    }
    
    public void runTransaction(final SVNSqlJetTransaction transaction, SqlJetTransactionMode mode) throws SVNException {
        try {
            beginTransaction(mode);
            transaction.transaction(SVNSqlJetDb.this);
        } catch (SqlJetException e) {
            try {
                db.rollback();
            } catch (SqlJetException e1) {
                e1.initCause(e);
                SVNErrorMessage err1 = SVNErrorMessage.create(SVNErrorCode.SQLITE_ERROR, e1);
                SVNErrorManager.error(err1, SVNLogType.DEFAULT);
            }
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.SQLITE_ERROR, e);
            SVNErrorManager.error(err, SVNLogType.DEFAULT);
        } finally {
            commit();
        }
    }

    public void rollback() throws SVNException {
        try {
            db.rollback();
        } catch (SqlJetException e1) {
            SVNErrorMessage err1 = SVNErrorMessage.create(SVNErrorCode.SQLITE_ERROR, e1);
            SVNErrorManager.error(err1, SVNLogType.DEFAULT);
        }
    }

    public boolean hasTable(String tableName) throws SVNException {
        try {
            return tableName != null && db.getSchema().getTableNames().contains(tableName);
        } catch (SqlJetException e) {
            SVNErrorMessage err1 = SVNErrorMessage.create(SVNErrorCode.SQLITE_ERROR, e);
            SVNErrorManager.error(err1, SVNLogType.DEFAULT);
        }
        return false;
    }

}
