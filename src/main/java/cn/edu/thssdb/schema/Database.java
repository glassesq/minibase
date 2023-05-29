package cn.edu.thssdb.schema;

import cn.edu.thssdb.communication.IO;
import cn.edu.thssdb.runtime.ServerRuntime;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Database {
  public static class DatabaseMetadata {
    public String name;
    public int databaseId;
    /** spaceId to tableMetadata */
    public HashMap<Integer, Table.TableMetadata> tables = new HashMap<>();

    /** lock for databaseMetadata */
    public static ReentrantReadWriteLock metaDataLatch = new ReentrantReadWriteLock();

    JSONObject object;

    /**
     * create a new database metadata using existing JSONObject. The lock is not required.
     *
     * @param object JSON object
     * @return new databaseMetadata
     * @throws Exception JSON error
     */
    public static DatabaseMetadata createDatabaseMetadata(JSONObject object) throws Exception {
      DatabaseMetadata metadata = new DatabaseMetadata();
      metadata.object = object;
      metadata.name = object.getString("databaseName");
      metadata.databaseId = object.getInt("databaseId");
      JSONArray tableArray = object.getJSONArray("tables");
      for (int i = 0; i < tableArray.length(); i++) {
        Table.TableMetadata tableMetadata = Table.TableMetadata.parse(tableArray.getJSONObject(i));
        metadata.tables.put(tableMetadata.spaceId, tableMetadata);
      }
      return metadata;
    }

    /**
     * This method create table, covering both data and metadata. Proper changes shall be done to
     * WAL buffer as well. The lock is not required. Only the transaction requested this method can
     * access tableMetadata now. It will not affect other tables.
     *
     * @param transactionId transactionId
     * @param tableMetadata tableMetadata
     * @throws Exception WAL error
     */
    public void createTable(long transactionId, Table.TableMetadata tableMetadata)
        throws Exception {
      metaDataLatch.writeLock().lock();
      tables.put(tableMetadata.spaceId, tableMetadata);
      object.getJSONArray("tables").put(tableMetadata.object);
      IO.writeCreateTable(transactionId, this.databaseId, tableMetadata);
      tableMetadata.initTablespaceFile(transactionId);
      ServerRuntime.tableMetadata.put(tableMetadata.spaceId, tableMetadata);
      metaDataLatch.writeLock().unlock();
    }

    /**
     * create database. ServerRuntime{@code (databaseNameLookup, databaseMetadata, metadataArray)}
     * will be automatically updated. Corresponding changes are recorded in WAL log buffer.
     *
     * @param transactionId transactionId
     * @param name name of the database
     * @return A Database Object
     * @throws Exception WAL error
     */
    public static DatabaseMetadata createDatabase(long transactionId, String name)
        throws Exception {
      metaDataLatch.writeLock().lock();
      if (ServerRuntime.databaseNameLookup.containsKey(name)) return null;
      DatabaseMetadata metadata = new DatabaseMetadata();
      metadata.name = name;
      metadata.databaseId = ServerRuntime.newDatabase();
      metadata.tables = new HashMap<>();
      ServerRuntime.databaseMetadata.put(metadata.databaseId, metadata);
      metadata.object = new JSONObject();
      metadata.object.put("databaseName", name);
      metadata.object.put("databaseId", metadata.databaseId);
      metadata.object.put("tables", new JSONArray());
      ServerRuntime.metadataArray.put(metadata.object);
      ServerRuntime.databaseNameLookup.put(name, metadata.databaseId);
      IO.writeCreateDatabase(transactionId, name, metadata.databaseId);
      metaDataLatch.writeLock().unlock();
      return metadata;
    }

    public static boolean dropDatabase(long transactionId, String name) throws Exception {
      Integer databaseId = ServerRuntime.databaseNameLookup.get(name);
      if (databaseId == null) {
        /* database not exists. */
        return false;
      }
      DatabaseMetadata metadata = ServerRuntime.databaseMetadata.get(databaseId);
      int index = -1;
      for (int i = 0; i < ServerRuntime.metadataArray.length(); i++) {
        if (ServerRuntime.metadataArray.getJSONObject(i).getString("databaseName").equals(name)) {
          index = i;
          break;
        }
      }
      if (index == -1) {
        /* This shall never happen. */
        return false;
      }
      metaDataLatch.writeLock().lock();
      ServerRuntime.databaseMetadata.remove(metadata.databaseId);
      ServerRuntime.databaseNameLookup.remove(name);
      ServerRuntime.metadataArray.remove(index);
      IO.writeDropDatabase(transactionId, name, metadata.databaseId);
      metaDataLatch.writeLock().unlock();
      return true;
    }

    public Table.TableMetadata getTableByName(String name) {
      metaDataLatch.readLock().lock();
      // optimization
      for (Integer i : tables.keySet()) {
        if (name.equals(tables.get(i).name)) {
          return tables.get(i);
        }
      }
      metaDataLatch.readLock().unlock();
      return null;
    }

    private void persist() {
      // TODO
    }

    private void deleteDatabase() {
      // TODO
    }

    public void drop() {
      // TODO
    }

    public String select() {
      // TODO
      return null;
    }

    private void recover() {
      // TODO
    }

    public void quit() {
      // TODO
    }
  }
}
