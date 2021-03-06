package es.incaser.apps.stockcontrol;


import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static es.incaser.apps.tools.Tools.str2date;


/**
 * Created by sergio on 23/09/14.
 */
public class SyncData {
    private static DbAdapter dbAdapter;
    private Context myContext;
    public SQLConnection conSQL;

    public SyncData(Context ctx) {
        myContext = ctx;
        dbAdapter = new DbAdapter(myContext);
    }

    public int importRecords() {
        ResultSet resultSet;
        for (int i = 0; i < DbAdapter.tablesToImport; i++) {
            resultSet = conSQL.getResultset(String.valueOf(DbAdapter.QUERY_LIST[i][1]));
            copyRecords(resultSet, DbAdapter.QUERY_LIST[i][0]);
        }
        // TODO Devolver numero de registros importados
        return 0;
    }

    public int exportRecords() {
        Cursor cursor;
        ResultSet resultSet = null;
        int numReg = 0;

        for (int i = DbAdapter.tablesToExport; i < DbAdapter.QUERY_LIST.length + 1; i++) {
            cursor = dbAdapter.getTable(DbAdapter.QUERY_LIST[i - 1][0], DbAdapter.QUERY_LIST[i - 1][2]);
            resultSet = conSQL.getResultset("Select * FROM " + DbAdapter.QUERY_LIST[i - 1][0] + " WHERE 1=2", true);
            if (resultSet != null) {
                int x;
                x = copyRecords(cursor, DbAdapter.QUERY_LIST[i - 1][0], resultSet);
                if (x < 0) {
                    return -1;
                }
                dbAdapter.emptyTables(DbAdapter.QUERY_LIST[i - 1][0]);
                numReg += x;
            }
        }
        //Consultas de postprocesado
        conSQL.updateSQL("UPDATE INC_Incidencias SET INC_PendienteSync=0 WHERE INC_PendienteSync<>0");
        return numReg;
    }

    public int copyRecords(Cursor source, String tableSource, ResultSet target) {
        ResultSetMetaData RSmd;
        List<String> columnList = new ArrayList();
        List<String> columnListBynary = new ArrayList();
        List<String> columnListDate = new ArrayList();
        List<String> columnListInt = new ArrayList();
        List<String> columnListDecimal = new ArrayList();
        Integer colInt;

        int numReg = source.getCount();
        int progresscount = 0;
        Log.w(tableSource, "A exportar: " + numReg);
        try {
            RSmd = target.getMetaData();
            String[] localColumns = source.getColumnNames();
            for (int i = 1; i <= RSmd.getColumnCount(); i++) {
                if (Arrays.asList(localColumns).contains(RSmd.getColumnName(i))) {
                    switch (RSmd.getColumnType(i)) {
                        case 3: //decimal
                            columnListDecimal.add(RSmd.getColumnName(i));
                            break;
                        case 5: //smallint
                            columnListInt.add(RSmd.getColumnName(i));
                            break;
                        case 93: //datetime
                            columnListDate.add(RSmd.getColumnName(i));
                            break;
                        case 1: //uniqueidentifier
                            columnListBynary.add(RSmd.getColumnName(i));
                            break;
                        default:
                            columnList.add(RSmd.getColumnName(i));
                    }
                }
            }
            while (source.moveToNext()) {
                progresscount++;
                target.moveToInsertRow();
                for (String col : columnListDecimal) {
                    colInt = source.getColumnIndex(col);
                    target.updateDouble(col, source.getDouble(colInt));
                }
                for (String col : columnListInt) {
                    colInt = source.getColumnIndex(col);
                    target.updateInt(col, source.getInt(colInt));
                }
                for (String col : columnListDate) {
                    colInt = source.getColumnIndex(col);
                    Date dateSql = new Date(str2date(source.getString(colInt), "yyyy-MM-dd HH:mm:ss").getTime());
                    target.updateDate(col, dateSql);
                }
                for (String col : columnListBynary) {
                    colInt = source.getColumnIndex(col);

                    if (source.getBlob(colInt) != null) {
                        target.updateString(col, source.getString(colInt));
                    }
                }
                for (String col : columnList) {
                    colInt = source.getColumnIndex(col);
                    String val = source.getString(colInt);
                    if (val == null) {
                        val = "";
                    }
                    target.updateString(col, val);
                }
                target.insertRow();
            }
            Log.w(tableSource, "Exportados: " + source.getCount());
        } catch (java.sql.SQLException e) {
            e.printStackTrace();
            return -1;
        }
        return numReg;
    }

    public Integer testData() {
        ResultSet source = conSQL.getResultset("SELECT * FROM VIS_INC_RecaudaInstalaActivas");
        String target = "RecaudacionesAnteriores";
        return copyRecords(source, target);
    }

    public int copyRecords(ResultSet source, String target) {
        ResultSetMetaData RSmd;
        ArrayList<String> columnList = new ArrayList();
        int numReg = 0;
        dbAdapter.emptyTables(target);
        Log.w(target, "Inicio importacion");
        try {
            RSmd = source.getMetaData();
            SQLiteDatabase db = dbAdapter.getDb();
            Cursor cursor = dbAdapter.getTable(target, 1);
            String[] localColumns = cursor.getColumnNames();
            String campos = "";
            String valores = "";
            String aux = "";
            for (int i = 1; i <= RSmd.getColumnCount(); i++) {
                if (Arrays.asList(localColumns).contains(RSmd.getColumnName(i))) {
                    columnList.add(RSmd.getColumnName(i));
                    if (campos == "") {
                        campos += RSmd.getColumnName(i);
                        valores += "?";
                    } else {
                        campos += ", " + RSmd.getColumnName(i);
                        valores += ", ?";
                    }
                }
            }
            String sql = "INSERT OR REPLACE INTO " + target + " (" + campos + ") VALUES (" + valores + ")";

            db.beginTransactionNonExclusive();
            SQLiteStatement stmt = db.compileStatement(sql);
            while (source.next()) {
                numReg++;
                int i = 1;
                for (String col : columnList) {
                    aux = source.getString(col);
                    if (aux == null) {
                        aux = "";
                    }
                    stmt.bindString(i, aux);
                    i++;
                }
                stmt.execute();
                stmt.clearBindings();
            }
            db.setTransactionSuccessful();
            db.endTransaction();

            Log.w(target, "Reg Importados: " + numReg);

        } catch (java.sql.SQLException e) {
            e.printStackTrace();
        }
        return numReg;
    }

    private class SynchronizeTest extends AsyncTask<Integer, Void, String> {
        @Override
        protected String doInBackground(Integer... params) {
            conSQL = new SQLConnection();
            if (SQLConnection.connection == null) {
                return "errorSQLconnection";
            }
            return "Test Realizado N:" + testData();
        }

        @Override
        protected void onPostExecute(String result) {
            if (result == "errorSQLconnection") {
                result = myContext.getString(R.string.errorSQLconnection);
            }
            Toast.makeText(myContext, result, Toast.LENGTH_SHORT).show();
        }
    }
}
